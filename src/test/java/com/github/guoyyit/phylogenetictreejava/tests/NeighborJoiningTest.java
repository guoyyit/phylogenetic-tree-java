package com.github.guoyyit.phylogenetictreejava.tests;

import com.github.guoyyit.phylogenetictreejava.distance.DistanceMatrix;
import com.github.guoyyit.phylogenetictreejava.io.NewickCodec;
import com.github.guoyyit.phylogenetictreejava.tree.NeighborJoiningTreeBuilder;
import com.github.guoyyit.phylogenetictreejava.tree.PhyloTree;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeighborJoiningTest {

    private static final double TOLERANCE = 1e-9;

    private final NeighborJoiningTreeBuilder builder =
            new NeighborJoiningTreeBuilder();

    @Test
    void reconstructsAdditiveDistanceMatrix() {
        List<String> names = List.of("A", "B", "C", "D", "E");
        double[][] additive = {
                {0.00, 0.50, 0.90, 0.90, 0.70},
                {0.50, 0.00, 0.90, 0.90, 0.70},
                {0.90, 0.90, 0.00, 0.40, 0.80},
                {0.90, 0.90, 0.40, 0.00, 0.80},
                {0.70, 0.70, 0.80, 0.80, 0.00}
        };
        DistanceMatrix matrix = new DistanceMatrix(names, additive);

        PhyloTree tree = builder.build(matrix);
        Map<String, Map<String, Double>> pathLengths = pathLengths(tree);

        for (int i = 0; i < names.size(); i++) {
            for (int j = 0; j < names.size(); j++) {
                int row = i;
                int column = j;
                assertEquals(additive[i][j],
                        pathLengths.get(names.get(i)).get(names.get(j)),
                        1e-9,
                        () -> "path " + names.get(row) + "-" + names.get(column));
            }
        }
    }

    @Test
    void allZeroDistancesYieldDeterministicValidTree() {
        List<String> names = List.of("A", "B", "C", "D");
        double[][] zeros = new double[4][4];

        String first = new NewickCodec().write(builder.build(
                new DistanceMatrix(names, zeros)));
        String second = new NewickCodec().write(builder.build(
                new DistanceMatrix(names, zeros)));
        assertEquals(first, second);

        Map<String, Map<String, Double>> paths =
                pathLengths(builder.build(new DistanceMatrix(names, zeros)));
        for (String from : names) {
            for (String to : names) {
                assertEquals(0.0, paths.get(from).get(to), TOLERANCE);
            }
        }
    }

    @Test
    void allBranchLengthsAreNonNegativeAndTieBreaksAreStable() {
        List<String> names = List.of("A", "B", "C", "D", "E", "F");
        double[][] d = new double[6][6];
        for (int i = 0; i < 6; i++) {
            Arrays.fill(d[i], 1.0);
            d[i][i] = 0.0;
        }
        PhyloTree first = builder.build(new DistanceMatrix(names, d));
        PhyloTree second = builder.build(new DistanceMatrix(names, d));
        assertEquals(new NewickCodec().write(first),
                new NewickCodec().write(second));
        assertNonNegative(first.getRoot());
    }

    private static void assertNonNegative(PhyloTree.Node node) {
        assertTrue(node.getLength() >= -0.0);
        for (PhyloTree.Node child : node.getChildren()) {
            assertNonNegative(child);
        }
    }

    private static Map<String, Map<String, Double>> pathLengths(PhyloTree tree) {
        List<String> leaves = new ArrayList<>();
        collectLeaves(tree.getRoot(), leaves);
        Map<String, Map<String, Double>> result = new HashMap<>();
        for (String leaf : leaves) {
            result.put(leaf, new HashMap<>());
        }
        for (int i = 0; i < leaves.size(); i++) {
            for (int j = i; j < leaves.size(); j++) {
                double length = distanceBetween(tree.getRoot(),
                        leaves.get(i), leaves.get(j));
                result.get(leaves.get(i)).put(leaves.get(j), length);
                result.get(leaves.get(j)).put(leaves.get(i), length);
            }
        }
        return result;
    }

    private static void collectLeaves(PhyloTree.Node node, List<String> leaves) {
        if (node.isLeaf()) {
            leaves.add(node.getLabel());
            return;
        }
        for (PhyloTree.Node child : node.getChildren()) {
            collectLeaves(child, leaves);
        }
    }

    private static double distanceBetween(PhyloTree.Node root, String first,
                                          String second) {
        if (first.equals(second)) {
            return 0.0;
        }
        PhyloTree.Node lca = lowestCommonAncestor(root, first, second);
        return depth(lca, first) + depth(lca, second);
    }

    private static PhyloTree.Node lowestCommonAncestor(PhyloTree.Node node,
                                                       String first,
                                                       String second) {
        if (node.isLeaf()) {
            return null;
        }
        int matchingChildren = 0;
        for (PhyloTree.Node child : node.getChildren()) {
            if (contains(child, first) && contains(child, second)) {
                return lowestCommonAncestor(child, first, second);
            }
            if (contains(child, first) || contains(child, second)) {
                matchingChildren++;
            }
        }
        return matchingChildren >= 2 ? node : node;
    }

    private static boolean contains(PhyloTree.Node node, String leaf) {
        if (node.isLeaf()) {
            return node.getLabel().equals(leaf);
        }
        for (PhyloTree.Node child : node.getChildren()) {
            if (contains(child, leaf)) {
                return true;
            }
        }
        return false;
    }

    private static double depth(PhyloTree.Node node, String leaf) {
        for (PhyloTree.Node child : node.getChildren()) {
            if (contains(child, leaf)) {
                return child.getLength() + depth(child, leaf);
            }
        }
        return 0.0;
    }
}
