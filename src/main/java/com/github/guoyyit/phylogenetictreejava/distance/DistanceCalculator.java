package com.github.guoyyit.phylogenetictreejava.distance;

import com.github.guoyyit.phylogenetictreejava.model.Alignment;
import com.github.guoyyit.phylogenetictreejava.model.DistanceMatrix;
import com.github.guoyyit.phylogenetictreejava.exception.AlignmentException;
import com.github.guoyyit.phylogenetictreejava.exception.RejectReason;
import com.github.guoyyit.phylogenetictreejava.model.Sequence;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes pairwise evolutionary distances from an alignment.
 */
public final class DistanceCalculator {

    /**
     * Comparison rules (documented in docs/):
     *
     * <ul>
     *   <li>A site is compared for a pair only when neither residue is a gap
     *       ({@code -}, {@code .}) nor fully unknown ({@code N}, {@code X}, {@code ?}).</li>
     *   <li>IUPAC ambiguity codes denote sets of nucleotides; the site is a match when
     *       the sets intersect (compatible), a mismatch when disjoint.</li>
     *   <li>Distance = mismatches / compared sites (pairwise-deleted p-distance).
     *   When a pair shares no comparable site the alignment is rejected.</li>
     * </ul>
     */
    private static final Map<Character, Set<Character>> NUCLEOTIDE_SETS = Map.ofEntries(
            Map.entry('A', Set.of('A')),
            Map.entry('C', Set.of('C')),
            Map.entry('G', Set.of('G')),
            Map.entry('T', Set.of('T')),
            Map.entry('R', Set.of('A', 'G')),
            Map.entry('Y', Set.of('C', 'T')),
            Map.entry('S', Set.of('G', 'C')),
            Map.entry('W', Set.of('A', 'T')),
            Map.entry('K', Set.of('G', 'T')),
            Map.entry('M', Set.of('A', 'C')),
            Map.entry('B', Set.of('C', 'G', 'T')),
            Map.entry('D', Set.of('A', 'G', 'T')),
            Map.entry('H', Set.of('A', 'C', 'T')),
            Map.entry('V', Set.of('A', 'C', 'G')));

    private DistanceCalculator() {
    }

    public static DistanceMatrix compute(Alignment alignment) {
        List<Sequence> sequences = alignment.sequences();
        int n = sequences.size();
        double[][] values = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double distance = pairDistance(sequences.get(i).data(),
                        sequences.get(j).data(), sequences.get(i).name(),
                        sequences.get(j).name());
                values[i][j] = distance;
                values[j][i] = distance;
            }
        }
        return new DistanceMatrix(sequences.stream().map(Sequence::name).toList(), values);
    }

    private static double pairDistance(String a, String b, String nameA, String nameB) {
        int compared = 0;
        int mismatches = 0;
        for (int k = 0; k < a.length(); k++) {
            Set<Character> setA = NUCLEOTIDE_SETS.get(a.charAt(k));
            Set<Character> setB = NUCLEOTIDE_SETS.get(b.charAt(k));
            if (setA == null || setB == null) {
                continue;
            }
            compared++;
            if (setA.stream().noneMatch(setB::contains)) {
                mismatches++;
            }
        }
        if (compared == 0) {
            throw new AlignmentException(RejectReason.INSUFFICIENT_INFORMATION,
                    "no comparable sites between '" + nameA + "' and '" + nameB
                            + "' (all sites are gaps or unknown)");
        }
        return (double) mismatches / compared;
    }
}
