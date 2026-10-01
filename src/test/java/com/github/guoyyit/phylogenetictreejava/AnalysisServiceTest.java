package com.github.guoyyit.phylogenetictreejava;

import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapConfig;
import com.github.guoyyit.phylogenetictreejava.exception.AlignmentException;
import com.github.guoyyit.phylogenetictreejava.exception.RejectReason;
import com.github.guoyyit.phylogenetictreejava.service.AnalysisService;
import com.github.guoyyit.phylogenetictreejava.service.AnalysisStatus;
import com.github.guoyyit.phylogenetictreejava.service.AnalysisTask;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

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
