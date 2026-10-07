package com.github.guoyyit.phylogenetictreejava;

import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapConfig;
import com.github.guoyyit.phylogenetictreejava.exception.AlignmentException;
import com.github.guoyyit.phylogenetictreejava.exception.RejectReason;
import com.github.guoyyit.phylogenetictreejava.service.AnalysisService;
import com.github.guoyyit.phylogenetictreejava.service.AnalysisStatus;
import com.github.guoyyit.phylogenetictreejava.service.AnalysisTask;
import com.github.guoyyit.phylogenetictreejava.service.TaskStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisServiceTest {

    private static final String FASTA = """
            >a
            ACGTACGTAAACGTACGTAA
            >b
            ACGTACGTAAACGTACGTAG
            >c
            ACGTTCGTAAATGGTACGTT
            >d
            TCGTTCGGAATTGGTACGTT
            """;

    private static void awaitTerminal(AnalysisService service, String id)
            throws InterruptedException {
        for (int i = 0; i < 500; i++) {
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

    @Test
    void invalidInputIsRejectedBeforeAnythingIsPersisted(@TempDir Path temp) {
        AnalysisService service = new AnalysisService(temp, 2);
        AlignmentException error = assertThrows(AlignmentException.class,
                () -> service.submit(">a\nACGT\n",
                        new BootstrapConfig(10, 1L, 1, 0)));
        assertEquals(RejectReason.TOO_FEW_SEQUENCES, error.getReason());
        assertFalse(service.status("anything").isPresent());
    }

    @Test
    void duplicateNamesAreRejectedSynchronouslyWithNoTaskDirectory(@TempDir Path temp)
            throws Exception {
        AnalysisService service = new AnalysisService(temp, 2);
        AlignmentException error = assertThrows(AlignmentException.class,
                () -> service.submit(">a\nACGT\n>b\nACGA\n>a\nACGC\n",
                        new BootstrapConfig(10, 1L, 1, 0)));
        assertEquals(RejectReason.DUPLICATE_NAME, error.getReason());
        // No 202: the rejection must not create a task or leave a working directory.
        assertEquals(List.of(), java.nio.file.Files.list(temp).toList());
        service.shutdown();
    }

    @Test
    void pairWithoutComparableSitesIsRejectedSynchronouslyWithNoTaskDirectory(
            @TempDir Path temp) throws Exception {
        AnalysisService service = new AnalysisService(temp, 2);
        AlignmentException error = assertThrows(AlignmentException.class,
                () -> service.submit(
                        ">a\nACGTACGT\n>b\n--NNNNNN\n>c\nTTTTAAAA\n",
                        new BootstrapConfig(10, 1L, 1, 0)));
        assertEquals(RejectReason.INSUFFICIENT_INFORMATION, error.getReason());
        assertEquals(List.of(), java.nio.file.Files.list(temp).toList());
        service.shutdown();
    }

    @Test
    void concurrentTasksRunIndependentlyAndComplete(@TempDir Path temp) throws Exception {
        AnalysisService service = new AnalysisService(temp, 4);
        String id1 = service.submit(FASTA, new BootstrapConfig(30, 11L, 2, 0));
        String id2 = service.submit(FASTA, new BootstrapConfig(30, 22L, 2, 0));
        awaitTerminal(service, id1);
        awaitTerminal(service, id2);
        assertEquals(AnalysisStatus.COMPLETED, service.status(id1).orElseThrow().getStatus());
        assertEquals(AnalysisStatus.COMPLETED, service.status(id2).orElseThrow().getStatus());
        String consensus1 = service.getStore().readOutput(id1, "consensus.nwk");
        String consensus2 = service.getStore().readOutput(id2, "consensus.nwk");
        assertFalse(consensus1.isBlank());
        assertFalse(consensus2.isBlank());
        service.shutdown();
    }

    @Test
    void identicalSeedsGiveIdenticalFilesAcrossRuns(@TempDir Path temp)
            throws Exception {
        AnalysisService service1 = new AnalysisService(temp, 1);
        String firstId = service1.submit(FASTA, new BootstrapConfig(40, 99L, 1, 0));
        awaitTerminal(service1, firstId);
        String trees1 = service1.getStore().readOutput(firstId, "bootstrap-trees.nwk");
        String consensus1 = service1.getStore().readOutput(firstId, "consensus.nwk");
        service1.shutdown();

        Path other = temp.resolveSibling("other-work");
        AnalysisService service2 = new AnalysisService(other, 8);
        String secondId = service2.submit(FASTA, new BootstrapConfig(40, 99L, 8, 0));
        awaitTerminal(service2, secondId);
        String trees2 = service2.getStore().readOutput(secondId, "bootstrap-trees.nwk");
        String consensus2 = service2.getStore().readOutput(secondId, "consensus.nwk");
        service2.shutdown();

        assertEquals(trees1, trees2);
        assertEquals(consensus1, consensus2);
    }

    @Test
    void cancellationLeavesNoFinalOutputs(@TempDir Path temp) throws Exception {
        AnalysisService service = new AnalysisService(temp, 1);
        String id = service.submit(FASTA, new BootstrapConfig(100000, 5L, 1, 0));
        Thread.sleep(100);
        assertTrue(service.cancel(id));
        awaitTerminal(service, id);
        assertEquals(AnalysisStatus.CANCELLED, service.status(id).orElseThrow().getStatus());
        assertFalse(service.getStore().hasOutputs(id));
        service.shutdown();
    }

    @Test
    void timeoutLeavesNoFinalOutputs(@TempDir Path temp) throws Exception {
        AnalysisService service = new AnalysisService(temp, 1);
        String id = service.submit(FASTA, new BootstrapConfig(10000000, 5L, 1, 1));
        awaitTerminal(service, id);
        assertEquals(AnalysisStatus.TIMEOUT, service.status(id).orElseThrow().getStatus());
        assertFalse(service.getStore().hasOutputs(id));
        service.shutdown();
    }

    @Test
    void restartSkipsCompletedReplicatesAndProducesIdenticalResult(@TempDir Path temp)
            throws Exception {
        int total = 5000;
        String interruptedId;
        try (AnalysisServiceInterruption killed = new AnalysisServiceInterruption(temp, 1)) {
            interruptedId = killed.service.submit(FASTA,
                    new BootstrapConfig(total, 777L, 1, 0));
            Thread.sleep(400);
            assertTrue(killed.service.getStore().completedReplicates(interruptedId, total).size()
                    < total, "task must still be running when the process is killed");
            // Abrupt "crash": shutdown without cancelling or finalizing.
        }

        int before = new com.github.guoyyit.phylogenetictreejava.service.TaskStore(temp)
                .completedReplicates(interruptedId, total).size();
        assertTrue(before > 0, "expected some persisted replicates after crash, got 0");

        AnalysisService recovered = new AnalysisService(temp, 2);
        recovered.recoverInterruptedTasks();
        awaitTerminal(recovered, interruptedId);
        assertEquals(AnalysisStatus.COMPLETED,
                recovered.status(interruptedId).orElseThrow().getStatus());

        AnalysisService uninterrupted = new AnalysisService(
                temp.resolveSibling("clean"), 2);
        String cleanId = uninterrupted.submit(FASTA,
                new BootstrapConfig(total, 777L, 2, 0));
        awaitTerminal(uninterrupted, cleanId);

        assertEquals(uninterrupted.getStore().readOutput(cleanId, "bootstrap-trees.nwk"),
                recovered.getStore().readOutput(interruptedId, "bootstrap-trees.nwk"));
        assertEquals(uninterrupted.getStore().readOutput(cleanId, "consensus.nwk"),
                recovered.getStore().readOutput(interruptedId, "consensus.nwk"));
        recovered.shutdown();
        uninterrupted.shutdown();
    }

    // ------------------------------------------------------------------
    // Large-scale requirements (>=128 sequences, >=200 columns).
    // ------------------------------------------------------------------

    private static final int LARGE_SEQUENCES = 128;
    private static final int LARGE_LENGTH = 200;
    private static final int LARGE_REPLICATES = 40;

    @Test
    void largeRunIsByteIdenticalAtConcurrency1And8(@TempDir Path temp) throws Exception {
        String fasta = LargeAlignmentFixtures.randomDnaFasta(
                LARGE_SEQUENCES, LARGE_LENGTH, 8080L);

        Path serialDir = temp.resolve("serial");
        AnalysisService serial = new AnalysisService(serialDir, 1);
        String serialId = serial.submit(fasta,
                new BootstrapConfig(LARGE_REPLICATES, 2718L, 1, 0));
        awaitTerminal(serial, serialId);
        assertEquals(AnalysisStatus.COMPLETED,
                serial.status(serialId).orElseThrow().getStatus());

        Path parallelDir = temp.resolve("parallel");
        AnalysisService parallel = new AnalysisService(parallelDir, 8);
        String parallelId = parallel.submit(fasta,
                new BootstrapConfig(LARGE_REPLICATES, 2718L, 8, 0));
        awaitTerminal(parallel, parallelId);
        assertEquals(AnalysisStatus.COMPLETED,
                parallel.status(parallelId).orElseThrow().getStatus());

        assertEquals(serial.getStore().readOutput(serialId, "bootstrap-trees.nwk"),
                parallel.getStore().readOutput(parallelId, "bootstrap-trees.nwk"));
        assertEquals(serial.getStore().readOutput(serialId, "consensus.nwk"),
                parallel.getStore().readOutput(parallelId, "consensus.nwk"));
        serial.shutdown();
        parallel.shutdown();
    }

    @Test
    void largeRunRecoveryIsByteIdenticalToUninterruptedRun(@TempDir Path temp) throws Exception {
        String fasta = LargeAlignmentFixtures.randomDnaFasta(
                LARGE_SEQUENCES, LARGE_LENGTH, 9090L);
        int total = LARGE_REPLICATES;
        String interruptedId;
        try (AnalysisServiceInterruption killed =
                     new AnalysisServiceInterruption(temp.resolve("crash"), 8)) {
            interruptedId = killed.service.submit(fasta,
                    new BootstrapConfig(total, 1618L, 8, 0));
            awaitSomeButNotAllReplicates(killed.service, interruptedId, total);
            // Abrupt "crash": shutdown without cancelling or finalizing.
        }

        TaskStore store = new TaskStore(temp.resolve("crash"));
        int before = store.completedReplicates(interruptedId, total).size();
        assertTrue(before > 0 && before < total,
                "expected a partial set of persisted replicates, got " + before);

        AnalysisService recovered = new AnalysisService(temp.resolve("crash"), 8);
        recovered.recoverInterruptedTasks();
        awaitTerminal(recovered, interruptedId);
        assertEquals(AnalysisStatus.COMPLETED,
                recovered.status(interruptedId).orElseThrow().getStatus());
        // Already completed replicates are never recomputed or counted twice.
        assertEquals(total,
                recovered.status(interruptedId).orElseThrow().getCompletedReplicates());

        AnalysisService uninterrupted = new AnalysisService(temp.resolve("clean"), 8);
        String cleanId = uninterrupted.submit(fasta,
                new BootstrapConfig(total, 1618L, 8, 0));
        awaitTerminal(uninterrupted, cleanId);

        assertEquals(uninterrupted.getStore().readOutput(cleanId, "bootstrap-trees.nwk"),
                recovered.getStore().readOutput(interruptedId, "bootstrap-trees.nwk"));
        assertEquals(uninterrupted.getStore().readOutput(cleanId, "consensus.nwk"),
                recovered.getStore().readOutput(interruptedId, "consensus.nwk"));
        recovered.shutdown();
        uninterrupted.shutdown();
    }

    @Test
    void largeCancellationWritesNoPartialFinalResultAndDoesNotAffectOtherTasks(
            @TempDir Path temp) throws Exception {
        AnalysisService service = new AnalysisService(temp, 8);
        String fasta = LargeAlignmentFixtures.randomDnaFasta(
                LARGE_SEQUENCES, LARGE_LENGTH, 3030L);

        String victim = service.submit(fasta,
                new BootstrapConfig(10_000_000, 5L, 8, 0));
        String survivor = service.submit(fasta,
                new BootstrapConfig(LARGE_REPLICATES, 6L, 8, 0));

        Thread.sleep(300);
        assertTrue(service.cancel(victim));
        awaitTerminal(service, victim);
        assertEquals(AnalysisStatus.CANCELLED,
                service.status(victim).orElseThrow().getStatus());
        assertFalse(service.getStore().hasOutputs(victim));
        assertNoTempFiles(service, victim);

        // The independent task still completes and produces intact outputs.
        awaitTerminal(service, survivor);
        assertEquals(AnalysisStatus.COMPLETED,
                service.status(survivor).orElseThrow().getStatus());
        assertTrue(service.getStore().hasOutputs(survivor));
        assertFalse(service.getStore().readOutput(survivor, "consensus.nwk").isBlank());
        assertEquals(LARGE_REPLICATES,
                service.getStore().readOutput(survivor, "bootstrap-trees.nwk").lines()
                        .filter(line -> !line.isBlank()).count());
        service.shutdown();
    }

    @Test
    void largeTimeoutWritesNoPartialFinalResult(@TempDir Path temp) throws Exception {
        AnalysisService service = new AnalysisService(temp, 8);
        String fasta = LargeAlignmentFixtures.randomDnaFasta(
                LARGE_SEQUENCES, LARGE_LENGTH, 4040L);
        String id = service.submit(fasta,
                new BootstrapConfig(10_000_000, 5L, 8, 1));
        awaitTerminal(service, id);
        assertEquals(AnalysisStatus.TIMEOUT, service.status(id).orElseThrow().getStatus());
        assertFalse(service.getStore().hasOutputs(id));
        assertNoTempFiles(service, id);
        service.shutdown();
    }

    private static void awaitSomeButNotAllReplicates(AnalysisService service, String id, int total)
            throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            try {
                int done = service.getStore().completedReplicates(id, total).size();
                if (done > 0 && done < total) {
                    return;
                }
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
            Thread.sleep(20);
        }
        throw new AssertionError("task did not reach a partial state before the crash");
    }

    private static void assertNoTempFiles(AnalysisService service, String id)
            throws java.io.IOException {
        try (java.util.stream.Stream<Path> entries =
                     java.nio.file.Files.walk(service.getStore().taskDirectory(id))) {
            boolean leftover = entries
                    .anyMatch(path -> path.getFileName().toString().startsWith(".tmp-"));
            assertFalse(leftover, "temp/partial files left behind for task " + id);
        }
    }

    private static final class AnalysisServiceInterruption implements AutoCloseable {
        private final AnalysisService service;

        private AnalysisServiceInterruption(Path temp, int concurrency) {
            this.service = new AnalysisService(temp, concurrency);
        }

        @Override
        public void close() {
            service.shutdownAbruptly();
        }
    }
}
