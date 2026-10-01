package com.github.guoyyit.phylogenetictreejava.tests;

import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapSupport;
import com.github.guoyyit.phylogenetictreejava.bootstrap.CancellationToken;
import com.github.guoyyit.phylogenetictreejava.bootstrap.FileCheckpointStore;
import com.github.guoyyit.phylogenetictreejava.io.AlignmentValidator;
import com.github.guoyyit.phylogenetictreejava.io.NewickCodec;
import com.github.guoyyit.phylogenetictreejava.model.Alignment;
import com.github.guoyyit.phylogenetictreejava.model.AlignmentInput;
import com.github.guoyyit.phylogenetictreejava.service.AnalysisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BootstrapDeterminismTest {

    private Alignment sampleAlignment() {
        return new AlignmentValidator().validate(List.of(
                new AlignmentInput("alpha", "ACGTACGTACGT"),
                new AlignmentInput("bravo", "ACGTACGTACGA"),
                new AlignmentInput("charlie", "TCGTTCGTTCGA"),
                new AlignmentInput("delta", "TCGTTTGTTCGA"),
                new AlignmentInput("echo", "TCGTTCGTTTGA")));
    }

    private String run(Alignment alignment, long seed, int concurrency,
                       Path workDirectory) {
        AnalysisService service = new AnalysisService();
        FileCheckpointStore store = new FileCheckpointStore(workDirectory);
        BootstrapSupport support = service.bootstrap(alignment, 40, seed,
                concurrency, store, new CancellationToken());
        return service.toNewick(support.replicateTrees())
                + service.toNewick(support.consensusTree());
    }

    @Test
    void sameSeedProducesByteIdenticalOutputAcrossConcurrency(@TempDir Path tempDir) {
        Alignment alignment = sampleAlignment();

        String serial = run(alignment, 42L, 1, tempDir.resolve("serial"));
        String parallel = run(alignment, 42L, 8, tempDir.resolve("parallel"));

        assertEquals(serial, parallel);
    }

    @Test
    void differentSeedsMayDifferAndRepeatedRunsMatch(@TempDir Path tempDir) {
        Alignment alignment = sampleAlignment();

        String first = run(alignment, 1L, 4, tempDir.resolve("first"));
        String second = run(alignment, 2L, 4, tempDir.resolve("second"));
        String repeated = run(alignment, 1L, 4, tempDir.resolve("repeated"));

        assertEquals(first, repeated);
        assertNotEquals(first, second);
    }
}
