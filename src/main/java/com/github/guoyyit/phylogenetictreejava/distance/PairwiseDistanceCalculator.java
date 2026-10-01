package com.github.guoyyit.phylogenetictreejava.distance;

import com.github.guoyyit.phylogenetictreejava.model.Alignment;

import java.util.EnumSet;
import java.util.Collections;
import java.util.List;

/**
 * Computes p-distances (mismatches / comparable sites, pairwise deletion).
 */
public final class PairwiseDistanceCalculator {

    public PairwiseDistanceCalculator() {
    }

    public DistanceMatrix calculate(Alignment alignment) {
        int n = alignment.size();
        int sites = alignment.length();
        double[][] distances = new double[n][n];

        for (int i = 0; i < n; i++) {
            String first = alignment.get(i).residues();
            for (int j = i + 1; j < n; j++) {
                String second = alignment.get(j).residues();
                int comparable = 0;
                int mismatches = 0;
                for (int site = 0; site < sites; site++) {
                    char a = AmbiguityRules.normalize(first.charAt(site));
                    char b = AmbiguityRules.normalize(second.charAt(site));
                    if (AmbiguityRules.isGap(a) || AmbiguityRules.isGap(b)) {
                        continue;
                    }
                    comparable++;
                    EnumSet<AmbiguityRules.Base> optionsA = AmbiguityRules.possibilities(a);
                    EnumSet<AmbiguityRules.Base> optionsB = AmbiguityRules.possibilities(b);
                    if (Collections.disjoint(optionsA, optionsB)) {
                        mismatches++;
                    }
                }
                if (comparable == 0) {
                    throw new IllegalStateException(
                            "no comparable sites between sequences " + i + " and " + j);
                }
                double distance = (double) mismatches / comparable;
                distances[i][j] = distance;
                distances[j][i] = distance;
            }
        }

        List<String> names = alignment.sequences().stream()
                .map(sequence -> sequence.name())
                .toList();
        return new DistanceMatrix(names, distances);
    }
}
