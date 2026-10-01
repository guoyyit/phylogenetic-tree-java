package com.github.guoyyit.phylogenetictreejava.model;

import java.util.List;

/**
 * Symmetric, non-negative pairwise distance matrix indexed by sequence name order.
 */
public final class DistanceMatrix {

    private final List<String> names;
    private final double[][] values;

    public DistanceMatrix(List<String> names, double[][] values) {
        this.names = List.copyOf(names);
        this.values = values;
    }

    public int size() {
        return names.size();
    }

    public List<String> names() {
        return names;
    }

    public double get(int i, int j) {
        return values[i][j];
    }
}
