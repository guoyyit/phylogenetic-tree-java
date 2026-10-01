package com.github.guoyyit.phylogenetictreejava.model;

import java.util.List;

/**
 * A validated multiple sequence alignment: equal-length, non-empty residues.
 */
public final class Alignment {

    private final List<Sequence> sequences;
    private final int length;

    public Alignment(List<Sequence> sequences) {
        this.sequences = List.copyOf(sequences);
        this.length = sequences.isEmpty() ? 0 : sequences.get(0).residues().length();
    }

    public List<Sequence> sequences() {
        return sequences;
    }

    public int size() {
        return sequences.size();
    }

    public int length() {
        return length;
    }

    public Sequence get(int index) {
        return sequences.get(index);
    }

    public char residueAt(int sequenceIndex, int siteIndex) {
        return sequences.get(sequenceIndex).residues().charAt(siteIndex);
    }
}
