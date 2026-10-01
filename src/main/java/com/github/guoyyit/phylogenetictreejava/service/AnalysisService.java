package com.github.guoyyit.phylogenetictreejava.service;

import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapConfig;
import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapEngine;
import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapResult;
import com.github.guoyyit.phylogenetictreejava.exception.AlignmentException;
import com.github.guoyyit.phylogenetictreejava.io.FastaReader;
import com.github.guoyyit.phylogenetictreejava.model.Alignment;
import com.github.guoyyit.phylogenetictreejava.model.TreeNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import jakarta.annotation.PreDestroy;

/**
 * Local phylogenetic analysis service: task submission, concurrency control,
 * cancellation, timeout and crash recovery.
 */
public final class AnalysisService {

    private final TaskStore store;
    private final int defaultConcurrency;
    private final Map<String, RunningTask> running = new ConcurrentHashMap<>();
    private final Set<ExecutorService> trackedPools =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final class RunningTask {
        private final AnalysisTask snapshot;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private Future<?> future;

        private RunningTask(AnalysisTask snapshot) {
            this.snapshot = snapshot;
        }
    }

    public AnalysisService(Path workRoot, int defaultConcurrency) {
        if (defaultConcurrency < 1) {
            throw new IllegalArgumentException("defaultConcurrency must be >= 1");
        }
        this.defaultConcurrency = defaultConcurrency;
        this.store = new TaskStore(workRoot);
    }

    /**
     * Validates and persists the request, then starts execution asynchronously.
     * Throws {@link AlignmentException} for invalid input; nothing is persisted.
     */
    public synchronized String submit(String fasta, BootstrapConfig config) {
        Alignment alignment = FastaReader.parse(fasta);
        BootstrapConfig effective = normalize(config);
        String taskId = newTaskId();
        while (store.exists(taskId)) {
            taskId = newTaskId();
        }
        try {
            store.createTask(taskId, fasta, effective.replicates(), effective.seed(),
                    effective.concurrency(), effective.timeoutSeconds());
        } catch (IOException e) {
            throw new IllegalStateException("failed to persist task " + taskId, e);
        }
        AnalysisTask task = new AnalysisTask(taskId);
        task.setConfig(effective);
        persistStatus(task);
        launch(task, fasta, effective);
        return taskId;
    }

    public Optional<AnalysisTask> status(String taskId) {
        RunningTask active = running.get(taskId);
        if (active != null) {
            return Optional.of(active.snapshot);
        }
        if (!store.exists(taskId)) {
            return Optional.empty();
        }
        return Optional.of(loadSnapshot(taskId));
    }

    public synchronized boolean cancel(String taskId) {
        RunningTask active = running.get(taskId);
        if (active == null) {
            return false;
        }
        active.cancelled.set(true);
        if (active.future != null) {
            active.future.cancel(true);
        }
        return true;
    }

    /**
     * Resumes every unfinished task found on disk. Already completed replicate
     * files are skipped; the final result is identical to an uninterrupted run.
     */
    public synchronized void recoverInterruptedTasks() {
        List<String> taskIds;
        try {
            taskIds = store.listTasks();
        } catch (IOException e) {
            throw new IllegalStateException("failed to list task directory", e);
        }
        for (String taskId : taskIds) {
            if (running.containsKey(taskId) || store.hasOutputs(taskId)) {
                continue;
            }
            cleanTempFiles(taskId);
            try {
                AnalysisTask task = loadSnapshot(taskId);
                boolean resumable = store.hasAnyReplicate(taskId);
                if (!resumable && (task.getStatus() == AnalysisStatus.FAILED
                        || task.getStatus() == AnalysisStatus.CANCELLED)) {
                    continue;
                }
                String fasta;
                try {
                    fasta = java.nio.file.Files.readString(
                            store.taskDirectory(taskId).resolve("input.fasta"));
                } catch (IOException missing) {
                    continue;
                }
                task.setStatus(AnalysisStatus.RUNNING);
                task.setMessage("recovered after restart (previous state: "
                        + task.getStatus() + ")");
                persistStatus(task);
                launch(task, fasta, task.getConfig());
            } catch (RuntimeException skipped) {
                // A corrupted task must not block recovery of the others.
            } catch (IOException skipped) {
                // A corrupted task must not block recovery of the others.
            }
        }
    }

    public TaskStore getStore() {
        return store;
    }

    private void launch(AnalysisTask task, String fasta, BootstrapConfig config) {
        RunningTask handle = new RunningTask(task);
        running.put(task.getId(), handle);
        handle.future = startWorker(() -> runTask(task, fasta, config, handle));
    }

    private void runTask(AnalysisTask task, String fasta, BootstrapConfig config,
                         RunningTask handle) {
        long deadlineNanos = config.timeoutSeconds() > 0
                ? System.nanoTime() + TimeUnit.SECONDS.toNanos(config.timeoutSeconds())
                : Long.MAX_VALUE;
        ExecutorService replicatePool = null;
        try {
            task.setStatus(AnalysisStatus.RUNNING);
            task.setMessage("");
            persistStatus(task);

            Alignment alignment = FastaReader.parse(fasta);
            int total = config.replicates();
            Set<Integer> done = new HashSet<>(store.completedReplicates(task.getId(), total));
            task.setCompletedReplicates(done.size());

            List<Integer> pending = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                if (!done.contains(i)) {
                    pending.add(i);
                }
            }

            int workers = Math.max(1, config.concurrency());
            replicatePool = Executors.newFixedThreadPool(workers);
            trackedPools.add(replicatePool);
            boolean stoppedByDeadline;
            try {
                stoppedByDeadline = runPending(task, alignment, config, pending,
                        replicatePool, handle, deadlineNanos);
            } catch (InterruptedException interrupted) {
                stoppedByDeadline = !handle.cancelled.get();
                if (handle.cancelled.get()) {
                    finish(task, AnalysisStatus.CANCELLED, "cancelled by user");
                    return;
                }
            }

            if (handle.cancelled.get()) {
                finish(task, AnalysisStatus.CANCELLED, "cancelled by user");
                return;
            }
            if (stoppedByDeadline || System.nanoTime() > deadlineNanos) {
                finish(task, AnalysisStatus.TIMEOUT,
                        "timed out after " + config.timeoutSeconds() + "s");
                return;
            }

            List<TreeNode> trees = new ArrayList<>(total);
            for (int i = 0; i < total; i++) {
                trees.add(store.readReplicate(task.getId(), i));
            }
            BootstrapResult result = BootstrapEngine.assembleResult(alignment, trees);
            synchronized (store) {
                store.writeOutputs(task.getId(), result.consensusTree(),
                        result.bootstrapTrees());
            }
            task.setCompletedReplicates(total);
            finish(task, AnalysisStatus.COMPLETED, "");
        } catch (AlignmentException e) {
            finish(task, AnalysisStatus.FAILED, "input rejected: " + e.getReason()
                    + ": " + e.getMessage());
        } catch (Exception e) {
            finish(task, AnalysisStatus.FAILED, e.toString());
        } finally {
            if (replicatePool != null) {
                replicatePool.shutdownNow();
                trackedPools.remove(replicatePool);
            }
            running.remove(task.getId());
        }
    }

    private boolean runPending(AnalysisTask task, Alignment alignment, BootstrapConfig config,
                            List<Integer> pending, ExecutorService replicatePool,
                            RunningTask handle, long deadlineNanos)
            throws InterruptedException, java.io.IOException {
        int maxInFlight = Math.max(1, config.concurrency()) * 2;
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>(pending);
        java.util.ArrayDeque<Future<Integer>> inFlight = new java.util.ArrayDeque<>();
        boolean deadlineHit = false;
        try {
            while (true) {
                if (handle.cancelled.get() || System.nanoTime() > deadlineNanos) {
                    queue.clear();
                    for (Future<Integer> future : inFlight) {
                        future.cancel(true);
                    }
                    inFlight.clear();
                    deadlineHit = System.nanoTime() > deadlineNanos;
                    break;
                }
                if (queue.isEmpty() && inFlight.isEmpty()) {
                    break;
                }
                while (!queue.isEmpty() && inFlight.size() < maxInFlight) {
                    if (handle.cancelled.get() || System.nanoTime() > deadlineNanos) {
                        for (Future<Integer> future : inFlight) {
                            future.cancel(true);
                        }
                        deadlineHit = System.nanoTime() > deadlineNanos;
                        queue.clear();
                        inFlight.clear();
                        break;
                    }
                    int replicate = queue.poll();
                    inFlight.add(replicatePool.submit(() -> {
                        TreeNode tree = BootstrapEngine.buildSingleReplicate(
                                alignment, config.seed(), replicate);
                        synchronized (store) {
                            store.writeReplicate(task.getId(), replicate, tree);
                        }
                        return replicate;
                    }));
                }
                Future<Integer> future = inFlight.poll();
                if (future == null) {
                    continue;
                }
                try {
                    future.get(200, TimeUnit.MILLISECONDS);
                    task.setCompletedReplicates(task.getCompletedReplicates() + 1);
                } catch (java.util.concurrent.TimeoutException ignored) {
                    inFlight.addFirst(future);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new IllegalStateException(cause);
                }
            }
        } finally {
            replicatePool.shutdownNow();
        }
        return deadlineHit;
    }

    private void finish(AnalysisTask task, AnalysisStatus status, String message) {
        task.setStatus(status);
        task.setMessage(message == null ? "" : message);
        persistStatus(task);
    }

    private void persistStatus(AnalysisTask task) {
        BootstrapConfig config = task.getConfig();
        String json = "{\"id\":\"" + escape(task.getId()) + "\","
                + "\"status\":\"" + task.getStatus() + "\","
                + "\"message\":\"" + escape(task.getMessage()) + "\","
                + "\"completedReplicates\":" + task.getCompletedReplicates() + ","
                + "\"totalReplicates\":" + (config == null ? 0 : config.replicates())
                + "}";
        try {
            store.writeStatus(task.getId(), json);
        } catch (IOException e) {
            throw new IllegalStateException("failed to persist status", e);
        }
    }

    private AnalysisTask loadSnapshot(String taskId) {
        try {
            AnalysisTask task = new AnalysisTask(taskId);
            Properties properties = store.readConfig(taskId);
            task.setConfig(new BootstrapConfig(
                    Integer.parseInt(properties.getProperty("replicates")),
                    Long.parseLong(properties.getProperty("seed")),
                    Integer.parseInt(properties.getProperty("concurrency")),
                    Long.parseLong(properties.getProperty("timeoutSeconds"))));
            String json = store.readStatus(taskId);
            applyStatusJson(task, json);
            task.setCompletedReplicates(
                    store.completedReplicates(taskId, task.getConfig().replicates()).size());
            return task;
        } catch (Exception e) {
            throw new IllegalStateException("unreadable task state: " + taskId, e);
        }
    }

    private void applyStatusJson(AnalysisTask task, String json) {
        String status = extract(json, "status");
        String message = extract(json, "message");
        if (status != null) {
            task.setStatus(AnalysisStatus.valueOf(status));
        }
        if (message != null) {
            task.setMessage(message);
        }
    }

    private static String extract(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            return null;
        }
        start += marker.length();
        StringBuilder value = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && json.charAt(i - 1) != '\\') {
                break;
            }
            value.append(c);
        }
        return value.toString().replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void cleanTempFiles(String taskId) {
        Path dir = store.taskDirectory(taskId);
        cleanTempIn(dir);
        cleanTempIn(dir.resolve("replicates"));
    }

    private static void cleanTempIn(Path dir) {
        if (!java.nio.file.Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> entries = java.nio.file.Files.list(dir)) {
            entries.filter(path -> path.getFileName().toString().startsWith(".tmp-"))
                    .forEach(path -> {
                        try {
                            java.nio.file.Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // best-effort cleanup
                        }
                    });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private BootstrapConfig normalize(BootstrapConfig config) {
        int replicates = config == null || config.replicates() <= 0 ? 100 : config.replicates();
        long seed = config == null ? 0L : config.seed();
        int concurrency = config == null || config.concurrency() <= 0
                ? defaultConcurrency : config.concurrency();
        long timeout = config == null || config.timeoutSeconds() < 0
                ? 0L : config.timeoutSeconds();
        return new BootstrapConfig(replicates, seed, concurrency, timeout);
    }

    private static String newTaskId() {
        return UUID.randomUUID().toString();
    }

    private final ExecutorService taskExecutor = Executors.newCachedThreadPool();

    private Future<?> startWorker(Runnable runnable) {
        return taskExecutor.submit(runnable);
    }

    @PreDestroy
    public void shutdown() {
        taskExecutor.shutdownNow();
    }

    /**
     * Simulates an abrupt process kill: every task and replicate thread is
     * interrupted immediately; no finalization or status rewrite occurs.
     * Already atomically persisted replicate files are the recovery source.
     */
    public void shutdownAbruptly() {
        for (RunningTask handle : running.values()) {
            handle.cancelled.set(true);
        }
        taskExecutor.shutdownNow();
        trackedPools.forEach(ExecutorService::shutdownNow);
    }
}
