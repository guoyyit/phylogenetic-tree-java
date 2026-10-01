package com.github.guoyyit.phylogenetictreejava.bootstrap;

import com.github.guoyyit.phylogenetictreejava.io.NewickCodec;
import com.github.guoyyit.phylogenetictreejava.tree.PhyloTree;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * {@link CheckpointStore} backed by one atomically renamed Newick file per
 * replicate under {@code <taskDir>/replicates/}.
 */
public final class FileCheckpointStore implements CheckpointStore {

    private static final Pattern REPLICATE_FILE = Pattern.compile("replicate-(\\d+)\\.nwk");

    private final Path replicateDirectory;

    public FileCheckpointStore(Path taskDirectory) {
        try {
            this.replicateDirectory = Files.createDirectories(
                    taskDirectory.resolve("replicates"));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create checkpoint directory", e);
        }
    }

    @Override
    public SortedMap<Integer, PhyloTree> loadCompleted() {
        NewickCodec codec = new NewickCodec();
        SortedMap<Integer, PhyloTree> completed = new TreeMap<>();
        try (Stream<Path> files = Files.list(replicateDirectory)) {
            List<Path> paths = files
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
            for (Path path : paths) {
                Matcher matcher = REPLICATE_FILE.matcher(path.getFileName().toString());
                if (!matcher.matches()) {
                    continue;
                }
                int index = Integer.parseInt(matcher.group(1));
                String content = Files.readString(path, StandardCharsets.UTF_8);
                completed.put(index, codec.parse(content));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot load checkpoints", e);
        }
        return completed;
    }

    @Override
    public void saveReplicate(int replicateIndex, PhyloTree tree) {
        String content = new NewickCodec().write(tree);
        Path target = replicateDirectory.resolve(
                String.format("replicate-%05d.nwk", replicateIndex));
        Path temporary = replicateDirectory.resolve(
                String.format("replicate-%05d.nwk.tmp-%d",
                        replicateIndex, Thread.currentThread().getId()));
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // best effort cleanup of the temporary file
            }
            throw new UncheckedIOException(
                    "cannot checkpoint replicate " + replicateIndex, e);
        }
    }
}
