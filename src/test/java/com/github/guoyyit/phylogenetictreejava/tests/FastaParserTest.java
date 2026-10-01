package com.github.guoyyit.phylogenetictreejava.tests;

import com.github.guoyyit.phylogenetictreejava.io.FastaParser;
import com.github.guoyyit.phylogenetictreejava.model.AlignmentInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastaParserTest {

    private final FastaParser parser = new FastaParser();

    @Test
    void parsesMultilineRecordsAndStripsDescription() {
        List<AlignmentInput> records = parser.parse("""
                ; a comment line
                >seq1 first sequence
                ACGT
                ACGT
                >seq2
                TT-G
                """);

        assertEquals(2, records.size());
        assertEquals("seq1", records.get(0).name());
        assertEquals("ACGTACGT", records.get(0).sequence());
        assertEquals("seq2", records.get(1).name());
        assertEquals("TT-G", records.get(1).sequence());
    }

    @Test
    void emptyContentProducesNoRecords() {
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse("   \n").isEmpty());
    }
}
