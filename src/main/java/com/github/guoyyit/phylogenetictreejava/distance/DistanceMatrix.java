package com.github.guoyyit.phylogenetictreejava.distance;

import java.util.Arrays;
import java.util.List;

/**
 * Immutable symmetric, non-negative pairwise distance matrix.
 */
public final class DistanceMatrix {

    private final List<String> names;
    private final double[][] distances;

    public DistanceMatrix(List<String> names, double[][] distances) {
        int n = names.size();
        if (distances.length != n) {
            throw new IllegalArgumentException("matrix row count does not match name count");
        }
        this.names = List.copyOf(names);
        this.distances = new double[n][n];
        for (int i = 0; i < n; i++) {
            if (distances[i].length != n) {
                throw new IllegalArgumentException("matrix must be square");
            }
            for (int j = 0; j < n; j++) {
                double value = distances[i][j];
                if (Double.isNaN(value) || value < 0.0) {
                    throw new IllegalArgumentException(
                            "distances must be finite and non-negative");
                }
                this.distances[i][j] = value;
            }
        }
    }

    public int size() {
        return names.size();
    }

    public List<String> names() {
        return names;
    }

    public String name(int index) {
        return names.get(index);
    }

    public double get(int i, int j) {
        return distances[i][j];
    }

    double[][] rawCopy() {
        double[][] copy = new double[distances.length][];
        for (int i = 0; i < distances.length; i++) {
            copy[i] = Arrays.copyOf(distances[i], distances[i].length);
        }
        return copy;
    }
}
