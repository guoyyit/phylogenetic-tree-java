package com.github.guoyyit.phylogenetictreejava;

import com.github.guoyyit.phylogenetictreejava.distance.DistanceCalculator;
import com.github.guoyyit.phylogenetictreejava.exception.AlignmentException;
import com.github.guoyyit.phylogenetictreejava.exception.RejectReason;
import com.github.guoyyit.phylogenetictreejava.io.FastaReader;
import com.github.guoyyit.phylogenetictreejava.model.DistanceMatrix;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistanceCalculatorTest {

    @Test
    void identicalSequencesHaveZeroDistance() {
        DistanceMatrix matrix = DistanceCalculator.compute(FastaReader.parse(
                ">a\nACGTACGT\n>b\nACGTACGT\n"));
        assertEquals(0.0, matrix.get(0, 1), 0.0);
        assertEquals(0.0, matrix.get(1, 0), 0.0);
    }

    @Test
    void distancesAreSymmetricAndNonNegative() {
        DistanceMatrix matrix = DistanceCalculator.compute(FastaReader.parse(
                ">a\nACGTACGTAA\n>b\nACGTACGTAC\n>c\nTTGTTCGTAG\n"));
        for (int i = 0; i < matrix.size(); i++) {
            for (int j = 0; j < matrix.size(); j++) {
                assertTrue(matrix.get(i, j) >= 0.0);
                assertEquals(matrix.get(i, j), matrix.get(j, i), 0.0);
            }
        }
        assertEquals(0.1, matrix.get(0, 1), 1e-12);
    }

    @Test
    void gapsArePairwiseDeletedAndAmbiguityCodesIntersect() {
        // 5 sites: site0 gap for a (skipped); site1 R vs A intersect (match);
        // site2 R vs A intersect (match); site3 N for a (skipped); site4 N vs G
        // is skipped. Two compatible ambiguity sites -> distance 0.
        DistanceMatrix matrix = DistanceCalculator.compute(FastaReader.parse(
                ">a\n-ARAN\n>b\nAAAAG\n"));
        assertEquals(0.0, matrix.get(0, 1), 1e-12);
    }

    @Test
    void disjointAmbiguityCodesCountAsMismatch() {
        // 2 known sites both compared: R={A,G} vs Y={C,T} disjoint twice -> 1.0
        DistanceMatrix matrix = DistanceCalculator.compute(FastaReader.parse(
                ">a\nRR\n>b\nYY\n"));
        assertEquals(1.0, matrix.get(0, 1), 1e-12);
    }

    @Test
    void rejectsPairsWithoutComparableSites() {
        AlignmentException error = assertThrows(AlignmentException.class,
                () -> DistanceCalculator.compute(FastaReader.parse(
                        ">a\n---\n>b\nNNN\n>c\nACG\n")));
        assertEquals(RejectReason.INSUFFICIENT_INFORMATION, error.getReason());
    }
}
