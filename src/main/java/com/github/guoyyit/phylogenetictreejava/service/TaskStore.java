package com.github.guoyyit.phylogenetictreejava.service;

import java.nio.file.Path;

import com.github.guoyyit.phylogenetictreejava.io.NewickParser;
import com.github.guoyyit.phylogenetictreejava.io.NewickWriter;
import com.github.guoyyit.phylogenetictreejava.model.Alignment;
import com.github.guoyyit.phylogenetictreejava.io.FastaReader;
import com.github.guoyyit.phylogenetictreejava.model.TreeNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * On-disk, crash-safe storage of task inputs, replicate results and final outputs.
 */
public final class TaskStore {

    private final Path workRoot;

    public TaskStore(Path workRoot) {
        this.workRoot = workRoot;
        try {
            Files.createDirectories(workRoot);
        } catch (IOException e) {
            throw new IllegalStateException("cannot create work directory " + workRoot, e);
        }
    }

    public Path taskDirectory(String taskId) {
        return workRoot.resolve(taskId);
    }

    public void createTask(String taskId, String fasta,
                           int replicates, long seed, int concurrency, long timeoutSeconds)
            throws IOException {
        Path dir = taskDirectory(taskId);
        Files.createDirectories(dir.resolve("replicates"));
        writeAtomically(dir.resolve("input.fasta"), fasta);
        Properties properties = new Properties();
        properties.setProperty("replicates", Integer.toString(replicates));
        properties.setProperty("seed", Long.toString(seed));
        properties.setProperty("concurrency", Integer.toString(concurrency));
        properties.setProperty("timeoutSeconds", Long.toString(timeoutSeconds));
        writeProperties(dir.resolve("config.properties"), properties);
    }

    public boolean exists(String taskId) {
        return Files.isDirectory(taskDirectory(taskId));
    }

    public Path listRoot() {
        return workRoot;
    }

    public List<String> listTasks() throws IOException {
        if (!Files.isDirectory(workRoot)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(workRoot)) {
            return stream.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    public Alignment readAlignment(String taskId) throws IOException {
        return FastaReader.read(taskDirectory(taskId).resolve("input.fasta"));
    }

    public Properties readConfig(String taskId) throws IOException {
        Properties properties = new Properties();
        try (var in = Files.newBufferedReader(taskDirectory(taskId).resolve("config.properties"),
                StandardCharsets.UTF_8)) {
            properties.load(in);
        }
        return properties;
    }

    /**
     * Atomically stores one replicate tree; surviving files are complete by
     * construction (temp file + atomic move), so a crash never leaves a partial
     * replicate counted on recovery.
     */
    public void writeReplicate(String taskId, int replicate, TreeNode tree) throws IOException {
        Path target = replicatePath(taskId, replicate);
        writeAtomically(target, NewickWriter.write(tree) + System.lineSeparator());
    }

    public boolean replicateExists(String taskId, int replicate) {
        return Files.isRegularFile(replicatePath(taskId, replicate));
    }

    public TreeNode readReplicate(String taskId, int replicate) throws IOException {
        return NewickParser.parse(Files.readString(replicatePath(taskId, replicate),
                StandardCharsets.UTF_8));
    }

    public List<Integer> completedReplicates(String taskId, int total) throws IOException {
        Path dir = taskDirectory(taskId).resolve("replicates");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Integer> done = new ArrayList<>();
        try (Stream<Path> entries = Files.list(dir)) {
            entries.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("rep-") && name.endsWith(".nwk"))
                    .map(name -> name.substring(4, name.length() - 4))
                    .forEach(stem -> {
                        try {
                            int replicate = Integer.parseInt(stem);
                            if (replicate >= 0 && replicate < total) {
                                done.add(replicate);
                            }
                        } catch (NumberFormatException ignored) {
                            // ignore unrelated files
                        }
                    });
        }
        done.sort(Integer::compareTo);
        return done;
    }

    public boolean hasAnyReplicate(String taskId) throws IOException {
        Path dir = taskDirectory(taskId).resolve("replicates");
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.anyMatch(path -> path.getFileName().toString().endsWith(".nwk"));
        }
    }

    public void writeOutputs(String taskId, TreeNode consensus, List<TreeNode> replicateTrees)
            throws IOException {
        Path dir = taskDirectory(taskId);
        writeAtomically(dir.resolve("consensus.nwk"),
                NewickWriter.write(consensus) + System.lineSeparator());
        StringBuilder all = new StringBuilder();
        for (TreeNode tree : replicateTrees) {
            all.append(NewickWriter.write(tree)).append(System.lineSeparator());
        }
        writeAtomically(dir.resolve("bootstrap-trees.nwk"), all.toString());
    }

    public void writeStatus(String taskId, String statusJson) throws IOException {
        writeAtomically(taskDirectory(taskId).resolve("status.json"), statusJson);
    }

    public String readStatus(String taskId) throws IOException {
        return Files.readString(taskDirectory(taskId).resolve("status.json"), StandardCharsets.UTF_8);
    }

    public boolean hasOutputs(String taskId) {
        Path dir = taskDirectory(taskId);
        return Files.isRegularFile(dir.resolve("consensus.nwk"))
                && Files.isRegularFile(dir.resolve("bootstrap-trees.nwk"));
    }

    public String readOutput(String taskId, String fileName) throws IOException {
        Path target = taskDirectory(taskId).resolve(fileName);
        if (!fileName.equals("consensus.nwk") && !fileName.equals("bootstrap-trees.nwk")) {
            throw new IllegalArgumentException("not an output file: " + fileName);
        }
        return Files.readString(target, StandardCharsets.UTF_8);
    }

    private Path replicatePath(String taskId, int replicate) {
        return taskDirectory(taskId).resolve("replicates")
                .resolve(String.format("rep-%06d.nwk", replicate));
    }

    private void writeProperties(Path target, Properties properties) throws IOException {
        StringBuilder text = new StringBuilder();
        for (String name : properties.stringPropertyNames().stream().sorted().toList()) {
            text.append(name).append('=').append(properties.getProperty(name)).append('\n');
        }
        writeAtomically(target, text.toString());
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, ".tmp-" + target.getFileName() + "-", ".part");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicUnsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
