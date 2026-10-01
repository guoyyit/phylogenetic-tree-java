package com.github.guoyyit.phylogenetictreejava.model;

/**
 * A validated sequence with its stable index inside an {@link Alignment}.
 */
public final class Sequence {

    private final int index;
    private final String name;
    private final String residues;

    public Sequence(int index, String name, String residues) {
        this.index = index;
        this.name = name;
        this.residues = residues;
    }

    public int index() {
        return index;
    }

    public String name() {
        return name;
    }

    public String residues() {
        return residues;
    }
}
