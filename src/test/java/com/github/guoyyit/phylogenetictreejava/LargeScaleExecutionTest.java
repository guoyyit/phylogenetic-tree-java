package com.github.guoyyit.phylogenetictreejava;

import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapConfig;
import com.github.guoyyit.phylogenetictreejava.service.AnalysisService;
import com.github.guoyyit.phylogenetictreejava.service.AnalysisStatus;
import com.github.guoyyit.phylogenetictreejava.service.AnalysisTask;
import com.github.guoyyit.phylogenetictreejava.service.TaskStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Determinism, crash recovery, cancellation and timeout for large inputs
 * (128 taxa x 200 columns).
 */
class LargeScaleExecutionTest {

    private static final int TAXA = 128;
    private static final int LENGTH = 200;
    private static final int REPLICATES = 120;
    private static final long SEED = 20261007L;

    private static String largeFasta() {
        Random rng = new Random(987654321L);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TAXA; i++) {
            sb.append(">t").append(i).append('\n');
            for (int k = 0; k < LENGTH; k++) {
                sb.append("ACGT".charAt(rng.nextInt(4)));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void awaitTerminal(AnalysisService service, String id)
            throws InterruptedException {
        for (int i = 0; i < 1000; i++) {
            AnalysisTask task = service.status(id).orElseThrow();
            if (task.getStatus() == AnalysisStatus.COMPLETED
                    || task.getStatus() == AnalysisStatus.FAILED
                    || task.getStatus() == AnalysisStatus.CANCELLED
                    || task.getStatus() == AnalysisStatus.TIMEOUT) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("task did not finish: " + id);
    }

    private static boolean hasTempFiles(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.anyMatch(p -> p.getFileName().toString().startsWith(".tmp-"));
        }
    }

    @Test
    void sameSeedIsByteIdenticalAtConcurrencyOneAndEight(@TempDir Path temp) throws Exception {
        String fasta = largeFasta();

        AnalysisService serial = new AnalysisService(temp.resolve("serial"), 1);
        String id1 = serial.submit(fasta, new BootstrapConfig(REPLICATES, SEED, 1, 0));
        awaitTerminal(serial, id1);
        assertEquals(AnalysisStatus.COMPLETED,
                serial.status(id1).orElseThrow().getStatus());
        String consensus1 = serial.getStore().readOutput(id1, "consensus.nwk");
        String trees1 = serial.getStore().readOutput(id1, "bootstrap-trees.nwk");
        serial.shutdown();

        AnalysisService parallel = new AnalysisService(temp.resolve("parallel"), 8);
        String id2 = parallel.submit(fasta, new BootstrapConfig(REPLICATES, SEED, 8, 0));
        awaitTerminal(parallel, id2);
        assertEquals(AnalysisStatus.COMPLETED,
                parallel.status(id2).orElseThrow().getStatus());
        String consensus2 = parallel.getStore().readOutput(id2, "consensus.nwk");
        String trees2 = parallel.getStore().readOutput(id2, "bootstrap-trees.nwk");
        parallel.shutdown();

        assertEquals(consensus1, consensus2, "consensus tree differs with concurrency");
        assertEquals(trees1, trees2, "bootstrap trees differ with concurrency");
        assertEquals(REPLICATES, trees1.lines().filter(l -> !l.isBlank()).count());
    }

    @Test
    void resumedLargeRunIsByteIdenticalAndSkipsDoneReplicates(@TempDir Path temp)
            throws Exception {
        String fasta = largeFasta();
        int total = 4000;
        String interruptedId;
        int killedCount;
        try (Killer killer = new Killer(temp, 2)) {
            interruptedId = killer.service.submit(fasta,
                    new BootstrapConfig(total, SEED, 2, 0));
            long deadline = System.currentTimeMillis() + 3000;
            int seen = 0;
            while (System.currentTimeMillis() < deadline) {
                seen = killer.service.getStore().completedReplicates(interruptedId, total)
                        .size();
                if (seen >= 200) {
                    break;
                }
                Thread.sleep(25);
            }
            assertTrue(seen > 0, "expected completed replicates before the crash");
            assertTrue(seen < total, "task must still be running when the process is killed");
        }

        TaskStore probe = new TaskStore(temp);
        java.util.List<Integer> before = probe.completedReplicates(interruptedId, total);
        killedCount = before.size();
        assertTrue(killedCount > 0);
        assertFalse(probe.hasOutputs(interruptedId),
                "crash must not leave final outputs behind");

        // Snapshot a few persisted replicates; recovery must neither recompute nor
        // rewrite them, so byte content and last-modified time stay untouched.
        java.util.Map<Integer, byte[]> snapshot = new java.util.HashMap<>();
        java.util.Map<Integer, Long> snapshotMtimes = new java.util.HashMap<>();
        for (Integer index : before.subList(0, Math.min(5, before.size()))) {
            Path file = temp.resolve(interruptedId).resolve("replicates")
                    .resolve(String.format("rep-%06d.nwk", index));
            snapshot.put(index, Files.readAllBytes(file));
            snapshotMtimes.put(index, Files.getLastModifiedTime(file).toMillis());
        }

        AnalysisService recovered = new AnalysisService(temp, 4);
        recovered.recoverInterruptedTasks();
        awaitTerminal(recovered, interruptedId);
        assertEquals(AnalysisStatus.COMPLETED,
                recovered.status(interruptedId).orElseThrow().getStatus());

        for (java.util.Map.Entry<Integer, byte[]> entry : snapshot.entrySet()) {
            Path file = temp.resolve(interruptedId).resolve("replicates")
                    .resolve(String.format("rep-%06d.nwk", entry.getKey()));
            assertEquals(new String(entry.getValue()), Files.readString(file),
                    "previously completed replicate " + entry.getKey() + " was rewritten");
            assertEquals(snapshotMtimes.get(entry.getKey()),
                    Files.getLastModifiedTime(file).toMillis(),
                    "replicate " + entry.getKey() + " file mtime changed (recomputed)");
        }

        // Already-persisted replicates must not be recomputed: their files keep their
        // original content and the set of completed indices only grows.
        java.util.List<Integer> after = recovered.getStore()
                .completedReplicates(interruptedId, total);
        assertTrue(after.containsAll(before), "recovery lost previously done replicates");
        assertEquals(total, after.size(), "some replicates were never assembled");
        assertEquals(total, recovered.status(interruptedId).orElseThrow()
                .getCompletedReplicates());

        AnalysisService clean = new AnalysisService(temp.resolveSibling("clean"), 2);
        String cleanId = clean.submit(fasta, new BootstrapConfig(total, SEED, 2, 0));
        awaitTerminal(clean, cleanId);
        assertEquals(AnalysisStatus.COMPLETED, clean.status(cleanId).orElseThrow().getStatus());

        assertEquals(clean.getStore().readOutput(cleanId, "bootstrap-trees.nwk"),
                recovered.getStore().readOutput(interruptedId, "bootstrap-trees.nwk"),
                "resumed bootstrap trees must be byte-identical to an uninterrupted run");
        assertEquals(clean.getStore().readOutput(cleanId, "consensus.nwk"),
                recovered.getStore().readOutput(interruptedId, "consensus.nwk"),
                "resumed consensus must be byte-identical to an uninterrupted run");
        recovered.shutdown();
        clean.shutdown();
    }

    @Test
    void cancelledLargeRunWritesNoFinalOutputAndDoesNotAffectOthers(@TempDir Path temp)
            throws Exception {
        AnalysisService service = new AnalysisService(temp, 4);

        String otherId = service.submit(largeFasta(),
                new BootstrapConfig(30, 55L, 2, 0));

        String cancelledId = service.submit(largeFasta(),
                new BootstrapConfig(10_000_000, SEED, 4, 0));
        Thread.sleep(150);
        assertTrue(service.cancel(cancelledId));
        awaitTerminal(service, cancelledId);

        assertEquals(AnalysisStatus.CANCELLED,
                service.status(cancelledId).orElseThrow().getStatus());
        assertFalse(service.getStore().hasOutputs(cancelledId),
                "cancellation must not produce final outputs");
        assertFalse(Files.exists(service.getStore()
                .taskDirectory(cancelledId).resolve("consensus.nwk.tmp")));
        assertFalse(hasTempFiles(service.getStore().taskDirectory(cancelledId)),
                "cancellation must not leave temporary files");

        // The independent task still completes with intact outputs.
        awaitTerminal(service, otherId);
        assertEquals(AnalysisStatus.COMPLETED,
                service.status(otherId).orElseThrow().getStatus());
        assertTrue(service.getStore().hasOutputs(otherId));
        assertEquals(30, service.getStore().readOutput(otherId, "bootstrap-trees.nwk")
                .lines().filter(l -> !l.isBlank()).count());
        service.shutdown();
    }

    @Test
    void timedOutLargeRunWritesNoFinalOutput(@TempDir Path temp) throws Exception {
        AnalysisService service = new AnalysisService(temp, 4);
        String id = service.submit(largeFasta(),
                new BootstrapConfig(10_000_000, SEED, 4, 1));
        awaitTerminal(service, id);
        assertEquals(AnalysisStatus.TIMEOUT, service.status(id).orElseThrow().getStatus());
        assertFalse(service.getStore().hasOutputs(id),
                "timeout must not produce final outputs");
        assertFalse(hasTempFiles(service.getStore().taskDirectory(id)),
                "timeout must not leave temporary files");
        service.shutdown();
    }

    private static final class Killer implements AutoCloseable {
        private final AnalysisService service;

        private Killer(Path workDir, int concurrency) {
            this.service = new AnalysisService(workDir, concurrency);
        }

        @Override
        public void close() {
            service.shutdownAbruptly();
        }
    }
}
