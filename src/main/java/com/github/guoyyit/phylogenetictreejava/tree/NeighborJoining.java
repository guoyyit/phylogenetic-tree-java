package com.github.guoyyit.phylogenetictreejava.tree;

import com.github.guoyyit.phylogenetictreejava.model.DistanceMatrix;
import com.github.guoyyit.phylogenetictreejava.model.TreeNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a binary (phylogenetic) tree from a distance matrix using neighbor joining.
 */
public final class NeighborJoining {

    /** Branch lengths with magnitude below this tolerance are treated as zero. */
    public static final double LENGTH_EPS = 1e-9;

    private NeighborJoining() {
    }

    public static TreeNode build(DistanceMatrix matrix) {
        int n = matrix.size();
        if (n < 2) {
            TreeNode single = new TreeNode();
            if (n == 1) {
                single.setLabel(matrix.names().get(0));
            }
            return single;
        }

        List<TreeNode> nodes = new ArrayList<>();
        for (String name : matrix.names()) {
            TreeNode leaf = new TreeNode();
            leaf.setLabel(name);
            nodes.add(leaf);
        }

        double[][] d = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                d[i][j] = matrix.get(i, j);
            }
        }

        boolean[] active = new boolean[n];
        java.util.Arrays.fill(active, true);
        int activeCount = n;
        int nextInternalId = 1;

        while (activeCount > 2) {
            int[] pair = pickPair(d, active, activeCount);
            int a = pair[0];
            int b = pair[1];

            double lenA = (d[a][b] / 2.0)
                    + (sumRow(d[a], active) - sumRow(d[b], active))
                    / (2.0 * (activeCount - 2));
            double lenB = d[a][b] - lenA;
            lenA = Math.max(0.0, normalizeZero(lenA));
            lenB = Math.max(0.0, normalizeZero(lenB));

            TreeNode nodeA = nodes.get(a);
            TreeNode nodeB = nodes.get(b);
            nodeA.setBranchLength(lenA);
            nodeB.setBranchLength(lenB);

            TreeNode parent = new TreeNode();
            parent.setLabel("N" + nextInternalId++);
            parent.addChild(nodeA);
            parent.addChild(nodeB);

            active[a] = false;
            active[b] = false;

            int r = -1;
            for (int i = active.length - 1; i >= 0; i--) {
                if (!active[i]) {
                    r = i;
                    break;
                }
            }
            if (r == -1) {
                nodes.add(parent);
                d = grow(d);
                active = java.util.Arrays.copyOf(active, active.length + 1);
                r = active.length - 1;
                nodes.set(r, parent);
            } else {
                nodes.set(r, parent);
            }
            active[r] = true;

            for (int k = 0; k < d.length; k++) {
                if (!active[k] || k == a || k == b || k == r) {
                    continue;
                }
                double dist = (d[a][k] + d[b][k] - d[a][b]) / 2.0;
                d[r][k] = Math.max(0.0, normalizeZero(dist));
                d[k][r] = d[r][k];
            }
            d[r][r] = 0.0;

            activeCount--;
        }

        List<Integer> remaining = new ArrayList<>();
        for (int i = 0; i < active.length; i++) {
            if (active[i]) {
                remaining.add(i);
            }
        }
        int a = remaining.get(0);
        int b = remaining.get(1);
        double half = Math.max(0.0, d[a][b] / 2.0);
        nodes.get(a).setBranchLength(half);
        nodes.get(b).setBranchLength(half);

        TreeNode root = new TreeNode();
        root.setLabel("ROOT");
        root.addChild(nodes.get(a));
        root.addChild(nodes.get(b));
        return root;
    }

    private static int[] pickPair(double[][] d, boolean[] active, int activeCount) {
        int bestA = -1;
        int bestB = -1;
        double bestQ = Double.POSITIVE_INFINITY;
        double[] sums = new double[d.length];
        for (int i = 0; i < d.length; i++) {
            if (active[i]) {
                sums[i] = sumRow(d[i], active);
            }
        }
        for (int i = 0; i < d.length; i++) {
            if (!active[i]) {
                continue;
            }
            for (int j = i + 1; j < d.length; j++) {
                if (!active[j]) {
                    continue;
                }
                double q = (activeCount - 2) * d[i][j] - sums[i] - sums[j];
                if (q < bestQ
                        || (q == bestQ && (bestA == -1 || tieBreaks(i, j, bestA, bestB)))) {
                    bestQ = q;
                    bestA = i;
                    bestB = j;
                }
            }
        }
        return new int[]{bestA, bestB};
    }

    private static boolean tieBreaks(int i, int j, int bestA, int bestB) {
        if (i != bestA) {
            return i < bestA;
        }
        return j < bestB;
    }

    private static double sumRow(double[] row, boolean[] active) {
        double total = 0.0;
        for (int k = 0; k < row.length; k++) {
            if (active[k]) {
                total += row[k];
            }
        }
        return total;
    }

    private static double normalizeZero(double value) {
        return Math.abs(value) < LENGTH_EPS ? 0.0 : value;
    }

    private static double[][] grow(double[][] d) {
        int n = d.length + 1;
        double[][] grown = new double[n][n];
        for (int i = 0; i < d.length; i++) {
            System.arraycopy(d[i], 0, grown[i], 0, d.length);
        }
        return grown;
    }
}
