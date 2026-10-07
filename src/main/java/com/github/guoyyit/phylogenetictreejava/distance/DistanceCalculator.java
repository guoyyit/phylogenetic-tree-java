package com.github.guoyyit.phylogenetictreejava.distance;

import com.github.guoyyit.phylogenetictreejava.model.Alignment;
import com.github.guoyyit.phylogenetictreejava.model.DistanceMatrix;
import com.github.guoyyit.phylogenetictreejava.exception.AlignmentException;
import com.github.guoyyit.phylogenetictreejava.exception.RejectReason;
import com.github.guoyyit.phylogenetictreejava.model.Sequence;

import java.util.List;

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
     *
     * <p>Each residue is encoded as a bit set over {A,C,G,T}; set intersection is
     * therefore a bitwise {@code &} and yields exactly the same results as the
     * documented set-based rule.
     */
    private static final byte A = 1;
    private static final byte C = 1 << 1;
    private static final byte G = 1 << 2;
    private static final byte T = 1 << 3;

    private static final byte[] RESIDUE_CODES;

    static {
        byte[] table = new byte[128];
        table['A'] = A;
        table['C'] = C;
        table['G'] = G;
        table['T'] = T;
        table['R'] = A | G;
        table['Y'] = C | T;
        table['S'] = G | C;
        table['W'] = A | T;
        table['K'] = G | T;
        table['M'] = A | C;
        table['B'] = C | G | T;
        table['D'] = A | G | T;
        table['H'] = A | C | T;
        table['V'] = A | C | G;
        // Gap/unknown characters map to 0 and are excluded per pair.
        RESIDUE_CODES = table;
    }

    private DistanceCalculator() {
    }

    /**
     * Strict distance computation used for the reference tree and for submit-time
     * validation: any pair without a single comparable site rejects the alignment
     * with {@link RejectReason#INSUFFICIENT_INFORMATION}.
     */
    public static DistanceMatrix compute(Alignment alignment) {
        return compute(alignment, false);
    }

    /**
     * Lenient distance computation used only for column-resampled bootstrap
     * replicates. Resampling can, by chance, leave a pair with no comparable site
     * even though the original alignment is valid; such a pair is given distance
     * 0.0 so replicate results stay a pure, deterministic function of (seed, index).
     */
    public static DistanceMatrix computeLenient(Alignment alignment) {
        return compute(alignment, true);
    }

    /**
     * Submit-time validation: every pair of sequences must share at least one
     * comparable site (neither residue a gap nor fully unknown at that site).
     */
    public static void validatePairwiseInformation(Alignment alignment) {
        compute(alignment, false);
    }

    private static DistanceMatrix compute(Alignment alignment, boolean lenientPairs) {
        List<Sequence> sequences = alignment.sequences();
        int n = sequences.size();
        int length = alignment.length();
        byte[][] codes = new byte[n][];
        for (int i = 0; i < n; i++) {
            String data = sequences.get(i).data();
            byte[] encoded = new byte[length];
            for (int k = 0; k < length; k++) {
                encoded[k] = residueCode(data.charAt(k));
            }
            codes[i] = encoded;
        }

        double[][] values = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                int compared = 0;
                int mismatches = 0;
                byte[] rowI = codes[i];
                byte[] rowJ = codes[j];
                for (int k = 0; k < length; k++) {
                    byte codeI = rowI[k];
                    byte codeJ = rowJ[k];
                    if (codeI == 0 || codeJ == 0) {
                        continue;
                    }
                    compared++;
                    if ((codeI & codeJ) == 0) {
                        mismatches++;
                    }
                }
                if (compared == 0) {
                    if (lenientPairs) {
                        continue;
                    }
                    throw new AlignmentException(RejectReason.INSUFFICIENT_INFORMATION,
                            "no comparable sites between '" + sequences.get(i).name()
                                    + "' and '" + sequences.get(j).name()
                                    + "' (all sites are gaps or unknown)");
                }
                double distance = (double) mismatches / compared;
                values[i][j] = distance;
                values[j][i] = distance;
            }
        }
        return new DistanceMatrix(sequences.stream().map(Sequence::name).toList(), values);
    }

    private static byte residueCode(char residue) {
        return residue < RESIDUE_CODES.length ? RESIDUE_CODES[residue] : 0;
    }
}
