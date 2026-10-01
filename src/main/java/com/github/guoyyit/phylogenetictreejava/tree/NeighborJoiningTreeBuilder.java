package com.github.guoyyit.phylogenetictreejava.tree;

import com.github.guoyyit.phylogenetictreejava.distance.DistanceMatrix;

/**
 * Deterministic neighbor-joining reconstruction.
 *
 * <p>Tie-breaking is fixed (smallest matrix indices), branch lengths are
 * clamped at zero, and an all-zero distance matrix yields a deterministic
 * valid tree whose leaf path lengths are all zero.</p>
 */
public final class NeighborJoiningTreeBuilder {

    public NeighborJoiningTreeBuilder() {
    }

    public PhyloTree build(DistanceMatrix matrix) {
        int size = matrix.size();
        if (size < 2) {
            throw new IllegalArgumentException(
                    "neighbor joining requires at least two taxa");
        }

        PhyloTree.Node[] nodes = new PhyloTree.Node[size];
        for (int i = 0; i < size; i++) {
            nodes[i] = new PhyloTree.Node(matrix.name(i), 0.0);
        }

        double[][] d = new double[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                d[i][j] = matrix.get(i, j);
            }
        }

        boolean[] active = new boolean[size];
        java.util.Arrays.fill(active, true);
        int activeCount = size;

        while (activeCount > 3) {
            double[] rowSums = rowSums(d, active, size);
            int[] pair = selectPair(d, rowSums, active, size, activeCount);
            int a = pair[0];
            int b = pair[1];

            double da = 0.5 * d[a][b]
                    + (rowSums[a] - rowSums[b]) / (2.0 * (activeCount - 2));
            double db = d[a][b] - da;
            PhyloTree.Node joined = new PhyloTree.Node(null, 0.0);
            joined.getChildren().add(withLength(nodes[a], Math.max(0.0, da)));
            joined.getChildren().add(withLength(nodes[b], Math.max(0.0, db)));
            nodes[a] = joined;
            nodes[b] = null;

            for (int k = 0; k < size; k++) {
                if (active[k] && k != a && k != b) {
                    double updated = 0.5 * (d[a][k] + d[b][k] - d[a][b]);
                    d[a][k] = Math.max(0.0, updated);
                    d[k][a] = d[a][k];
                }
            }
            active[b] = false;
            activeCount--;
        }

        int[] survivors = new int[activeCount];
        int write = 0;
        for (int i = 0; i < size; i++) {
            if (active[i]) {
                survivors[write++] = i;
            }
        }
        return new PhyloTree(finalizeRoot(d, nodes, survivors));
    }

    private static PhyloTree.Node withLength(PhyloTree.Node node, double length) {
        node.setLength(length);
        return node;
    }

    private static double[] rowSums(double[][] d, boolean[] active, int size) {
        double[] sums = new double[size];
        for (int i = 0; i < size; i++) {
            if (!active[i]) {
                continue;
            }
            double total = 0.0;
            for (int j = 0; j < size; j++) {
                if (active[j]) {
                    total += d[i][j];
                }
            }
            sums[i] = total;
        }
        return sums;
    }

    private static int[] selectPair(double[][] d, double[] rowSums, boolean[] active,
                                    int size, int activeCount) {
        double best = Double.POSITIVE_INFINITY;
        int bestA = -1;
        int bestB = -1;
        for (int i = 0; i < size; i++) {
            if (!active[i]) {
                continue;
            }
            for (int j = i + 1; j < size; j++) {
                if (!active[j]) {
                    continue;
                }
                double q = (activeCount - 2) * d[i][j] - rowSums[i] - rowSums[j];
                if (q < best) {
                    best = q;
                    bestA = i;
                    bestB = j;
                }
            }
        }
        return new int[]{bestA, bestB};
    }

    private static PhyloTree.Node finalizeRoot(double[][] d, PhyloTree.Node[] nodes,
                                               int[] survivors) {
        if (survivors.length == 2) {
            int a = survivors[0];
            int b = survivors[1];
            PhyloTree.Node root = new PhyloTree.Node(null, 0.0);
            root.getChildren().add(withLength(nodes[a], 0.5 * d[a][b]));
            root.getChildren().add(withLength(nodes[b], 0.5 * d[a][b]));
            return root;
        }

        int a = survivors[0];
        int b = survivors[1];
        int c = survivors[2];
        double da = 0.5 * (d[a][b] + d[a][c] - d[b][c]);
        double db = 0.5 * (d[a][b] + d[b][c] - d[a][c]);
        double dc = 0.5 * (d[a][c] + d[b][c] - d[a][b]);
        PhyloTree.Node root = new PhyloTree.Node(null, 0.0);
        root.getChildren().add(withLength(nodes[a], Math.max(0.0, da)));
        root.getChildren().add(withLength(nodes[b], Math.max(0.0, db)));
        root.getChildren().add(withLength(nodes[c], Math.max(0.0, dc)));
        return root;
    }
}
