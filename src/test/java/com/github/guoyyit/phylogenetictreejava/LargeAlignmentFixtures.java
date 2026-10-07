package com.github.guoyyit.phylogenetictreejava;

import java.util.Random;

/**
 * Deterministic large-alignment generators shared by the scale tests.
 */
final class LargeAlignmentFixtures {

    private LargeAlignmentFixtures() {
    }

    /**
     * {@code sequences} equal-length sequences of {@code length} columns. Every
     * site is an unambiguous base (no gaps/unknowns), so any pair shares many
     * comparable sites and every resampled replicate stays analyzable.
     */
    static String randomDnaFasta(int sequences, int length, long seed) {
        Random random = new Random(seed);
        char[] bases = {'A', 'C', 'G', 'T'};
        StringBuilder out = new StringBuilder(sequences * (length + 16));
        for (int i = 0; i < sequences; i++) {
            out.append(">s").append(i).append('\n');
            for (int k = 0; k < length; k++) {
                out.append(bases[random.nextInt(bases.length)]);
            }
            out.append('\n');
        }
        return out.toString();
    }

    /** {@code sequences} identical sequences of {@code length}; all distances are zero. */
    static String identicalDnaFasta(int sequences, int length) {
        String row = "ACGT".repeat((length + 3) / 4).substring(0, length);
        StringBuilder out = new StringBuilder(sequences * (length + 16));
        for (int i = 0; i < sequences; i++) {
            out.append(">s").append(i).append('\n').append(row).append('\n');
        }
        return out.toString();
    }
}
