package com.github.guoyyit.phylogenetictreejava.model;

import java.util.List;

/**
 * A validated multiple sequence alignment: equal-length sequences.
 */
public final class Alignment {

    private final List<Sequence> sequences;
    private final int length;

    public Alignment(List<Sequence> sequences, int length) {
        this.sequences = List.copyOf(sequences);
        this.length = length;
    }

    public List<Sequence> sequences() {
        return sequences;
    }

    public int length() {
        return length;
    }

    public int size() {
        return sequences.size();
    }
}
