package com.github.guoyyit.phylogenetictreejava;

import com.github.guoyyit.phylogenetictreejava.distance.DistanceCalculator;
import com.github.guoyyit.phylogenetictreejava.io.FastaReader;
import com.github.guoyyit.phylogenetictreejava.model.DistanceMatrix;
import com.github.guoyyit.phylogenetictreejava.model.TreeNode;
import com.github.guoyyit.phylogenetictreejava.tree.NeighborJoining;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeighborJoiningTest {

    private static final double TOLERANCE = 1e-6;

    @Test
    void exactlyReconstructsAdditiveTreeOfFourLeaves() {
        // Tree: u=(a:0.1,b:0.2); v=(c:0.3,d:0.4); edge u-v = 0.15
        String[] names = {"a", "b", "c", "d"};
        double[][] d = {
                {0.00, 0.30, 0.55, 0.65},
                {0.30, 0.00, 0.65, 0.75},
                {0.55, 0.65, 0.00, 0.70},
                {0.65, 0.75, 0.70, 0.00}
        };
        TreeNode tree = NeighborJoining.build(new DistanceMatrix(List.of(names), d));
        assertAllBranchesNonNegative(tree);
        Map<String, Map<String, Double>> paths = pathLengths(tree);
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                assertEquals(d[i][j],
                        paths.get(names[i]).get(names[j]), TOLERANCE,
                        "pair " + names[i] + "-" + names[j]);
            }
        }
    }

    @Test
    void exactlyReconstructsLargerAdditiveTree() {
        String[] names = {"a", "b", "c", "d", "e"};
        double[][] d = {
                {0.0, 0.4, 0.7, 0.7, 0.9},
                {0.4, 0.0, 0.7, 0.7, 0.9},
                {0.7, 0.7, 0.0, 0.6, 0.8},
                {0.7, 0.7, 0.6, 0.0, 0.8},
                {0.9, 0.9, 0.8, 0.8, 0.0}
        };
        TreeNode tree = NeighborJoining.build(new DistanceMatrix(List.of(names), d));
        assertAllBranchesNonNegative(tree);
        Map<String, Map<String, Double>> paths = pathLengths(tree);
        for (int i = 0; i < 5; i++) {
            for (int j = i + 1; j < 5; j++) {
                assertEquals(d[i][j],
                        paths.get(names[i]).get(names[j]), TOLERANCE);
            }
        }
    }

    @Test
    void allZeroDistancesProduceValidDeterministicTree() {
        int n = 5;
        double[][] d = new double[n][n];
        TreeNode first = NeighborJoining.build(
                new DistanceMatrix(List.of("a", "b", "c", "d", "e"), d));
        TreeNode second = NeighborJoining.build(
                new DistanceMatrix(List.of("a", "b", "c", "d", "e"),
                        new double[n][n]));
        assertAllBranchesNonNegative(first);
        assertEquals(5, countLeaves(first));
        assertEquals(com.github.guoyyit.phylogenetictreejava.io.NewickWriter.write(first),
                com.github.guoyyit.phylogenetictreejava.io.NewickWriter.write(second));
    }

    @Test
    void empiricalDistancesGiveNonNegativeTree() {
        DistanceMatrix matrix = DistanceCalculator.compute(FastaReader.parse("""
                >a
                ACGTACGTAAACGT
                >b
                ACGTACGTAAACGG
                >c
                ACGTTCGTAAATGG
                >d
                TCGTTCGGAATTGG
                """));
        TreeNode tree = NeighborJoining.build(matrix);
        assertAllBranchesNonNegative(tree);
        assertEquals(4, countLeaves(tree));
    }

    private static void assertAllBranchesNonNegative(TreeNode node) {
        for (TreeNode child : node.getChildren()) {
            assertTrue(child.getBranchLength() >= 0.0,
                    "negative branch length " + child.getBranchLength());
            assertAllBranchesNonNegative(child);
        }
    }

    private static int countLeaves(TreeNode node) {
        if (node.isLeaf()) {
            return 1;
        }
        return node.getChildren().stream().mapToInt(NeighborJoiningTest::countLeaves).sum();
    }

    static Map<String, Map<String, Double>> pathLengths(TreeNode root) {
        Map<String, Double> depth = new HashMap<>();
        depth.put(root.getLabel(), 0.0);
        collectDepths(root, depth);
        Map<String, String> parent = new HashMap<>();
        collectParents(root, parent);
        Map<String, Map<String, Double>> result = new HashMap<>();
        for (String leaf1 : depth.keySet()) {
            if (leaf1.equals(root.getLabel())) {
                continue;
            }
            for (String leaf2 : depth.keySet()) {
                if (leaf2.equals(root.getLabel())) {
                    continue;
                }
                String lca = lca(leaf1, leaf2, parent, root.getLabel());
                double length = depth.get(leaf1) + depth.get(leaf2) - 2 * depth.get(lca);
                result.computeIfAbsent(leaf1, k -> new HashMap<>()).put(leaf2, length);
            }
        }
        return result;
    }

    private static void collectDepths(TreeNode node, Map<String, Double> depth) {
        for (TreeNode child : node.getChildren()) {
            depth.put(child.getLabel(),
                    depth.get(node.getLabel()) + child.getBranchLength());
            if (!child.isLeaf()) {
                collectDepths(child, depth);
            }
        }
    }

    private static void collectParents(TreeNode node, Map<String, String> parent) {
        for (TreeNode child : node.getChildren()) {
            parent.put(child.getLabel(), node.getLabel());
            if (!child.isLeaf()) {
                collectParents(child, parent);
            }
        }
    }

    private static String lca(String a, String b, Map<String, String> parent, String root) {
        List<String> chainA = new ArrayList<>();
        String cur = a;
        while (true) {
            chainA.add(cur);
            if (cur.equals(root)) {
                break;
            }
            cur = parent.get(cur);
        }
        cur = b;
        while (true) {
            if (chainA.contains(cur)) {
                return cur;
            }
            cur = parent.get(cur);
        }
    }
}
