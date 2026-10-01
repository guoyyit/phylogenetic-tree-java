package com.github.guoyyit.phylogenetictreejava.service;

import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapRunner;
import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapSupport;
import com.github.guoyyit.phylogenetictreejava.bootstrap.CancellationToken;
import com.github.guoyyit.phylogenetictreejava.bootstrap.FileCheckpointStore;
import com.github.guoyyit.phylogenetictreejava.io.NewickCodec;
import com.github.guoyyit.phylogenetictreejava.model.Alignment;
import com.github.guoyyit.phylogenetictreejava.model.AlignmentInput;
import com.github.guoyyit.phylogenetictreejava.tree.PhyloTree;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Manages concurrent, isolated analysis tasks: submit, observe, cancel and
 * resume. Each task has its own work directory; cancel/timeout of one task
 * never affects another and never leaves partially written final output.
 *
 * <p>A task id is a deterministic digest of the input sequences and config,
 * so re-submitting identical work after a restart resumes from checkpoints
 * instead of recomputing finished replicates.</p>
 */
public final class TaskManager {

    private final Path workRoot;
    private final int defaultConcurrency;
    private final AnalysisService analysisService = new AnalysisService();
    private final ExecutorService taskExecutor;
    private final ScheduledExecutorService timeoutExecutor;
    private final Map<String, TaskHandle> handles = new ConcurrentHashMap<>();

    public TaskManager(Path workRoot, int defaultConcurrency) {
        try {
            this.workRoot = Files.createDirectories(workRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create work root", e);
        }
        this.defaultConcurrency = Math.max(1, defaultConcurrency);
        this.taskExecutor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "analysis-task");
            thread.setDaemon(true);
            return thread;
        });
        this.timeoutExecutor = Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "analysis-timeout");
            thread.setDaemon(true);
            return thread;
        });
    }

    public String submit(List<AlignmentInput> inputs, AnalysisConfig config) {
        if (config.replicates() < 0) {
            throw new IllegalArgumentException(
                    "replicates must be >= 0");
        }
        if (config.concurrency() < 1) {
            throw new IllegalArgumentException(
                    "concurrency must be >= 1");
        }
        List<AlignmentInput> safeInputs = List.copyOf(inputs);
        analysisService.validate(safeInputs);
        String taskId = taskId(safeInputs, config);
        handles.compute(taskId, (id, existing) -> {
            if (existing == null
                    || existing.phase == TaskInfo.State.FAILED
                    || existing.phase == TaskInfo.State.CANCELLED
                    || existing.phase == TaskInfo.State.TIMEOUT) {
                TaskHandle fresh = new TaskHandle(id, config.replicates());
                launch(fresh, safeInputs, config);
                return fresh;
            }
            return existing;
        });
        return taskId;
    }

    public TaskInfo getStatus(String taskId) {
        TaskHandle handle = handles.get(taskId);
        if (handle == null) {
            return restoredStatus(taskId);
        }
        return handle.snapshot();
    }

    public Optional<AnalysisResult> getResult(String taskId) {
        TaskHandle handle = handles.get(taskId);
        if (handle != null && handle.phase == TaskInfo.State.COMPLETED
                && handle.result != null) {
            return Optional.of(handle.result);
        }
        return readPersistedResult(taskId);
    }

    public boolean cancel(String taskId) {
        TaskHandle handle = handles.get(taskId);
        if (handle == null) {
            return false;
        }
        synchronized (handle) {
            if (handle.phase == TaskInfo.State.RUNNING) {
                handle.cancellation.cancel();
                return true;
            }
        }
        return false;
    }

    private void launch(TaskHandle handle, List<AlignmentInput> inputs,
                        AnalysisConfig config) {
        Future<?> future = taskExecutor.submit(
                () -> execute(handle, inputs, config));
        handle.future = future;
        if (config.timeoutMillis() > 0) {
            handle.timeoutFuture = timeoutExecutor.schedule(() -> {
                handle.cancellation.cancel();
                handle.timedOut = true;
            }, config.timeoutMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void execute(TaskHandle handle, List<AlignmentInput> inputs,
                         AnalysisConfig config) {
        Path taskDirectory = workRoot.resolve(handle.taskId);
        FileCheckpointStore checkpointStore =
                new FileCheckpointStore(taskDirectory);
        try {
            Alignment alignment = analysisService.validate(inputs);
            int startingReplicates = checkpointStore.loadCompleted().size();
            handle.completedReplicates = startingReplicates;

            BootstrapSupport support = analysisService.bootstrap(
                    alignment, config.replicates(), config.seed(),
                    config.concurrency(), checkpointStore, handle.cancellation,
                    delta -> handle.completedReplicates =
                            handle.completedReplicates + delta);
            handle.completedReplicates = support.replicateTrees().size();

            String consensus = analysisService.toNewick(support.consensusTree());
            String bootstrap = analysisService.toNewick(support.replicateTrees());
            AnalysisResult result =
                    new AnalysisResult(handle.taskId, consensus, bootstrap);
            persistResult(taskDirectory, result);
            handle.result = result;
            handle.phase = TaskInfo.State.COMPLETED;
        } catch (BootstrapRunner.BootstrapCancelledException
                 | BootstrapRunner.BootstrapInterruptedException e) {
            handle.phase = handle.timedOut
                    ? TaskInfo.State.TIMEOUT : TaskInfo.State.CANCELLED;
            handle.errorMessage = handle.phase == TaskInfo.State.TIMEOUT
                    ? "analysis exceeded its timeout"
                    : "analysis was cancelled";
        } catch (RuntimeException e) {
            handle.phase = TaskInfo.State.FAILED;
            handle.errorMessage = e.getMessage();
        } finally {
            if (handle.timeoutFuture != null) {
                handle.timeoutFuture.cancel(false);
            }
        }
    }

    private void persistResult(Path taskDirectory, AnalysisResult result) {
        try {
            writeAtomically(taskDirectory.resolve("consensus.nwk"),
                    result.consensusNewick());
            writeAtomically(taskDirectory.resolve("bootstrap-trees.nwk"),
                    result.bootstrapNewick());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot persist analysis result", e);
        }
    }

    private static void writeAtomically(Path target, String content)
            throws IOException {
        Path temporary = target.resolveSibling(
                target.getFileName() + ".tmp-" + Thread.currentThread().getId());
        Files.writeString(temporary, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private Optional<AnalysisResult> readPersistedResult(String taskId) {
        Path consensus = workRoot.resolve(taskId).resolve("consensus.nwk");
        Path bootstrap = workRoot.resolve(taskId).resolve("bootstrap-trees.nwk");
        if (!Files.isRegularFile(consensus) || !Files.isRegularFile(bootstrap)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new AnalysisResult(taskId,
                    Files.readString(consensus, StandardCharsets.UTF_8),
                    Files.readString(bootstrap, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read persisted result", e);
        }
    }

    private TaskInfo restoredStatus(String taskId) {
        if (readPersistedResult(taskId).isPresent()) {
            return new TaskInfo(taskId, TaskInfo.State.COMPLETED, 0, 0, null);
        }
        Path replicateDirectory =
                workRoot.resolve(taskId).resolve("replicates");
        if (Files.isDirectory(replicateDirectory)) {
            int count;
            try (Stream<Path> files = Files.list(replicateDirectory)) {
                count = (int) files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString()
                                .matches("replicate-\\d+\\.nwk"))
                        .count();
            } catch (IOException e) {
                throw new UncheckedIOException("cannot inspect checkpoints", e);
            }
            return new TaskInfo(taskId, TaskInfo.State.CANCELLED, count, 0,
                    "task is not active in this process");
        }
        return null;
    }

    private String taskId(List<AlignmentInput> inputs, AnalysisConfig config) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        for (AlignmentInput input : inputs) {
            digest.update((input.name() + "\n").getBytes(StandardCharsets.UTF_8));
            digest.update((input.sequence() + "\n").getBytes(StandardCharsets.UTF_8));
        }
        digest.update(("replicates=" + config.replicates() + "\n")
                .getBytes(StandardCharsets.UTF_8));
        digest.update(("seed=" + config.seed() + "\n")
                .getBytes(StandardCharsets.UTF_8));
        digest.update(("concurrency=" + config.concurrency() + "\n")
                .getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest()).substring(0, 16);
    }

    private static final class TaskHandle {
        private final String taskId;
        private final int totalReplicates;
        private final CancellationToken cancellation = new CancellationToken();
        private volatile TaskInfo.State phase = TaskInfo.State.RUNNING;
        private volatile int completedReplicates;
        private volatile String errorMessage;
        private volatile AnalysisResult result;
        private volatile Future<?> future;
        private volatile Future<?> timeoutFuture;
        private volatile boolean timedOut;

        private TaskHandle(String taskId, int totalReplicates) {
            this.taskId = taskId;
            this.totalReplicates = totalReplicates;
        }

        private TaskInfo snapshot() {
            return new TaskInfo(taskId, phase, completedReplicates,
                    totalReplicates, errorMessage);
        }
    }
}
