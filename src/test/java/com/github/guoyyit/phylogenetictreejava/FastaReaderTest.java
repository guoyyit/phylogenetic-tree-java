package com.github.guoyyit.phylogenetictreejava;

import com.github.guoyyit.phylogenetictreejava.exception.AlignmentException;
import com.github.guoyyit.phylogenetictreejava.exception.RejectReason;
import com.github.guoyyit.phylogenetictreejava.io.FastaReader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastaReaderTest {

    private static AlignmentException reject(String fasta) {
        return assertThrows(AlignmentException.class, () -> FastaReader.parse(fasta));
    }

    @Test
    void rejectsNullAndBlankAsEmpty() {
        assertEquals(RejectReason.EMPTY_INPUT, reject(null).getReason());
        assertEquals(RejectReason.EMPTY_INPUT, reject("   \n").getReason());
        assertEquals(RejectReason.EMPTY_INPUT, reject(">a\n").getReason());
    }

    @Test
    void rejectsSingleSequenceWithDistinctReason() {
        AlignmentException error = reject(">a\nACGT\n");
        assertEquals(RejectReason.TOO_FEW_SEQUENCES, error.getReason());
    }

    @Test
    void rejectsUnequalLengthsWithDistinctReason() {
        AlignmentException error = reject(">a\nACGT\n>b\nACG\n");
        assertEquals(RejectReason.LENGTH_MISMATCH, error.getReason());
    }

    @Test
    void rejectsInvalidCharacters() {
        AlignmentException error = reject(">a\nACGZ\n>b\nACGT\n");
        assertEquals(RejectReason.INVALID_CHARACTER, error.getReason());
    }

    @Test
    void rejectsAllGapAlignmentAsInsufficientInformation() {
        AlignmentException error = reject(">a\n----\n>b\n----\n>c\n-??-\n");
        assertEquals(RejectReason.INSUFFICIENT_INFORMATION, error.getReason());
    }

    @Test
    void rejectsDuplicateNamesWithDistinctClientReason() {
        AlignmentException error = reject(">a\nACGT\n>b\nACGA\n>a\nACGC\n");
        assertEquals(RejectReason.DUPLICATE_NAME, error.getReason());
        assertTrue(error.getMessage().contains("a"));
    }

    @Test
    void rejectsPairWithoutComparableSitesEvenWhenAlignmentIsOtherwiseValid() {
        // Overall the alignment is informative (a/c share sites and b/c share
        // sites), but a and b never share a non-gap, non-unknown site.
        AlignmentException error = reject(
                ">a\nACGTACGT\n>b\n--NNNNNN\n>c\nTTTTAAAA\n");
        assertEquals(RejectReason.INSUFFICIENT_INFORMATION, error.getReason());
        assertTrue(error.getMessage().contains("'a'"));
        assertTrue(error.getMessage().contains("'b'"));
    }

    @Test
    void duplicateNameReasonDiffersFromInsufficientInformationReason() {
        AlignmentException duplicate = reject(">x\nACGT\n>y\nACGA\n>x\nACGC\n");
        AlignmentException insufficient = reject(">a\nAC\n>b\nNN\n");
        assertEquals(RejectReason.DUPLICATE_NAME, duplicate.getReason());
        assertEquals(RejectReason.INSUFFICIENT_INFORMATION, insufficient.getReason());
    }

    @Test
    void acceptsAmbiguityCodesAndGapsMixedWithKnownSites() {
        var alignment = FastaReader.parse(
                ">a\nACGT-N?\n>b\nARGN.N-\n>c\nTTT.TN.\n");
        assertEquals(3, alignment.size());
        assertEquals(7, alignment.length());
        assertTrue(alignment.sequences().get(0).data().contains("-"));
    }

    @Test
    void normalizesLowercaseInput() {
        var alignment = FastaReader.parse(">a\nacgt\n>b\nacgn\n");
        assertEquals("ACGT", alignment.sequences().get(0).data());
    }
}
