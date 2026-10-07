package com.github.guoyyit.phylogenetictreejava;

import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapConfig;
import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapEngine;
import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapResult;
import com.github.guoyyit.phylogenetictreejava.consensus.ConsensusBuilder;
import com.github.guoyyit.phylogenetictreejava.io.FastaReader;
import com.github.guoyyit.phylogenetictreejava.io.NewickParser;
import com.github.guoyyit.phylogenetictreejava.io.NewickWriter;
import com.github.guoyyit.phylogenetictreejava.model.Alignment;
import com.github.guoyyit.phylogenetictreejava.model.TreeNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Completion and support correctness for large (>=128 taxa, >=200 columns)
 * inputs and for the 62/63/64/65-taxon boundary around the old 63-taxon limit.
 */
class LargeScaleConsensusTest {

    static String dnaAlignment(int taxa, int length, long seed) {
        Random rng = new Random(seed);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < taxa; i++) {
            sb.append(">s").append(i).append('\n');
            for (int k = 0; k < length; k++) {
                sb.append("ACGT".charAt(rng.nextInt(4)));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static int countLeaves(TreeNode node) {
        if (node.isLeaf()) {
            return 1;
        }
        int total = 0;
        for (TreeNode child : node.getChildren()) {
            total += countLeaves(child);
        }
        return total;
    }

    private static int countInternal(TreeNode node) {
        if (node.isLeaf()) {
            return 0;
        }
        int total = 1;
        for (TreeNode child : node.getChildren()) {
            total += countInternal(child);
        }
        return total;
    }

    private static List<TreeNode> internalNodes(TreeNode node) {
        List<TreeNode> result = new ArrayList<>();
        collectInternal(node, result);
        return result;
    }

    private static void collectInternal(TreeNode node, List<TreeNode> out) {
        if (node.isLeaf()) {
            return;
        }
        out.add(node);
        for (TreeNode child : node.getChildren()) {
            collectInternal(child, out);
        }
    }

    private static List<String> leaves(TreeNode node) {
        List<String> names = new ArrayList<>();
        collectLeafNames(node, names);
        return names;
    }

    private static void collectLeafNames(TreeNode node, List<String> names) {
        if (node.isLeaf()) {
            names.add(node.getLabel());
            return;
        }
        for (TreeNode child : node.getChildren()) {
            collectLeafNames(child, names);
        }
    }

    private static Set<String> leafNameSet(TreeNode node) {
        Set<String> names = new HashSet<>();
        collectLeafNameSet(node, names);
        return names;
    }

    private static void collectLeafNameSet(TreeNode node, Set<String> names) {
        if (node.isLeaf()) {
            names.add(node.getLabel());
            return;
        }
        for (TreeNode child : node.getChildren()) {
            collectLeafNameSet(child, names);
        }
    }

    /**
     * Independently recomputes split frequencies directly from leaf-name sets
     * (no BitSet, no production helper) and compares every annotated support.
     * Canonical orientation is the side not containing the reference DFS-first leaf,
     * matching the production rule expressed in terms of names instead of bits.
     */
    private static void assertSupportsMatchFrequencies(TreeNode reference,
                                                       List<TreeNode> replicateTrees,
                                                       TreeNode annotated, int taxa) {
        Set<String> allNames = leafNameSet(reference);
        assertEquals(taxa, allNames.size());
        String anchor = leaves(reference).get(0);

        Map<Set<String>, Integer> frequencies = new HashMap<>();
        for (TreeNode tree : replicateTrees) {
            Set<Set<String>> seen = new HashSet<>();
            collectCanonicalNameSplits(tree, allNames, anchor, seen);
            for (Set<String> split : seen) {
                frequencies.merge(split, 1, Integer::sum);
            }
        }

        int replicates = replicateTrees.size();
        compareNodeSupport(reference, annotated, allNames, anchor, frequencies,
                replicates, taxa);
    }

    private static Set<String> collectCanonicalNameSplits(
            TreeNode node, Set<String> allNames, String anchor, Set<Set<String>> out) {
        if (node.isLeaf()) {
            return Set.of(node.getLabel());
        }
        Set<String> set = new HashSet<>();
        for (TreeNode child : node.getChildren()) {
            set.addAll(collectCanonicalNameSplits(child, allNames, anchor, out));
        }
        if (set.size() > 1 && set.size() < allNames.size() - 1) {
            out.add(set.contains(anchor) ? complement(allNames, set) : set);
        }
        return set;
    }

    private static Set<String> complement(Set<String> all, Set<String> side) {
        Set<String> rest = new HashSet<>(all);
        rest.removeAll(side);
        return rest;
    }

    private static void compareNodeSupport(TreeNode referenceNode, TreeNode annotatedNode,
                                           Set<String> allNames, String anchor,
                                           Map<Set<String>, Integer> frequencies,
                                           int replicates, int taxa) {
        if (referenceNode.isLeaf()) {
            return;
        }
        for (int c = 0; c < referenceNode.getChildren().size(); c++) {
            compareNodeSupport(referenceNode.getChildren().get(c),
                    annotatedNode.getChildren().get(c), allNames, anchor, frequencies,
                    replicates, taxa);
        }
        Set<String> set = leafNameSet(referenceNode);
        Double support = annotatedNode.getSupport();
        assertNotNull(support, "missing support at " + referenceNode.getLabel());
        assertTrue(support >= 0.0 && support <= 1.0, "support out of range: " + support);
        double expected;
        if (set.size() == taxa) {
            expected = 1.0;
        } else {
            Set<String> canonical = set.contains(anchor)
                    ? complement(allNames, set) : set;
            expected = (double) frequencies.getOrDefault(canonical, 0) / replicates;
        }
        assertEquals(expected, support, 1e-12,
                "support mismatch at " + referenceNode.getLabel());
    }

    @Test
    void completesAt128Taxa200ColumnsWithDefaultReplicatesAndAllSupports() {
        int taxa = 128;
        int length = 200;
        Alignment alignment = FastaReader.parse(dnaAlignment(taxa, length, 20261007L));

        // Default configuration: 100 replicates, fixed seed.
        BootstrapConfig config = new BootstrapConfig(100, 42L, 1, 0L);
        BootstrapResult result = new BootstrapEngine(1).run(alignment, config);

        assertEquals(taxa, countLeaves(result.consensusTree()));
        assertEquals(100, result.bootstrapTrees().size());
        // A binary rooted tree with n leaves has n-1 internal nodes incl. the root.
        assertEquals(taxa - 1, countInternal(result.consensusTree()));

        List<TreeNode> annotated = internalNodes(result.consensusTree());
        for (TreeNode node : annotated) {
            assertNotNull(node.getSupport(), "missing support at " + node.getLabel());
            assertTrue(node.getSupport() >= 0.0 && node.getSupport() <= 1.0,
                    "support out of bounds: " + node.getSupport());
        }
        assertEquals(1.0, result.consensusTree().getSupport(), 0.0);
        assertSupportsMatchFrequencies(
                BootstrapEngine.referenceTree(alignment),
                result.bootstrapTrees(), result.consensusTree(), taxa);

        // Every replicate tree must itself be a full binary tree over all taxa.
        for (TreeNode tree : result.bootstrapTrees()) {
            assertEquals(taxa, countLeaves(tree));
            assertEquals(taxa - 1, countInternal(tree));
            String serialized = NewickWriter.write(tree);
            assertEquals(serialized, NewickWriter.write(NewickParser.parse(serialized)),
                    "replicate tree must round-trip losslessly");
        }

        // Supports must be fractions of 100 replicates (4 decimal output granularity).
        for (TreeNode node : annotated) {
            double s = node.getSupport();
            double roundedToHundredth = Math.round(s * 100.0) / 100.0;
            assertEquals(roundedToHundredth, s, 1e-12,
                    "support " + s + " is not a multiple of 1/100");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {62, 63, 64, 65})
    void boundaryTaxonCountsGiveDeterminedCompleteSupports(int taxa) {
        int length = 200;
        Alignment alignment = FastaReader.parse(dnaAlignment(taxa, length, 1000L + taxa));
        BootstrapResult result = new BootstrapEngine(4)
                .run(alignment, new BootstrapConfig(40, 9L, 4, 0L));

        assertEquals(taxa, countLeaves(result.consensusTree()));
        assertEquals(taxa - 1, countInternal(result.consensusTree()));
        assertEquals(40, result.bootstrapTrees().size());

        for (TreeNode node : internalNodes(result.consensusTree())) {
            assertNotNull(node.getSupport(),
                    "missing support at boundary n=" + taxa + " node " + node.getLabel());
            assertTrue(node.getSupport() >= 0.0 && node.getSupport() <= 1.0,
                    "support out of bounds at boundary n=" + taxa + ": " + node.getSupport());
        }
        assertEquals(1.0, result.consensusTree().getSupport(), 0.0);

        // Determinism: same (seed, input) reproduces byte-identical output.
        BootstrapResult again = new BootstrapEngine(2)
                .run(alignment, new BootstrapConfig(40, 9L, 2, 0L));
        assertEquals(NewickWriter.write(result.consensusTree()),
                NewickWriter.write(again.consensusTree()));
        for (int r = 0; r < result.bootstrapTrees().size(); r++) {
            assertEquals(NewickWriter.write(result.bootstrapTrees().get(r)),
                    NewickWriter.write(again.bootstrapTrees().get(r)));
        }
    }

    @Test
    void identicalAlignmentYieldsSupportOneOnEveryInternalBranch() {
        // All sequences identical: every NJ tree is the zero-length index-tie tree,
        // reference and replicates agree on every (non-trivial) split -> support is
        // exactly 1 on every genuine internal branch. The two clades adjacent to the
        // artificial root are pendant (one side is a single leaf) and carry 0; the
        // root itself carries 1.
        int taxa = 10;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < taxa; i++) {
            sb.append(">s").append(i).append('\n').append("ACGT".repeat(50)).append('\n');
        }
        Alignment alignment = FastaReader.parse(sb.toString());
        BootstrapResult result = new BootstrapEngine(2)
                .run(alignment, new BootstrapConfig(20, 3L, 2, 0L));
        for (TreeNode node : internalNodes(result.consensusTree())) {
            int subtreeLeaves = countLeaves(node);
            boolean internalBranch = subtreeLeaves >= 2 && subtreeLeaves <= taxa - 2;
            if (internalBranch) {
                assertEquals(1.0, node.getSupport(), 0.0,
                        "internal branch " + node.getLabel() + " should be supported");
            }
        }
        assertEquals(1.0, result.consensusTree().getSupport(), 0.0);
    }

    @Test
    void consensusBuilderHandlesMoreThan63Taxa() {
        Alignment alignment = FastaReader.parse(dnaAlignment(130, 200, 5L));
        TreeNode reference = BootstrapEngine.referenceTree(alignment);
        List<TreeNode> replicates = new ArrayList<>();
        for (int r = 0; r < 10; r++) {
            replicates.add(BootstrapEngine.buildSingleReplicate(alignment, 1L, r));
        }
        TreeNode annotated = ConsensusBuilder.annotate(reference, replicates);
        assertEquals(130, countLeaves(annotated));
        for (TreeNode node : internalNodes(annotated)) {
            assertNotNull(node.getSupport());
            assertTrue(node.getSupport() >= 0.0 && node.getSupport() <= 1.0);
        }
    }
}
