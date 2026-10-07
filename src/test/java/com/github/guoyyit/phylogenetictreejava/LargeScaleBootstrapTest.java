package com.github.guoyyit.phylogenetictreejava;

import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapConfig;
import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapEngine;
import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapResult;
import com.github.guoyyit.phylogenetictreejava.io.FastaReader;
import com.github.guoyyit.phylogenetictreejava.io.NewickParser;
import com.github.guoyyit.phylogenetictreejava.io.NewickWriter;
import com.github.guoyyit.phylogenetictreejava.model.Alignment;
import com.github.guoyyit.phylogenetictreejava.model.TreeNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scale requirements: at least 128 sequences / 200 columns complete and annotate
 * every internal node, the 62..65 boundary is well defined, and results are
 * independent of worker count.
 */
class LargeScaleBootstrapTest {

    private static final int LARGE_SEQUENCES = 128;
    private static final int LARGE_LENGTH = 200;

    @Test
    void completes128Sequences200ColumnsAndSupportsEveryInternalNode() {
        Alignment alignment = FastaReader.parse(
                LargeAlignmentFixtures.randomDnaFasta(LARGE_SEQUENCES, LARGE_LENGTH, 20261007L));
        BootstrapResult result = new BootstrapEngine(8)
                .run(alignment, new BootstrapConfig(100, 42L, 8, 0));

        int[] internal = {0};
        assertEveryInternalNodeSupported(result.consensusTree(), internal);
        assertEquals(LARGE_SEQUENCES - 1, internal[0],
                "binary tree on 128 leaves must have 127 internal nodes");
        assertEquals(100, result.bootstrapTrees().size());
        assertEquals(LARGE_SEQUENCES, countLeaves(result.consensusTree()));

        // Supports must survive the documented lossless Newick round trip.
        String written = NewickWriter.write(result.consensusTree());
        assertEquals(written, NewickWriter.write(NewickParser.parse(written)));

        // Independent support verification from the actual replicate trees.
        verifySupportsAgainstReplicates(result);
    }

    @Test
    void identicalLargeSequencesGiveFullSupportEverywhere() {
        Alignment alignment = FastaReader.parse(
                LargeAlignmentFixtures.identicalDnaFasta(LARGE_SEQUENCES, LARGE_LENGTH));
        BootstrapResult result = new BootstrapEngine(8)
                .run(alignment, new BootstrapConfig(40, 7L, 8, 0));
        assertSupportExactlyOne(result.consensusTree());
    }

    @ParameterizedTest
    @ValueSource(ints = {62, 63, 64, 65})
    void boundarySizesProduceCompleteDeterminateSupports(int sequences) {
        Alignment alignment = FastaReader.parse(
                LargeAlignmentFixtures.randomDnaFasta(sequences, LARGE_LENGTH, 99L + sequences));
        BootstrapResult first = new BootstrapEngine(4)
                .run(alignment, new BootstrapConfig(40, 123L, 4, 0));
        BootstrapResult second = new BootstrapEngine(4)
                .run(alignment, new BootstrapConfig(40, 123L, 4, 0));

        int[] internal = {0};
        assertEveryInternalNodeSupported(first.consensusTree(), internal);
        assertEquals(sequences - 1, internal[0]);
        assertEquals(sequences, countLeaves(first.consensusTree()));
        assertEquals(40, first.bootstrapTrees().size());
        // Determinate: re-running yields byte-identical output.
        assertEquals(serialize(first), serialize(second));
    }

    @Test
    void largeRunIsByteIdenticalAtConcurrency1And8() {
        Alignment alignment = FastaReader.parse(
                LargeAlignmentFixtures.randomDnaFasta(LARGE_SEQUENCES, LARGE_LENGTH, 555L));
        BootstrapResult serial = new BootstrapEngine(1)
                .run(alignment, new BootstrapConfig(40, 314L, 1, 0));
        BootstrapResult parallel = new BootstrapEngine(8)
                .run(alignment, new BootstrapConfig(40, 314L, 8, 0));
        assertEquals(serialize(serial), serialize(parallel));
    }

    private static void assertEveryInternalNodeSupported(TreeNode node, int[] internal) {
        if (node.isLeaf()) {
            return;
        }
        internal[0]++;
        Double support = node.getSupport();
        assertNotNull(support, "internal node missing support");
        assertTrue(support >= 0.0 && support <= 1.0,
                "support out of range: " + support);
        for (TreeNode child : node.getChildren()) {
            assertEveryInternalNodeSupported(child, internal);
        }
    }

    private static void assertSupportExactlyOne(TreeNode node) {
        if (!node.isLeaf()) {
            assertNotNull(node.getSupport());
            assertEquals(1.0, node.getSupport(), 0.0);
            for (TreeNode child : node.getChildren()) {
                assertSupportExactlyOne(child);
            }
        }
    }

    private static int countLeaves(TreeNode node) {
        if (node.isLeaf()) {
            return 1;
        }
        int leaves = 0;
        for (TreeNode child : node.getChildren()) {
            leaves += countLeaves(child);
        }
        return leaves;
    }

    /**
     * Recomputes split frequencies straight from the replicate trees and compares
     * them with the supports written on the reference tree, using an independent
     * leaf-ordering to avoid coupling to the engine's internal indexing.
     */
    private static void verifySupportsAgainstReplicates(BootstrapResult result) {
        List<String> leaves = new ArrayList<>();
        collectLeaves(result.consensusTree(), leaves);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < leaves.size(); i++) {
            index.put(leaves.get(i), i);
        }
        int n = leaves.size();

        Map<BitSet, Integer> counts = new HashMap<>();
        for (TreeNode tree : result.bootstrapTrees()) {
            Set<BitSet> splits = new HashSet<>();
            collectSplits(tree, index, n, splits);
            for (BitSet split : splits) {
                counts.merge(split, 1, Integer::sum);
            }
        }
        int replicates = result.bootstrapTrees().size();
        checkNodeSupports(result.consensusTree(), index, n, counts, replicates, true);
    }

    private static void checkNodeSupports(TreeNode node, Map<String, Integer> index, int n,
                                          Map<BitSet, Integer> counts, int replicates,
                                          boolean isRoot) {
        if (node.isLeaf()) {
            return;
        }
        BitSet mask = new BitSet(n);
        for (TreeNode child : node.getChildren()) {
            checkNodeSupports(child, index, n, counts, replicates, false);
            mask.or(subtreeMask(child, index, n));
        }
        double expected;
        if (isRoot) {
            expected = 1.0;
        } else {
            BitSet split = canonical(mask, n);
            expected = (double) counts.getOrDefault(split, 0) / replicates;
        }
        assertEquals(expected, node.getSupport(), 1e-12,
                "support mismatch on node " + node.getLabel());
    }

    private static BitSet collectSplits(TreeNode node, Map<String, Integer> index, int n,
                                        Set<BitSet> splits) {
        if (node.isLeaf()) {
            BitSet mask = new BitSet(n);
            Integer bit = index.get(node.getLabel());
            if (bit != null) {
                mask.set(bit);
            }
            return mask;
        }
        BitSet mask = new BitSet(n);
        for (TreeNode child : node.getChildren()) {
            mask.or(collectSplits(child, index, n, splits));
        }
        int cardinality = mask.cardinality();
        if (cardinality > 1 && cardinality < n) {
            splits.add(canonical(mask, n));
        }
        return mask;
    }

    private static BitSet subtreeMask(TreeNode node, Map<String, Integer> index, int n) {
        if (node.isLeaf()) {
            BitSet mask = new BitSet(n);
            Integer bit = index.get(node.getLabel());
            if (bit != null) {
                mask.set(bit);
            }
            return mask;
        }
        BitSet mask = new BitSet(n);
        for (TreeNode child : node.getChildren()) {
            mask.or(subtreeMask(child, index, n));
        }
        return mask;
    }

    private static BitSet canonical(BitSet mask, int n) {
        return mask.get(n - 1) ? complement(mask, n) : (BitSet) mask.clone();
    }

    private static BitSet complement(BitSet mask, int n) {
        BitSet complement = new BitSet(n);
        complement.set(0, n);
        complement.andNot(mask);
        return complement;
    }

    private static void collectLeaves(TreeNode node, List<String> leaves) {
        if (node.isLeaf()) {
            leaves.add(node.getLabel());
            return;
        }
        for (TreeNode child : node.getChildren()) {
            collectLeaves(child, leaves);
        }
    }

    private static String serialize(BootstrapResult result) {
        return result.bootstrapTrees().stream()
                .map(NewickWriter::write)
                .collect(Collectors.joining("\n"))
                + "\n#CONSENSUS#\n"
                + NewickWriter.write(result.consensusTree());
    }
}
