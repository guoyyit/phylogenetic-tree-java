package com.github.guoyyit.phylogenetictreejava.model;

/**
 * One aligned sequence: a name plus its residue string (already aligned).
 */
public record Sequence(String name, String data) {
}
