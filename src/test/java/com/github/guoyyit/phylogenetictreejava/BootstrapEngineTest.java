package com.github.guoyyit.phylogenetictreejava;

import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapCancelledException;
import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapConfig;
import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapEngine;
import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapResult;
import com.github.guoyyit.phylogenetictreejava.io.FastaReader;
import com.github.guoyyit.phylogenetictreejava.io.NewickWriter;
import com.github.guoyyit.phylogenetictreejava.model.Alignment;

import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BootstrapEngineTest {

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

    @Test
    void sameSeedProducesByteIdenticalOutput() {
        Alignment alignment = FastaReader.parse(FASTA);
        BootstrapConfig config = new BootstrapConfig(50, 1234567L, 1, 0);
        BootstrapResult first = new BootstrapEngine(1).run(alignment, config);
        BootstrapResult second = new BootstrapEngine(1).run(alignment, config);
        assertEquals(serialize(first), serialize(second));
    }

    @Test
    void outputIsIndependentOfConcurrencyAndOrdering() {
        Alignment alignment = FastaReader.parse(FASTA);
        BootstrapResult serial = new BootstrapEngine(1)
                .run(alignment, new BootstrapConfig(80, 42L, 1, 0));
        BootstrapResult parallel = new BootstrapEngine(8)
                .run(alignment, new BootstrapConfig(80, 42L, 8, 0));
        assertEquals(serialize(serial), serialize(parallel));
    }

    @Test
    void differentSeedsMayProduceDifferentResampling() {
        Alignment alignment = FastaReader.parse(FASTA);
        BootstrapResult first = new BootstrapEngine(1)
                .run(alignment, new BootstrapConfig(40, 1L, 1, 0));
        BootstrapResult second = new BootstrapEngine(1)
                .run(alignment, new BootstrapConfig(40, 2L, 1, 0));
        assertNotEquals(serialize(first), serialize(second));
    }

    @Test
    void cancellationStopsTheRun() {
        Alignment alignment = FastaReader.parse(FASTA);
        AtomicCancelled cancelled = new AtomicCancelled();
        BootstrapEngine engine = new BootstrapEngine(1);
        cancelled.set(true);
        assertThrows(BootstrapCancelledException.class,
                () -> engine.run(alignment,
                        new BootstrapConfig(10, 7L, 1, 0), cancelled::get));
    }

    private static String serialize(BootstrapResult result) {
        return result.bootstrapTrees().stream()
                .map(NewickWriter::write)
                .collect(Collectors.joining("\n"))
                + "\n#CONSENSUS#\n"
                + NewickWriter.write(result.consensusTree());
    }

    private static final class AtomicCancelled {
        private volatile boolean value;

        private void set(boolean value) {
            this.value = value;
        }

        private boolean get() {
            return value;
        }
    }
}
