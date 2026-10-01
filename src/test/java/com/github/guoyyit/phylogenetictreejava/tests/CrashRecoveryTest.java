package com.github.guoyyit.phylogenetictreejava.tests;

import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapSupport;
import com.github.guoyyit.phylogenetictreejava.bootstrap.CancellationToken;
import com.github.guoyyit.phylogenetictreejava.bootstrap.CheckpointStore;
import com.github.guoyyit.phylogenetictreejava.bootstrap.FileCheckpointStore;
import com.github.guoyyit.phylogenetictreejava.io.AlignmentValidator;
import com.github.guoyyit.phylogenetictreejava.model.Alignment;
import com.github.guoyyit.phylogenetictreejava.model.AlignmentInput;
import com.github.guoyyit.phylogenetictreejava.service.AnalysisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrashRecoveryTest {

    private Alignment sampleAlignment() {
        return new AlignmentValidator().validate(List.of(
                new AlignmentInput("alpha", "ACGTACGTACGTAA"),
                new AlignmentInput("bravo", "ACGTACGTACGATA"),
                new AlignmentInput("charlie", "TCGTTCGTTCGATA"),
                new AlignmentInput("delta", "TCGTTTGTTCGATA"),
                new AlignmentInput("echo", "TCGTTCGTTTGATA")));
    }

    @Test
    void resumedRunSkipsCompletedReplicatesAndMatchesUninterruptedRun(
            @TempDir Path tempDir) {
        Alignment alignment = sampleAlignment();
        int replicates = 30;
        long seed = 7L;

        Path uninterruptedDir = tempDir.resolve("uninterrupted");
        Path interruptedDir = tempDir.resolve("interrupted");
        AnalysisService service = new AnalysisService();

        BootstrapSupport uninterrupted = service.bootstrap(alignment, replicates,
                seed, 4, new FileCheckpointStore(uninterruptedDir),
                new CancellationToken());

        for (int replicate = 0; replicate < 12; replicate++) {
            new FileCheckpointStore(interruptedDir).saveReplicate(
                    replicate, uninterrupted.replicateTrees().get(replicate));
        }

        AtomicInteger recomputed = new AtomicInteger();
        CheckpointStore countingStore = new CheckpointStore() {
            private final FileCheckpointStore delegate =
                    new FileCheckpointStore(interruptedDir);

            @Override
            public java.util.SortedMap<Integer,
                    com.github.guoyyit.phylogenetictreejava.tree.PhyloTree>
                    loadCompleted() {
                return delegate.loadCompleted();
            }

            @Override
            public void saveReplicate(int replicateIndex,
                                      com.github.guoyyit.phylogenetictreejava.tree.PhyloTree tree) {
                recomputed.incrementAndGet();
                delegate.saveReplicate(replicateIndex, tree);
            }
        };

        BootstrapSupport resumed = service.bootstrap(alignment, replicates,
                seed, 4, countingStore, new CancellationToken());

        assertTrue(recomputed.get() > 0,
                "resumption must finish the remaining replicates");
        assertTrue(recomputed.get() < replicates,
                "resumption must skip already completed replicates");

        assertEquals(service.toNewick(uninterrupted.replicateTrees()),
                service.toNewick(resumed.replicateTrees()));
        assertEquals(service.toNewick(uninterrupted.consensusTree()),
                service.toNewick(resumed.consensusTree()));
    }
}
