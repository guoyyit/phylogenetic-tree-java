package com.github.guoyyit.phylogenetictreejava.model;

/**
 * One raw named sequence, either read from a FASTA file or submitted in-process.
 */
public final class AlignmentInput {

    private final String name;
    private final String sequence;

    public AlignmentInput(String name, String sequence) {
        this.name = name;
        this.sequence = sequence;
    }

    public String name() {
        return name;
    }

    public String sequence() {
        return sequence;
    }
}
