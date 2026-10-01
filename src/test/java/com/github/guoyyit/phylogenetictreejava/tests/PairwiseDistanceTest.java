package com.github.guoyyit.phylogenetictreejava.tests;

import com.github.guoyyit.phylogenetictreejava.distance.DistanceMatrix;
import com.github.guoyyit.phylogenetictreejava.distance.PairwiseDistanceCalculator;
import com.github.guoyyit.phylogenetictreejava.io.AlignmentValidator;
import com.github.guoyyit.phylogenetictreejava.model.Alignment;
import com.github.guoyyit.phylogenetictreejava.model.AlignmentInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PairwiseDistanceTest {

    private final AlignmentValidator validator = new AlignmentValidator();
    private final PairwiseDistanceCalculator calculator =
            new PairwiseDistanceCalculator();

    @Test
    void identicalSequencesHaveZeroDistanceAndMatrixIsSymmetric() {
        Alignment alignment = validator.validate(List.of(
                new AlignmentInput("a", "ACGTACGT"),
                new AlignmentInput("b", "ACGTACGT"),
                new AlignmentInput("c", "ACGTACGT")));

        DistanceMatrix matrix = calculator.calculate(alignment);
        for (int i = 0; i < matrix.size(); i++) {
            for (int j = 0; j < matrix.size(); j++) {
                assertEquals(matrix.get(j, i), matrix.get(i, j), 0.0);
                assertTrue(matrix.get(i, j) >= 0.0);
                if (i == j) {
                    assertEquals(0.0, matrix.get(i, j), 0.0);
                }
            }
        }
    }

    @Test
    void gapsAndMissingAreExcludedPairwise() {
        Alignment alignment = validator.validate(List.of(
                new AlignmentInput("a", "AC-GT"),
                new AlignmentInput("b", "ACAGT")));

        DistanceMatrix matrix = calculator.calculate(alignment);
        assertEquals(0.0, matrix.get(0, 1), 1e-12);
    }

    @Test
    void ambiguityIntersectionCountsAsMatchAndDisjointAsMismatch() {
        Alignment intersection = validator.validate(List.of(
                new AlignmentInput("a", "R"),
                new AlignmentInput("b", "A")));
        assertEquals(0.0, calculator.calculate(intersection).get(0, 1), 1e-12);

        Alignment disjoint = validator.validate(List.of(
                new AlignmentInput("a", "R"),
                new AlignmentInput("b", "C")));
        assertEquals(1.0, calculator.calculate(disjoint).get(0, 1), 1e-12);

        Alignment uracil = validator.validate(List.of(
                new AlignmentInput("a", "ACGU"),
                new AlignmentInput("b", "ACGT")));
        assertEquals(0.0, calculator.calculate(uracil).get(0, 1), 1e-12);
    }

    @Test
    void distanceIsMismatchesOverComparableSites() {
        Alignment alignment = validator.validate(List.of(
                new AlignmentInput("a", "ACGT-A"),
                new AlignmentInput("b", "ACAT-A")));
        DistanceMatrix matrix = calculator.calculate(alignment);
        assertEquals(1.0 / 5.0, matrix.get(0, 1), 1e-12);
    }
}
