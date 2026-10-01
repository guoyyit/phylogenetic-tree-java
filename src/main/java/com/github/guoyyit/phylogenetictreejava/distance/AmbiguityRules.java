package com.github.guoyyit.phylogenetictreejava.distance;

import java.util.EnumSet;

/**
 * Documented handling of gap and ambiguity IUPAC nucleotide codes.
 *
 * <ul>
 *   <li>Gap characters ({@code - . ~}) and missing ({@code ?}) never match
 *       any concrete base; sites where either side is gap/missing are
 *       excluded for that pair (pairwise deletion).</li>
 *   <li>Ambiguity codes ({@code R,Y,S,W,K,M,B,D,H,V}, plus {@code X}) expand
 *       to the set of possible nucleotides; two codes are a match when their
 *       sets intersect and a mismatch only when they are disjoint.</li>
 *   <li>{@code U} is accepted as equivalent to {@code T}.</li>
 * </ul>
 */
public final class AmbiguityRules {

    public enum Base {
        A, C, G, T
    }

    public AmbiguityRules() {
    }

    public static boolean isGap(char residue) {
        return residue == '-' || residue == '.' || residue == '~' || residue == '?';
    }

    public static boolean isKnown(char residue) {
        if (isGap(residue)) {
            return false;
        }
        return switch (residue) {
            case 'A', 'C', 'G', 'T',
                    'R', 'Y', 'S', 'W', 'K', 'M',
                    'B', 'D', 'H', 'V', 'N', 'X' -> true;
            default -> false;
        };
    }

    public static EnumSet<Base> possibilities(char residue) {
        return switch (residue) {
            case 'A' -> EnumSet.of(Base.A);
            case 'C' -> EnumSet.of(Base.C);
            case 'G' -> EnumSet.of(Base.G);
            case 'T' -> EnumSet.of(Base.T);
            case 'R' -> EnumSet.of(Base.A, Base.G);
            case 'Y' -> EnumSet.of(Base.C, Base.T);
            case 'S' -> EnumSet.of(Base.C, Base.G);
            case 'W' -> EnumSet.of(Base.A, Base.T);
            case 'K' -> EnumSet.of(Base.G, Base.T);
            case 'M' -> EnumSet.of(Base.A, Base.C);
            case 'B' -> EnumSet.of(Base.C, Base.G, Base.T);
            case 'D' -> EnumSet.of(Base.A, Base.G, Base.T);
            case 'H' -> EnumSet.of(Base.A, Base.C, Base.T);
            case 'V' -> EnumSet.of(Base.A, Base.C, Base.G);
            case 'N', 'X' -> EnumSet.allOf(Base.class);
            default -> EnumSet.noneOf(Base.class);
        };
    }

    public static char normalize(char residue) {
        return residue == 'U' ? 'T' : residue;
    }
}
