package com.github.guoyyit.phylogenetictreejava.consensus;

import com.github.guoyyit.phylogenetictreejava.model.TreeNode;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Annotates a reference tree's internal branches with bootstrap support.
 */
public final class ConsensusBuilder {

    private ConsensusBuilder() {
    }

    /**
     * Returns a copy of {@code reference} whose internal branches are annotated with
     * the fraction of replicate trees inducing the same leaf split. The reference
     * topology (from the original alignment) is preserved; only support is added.
     *
     * <p>Splits are canonical leaf-set {@link BitSet}s (the side not containing
     * leaf index 0), so alignments of any size are supported without fixed limits.
     */
    public static TreeNode annotate(TreeNode reference, List<TreeNode> replicateTrees) {
        List<String> leaves = new ArrayList<>();
        collectLeaves(reference, leaves);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < leaves.size(); i++) {
            index.put(leaves.get(i), i);
        }
        int leafCount = leaves.size();

        int replicates = replicateTrees.size();
        Map<BitSet, Integer> splitCounts = new HashMap<>();
        for (TreeNode tree : replicateTrees) {
            Set<BitSet> splits = new HashSet<>();
            BitSet whole = collectSplits(tree, index, leafCount, splits);
            // A binary replicate should contain every reference leaf; nothing else
            // to do with the whole-tree mask, but compute it once for consistency.
            if (whole.cardinality() == 0 && leafCount > 0) {
                throw new IllegalArgumentException("replicate tree contains no recognizable leaves");
            }
            for (BitSet split : splits) {
                splitCounts.merge(split, 1, Integer::sum);
            }
        }

        TreeNode copy = copyTree(reference);
        annotateNode(copy, index, leafCount, splitCounts, replicates);
        return copy;
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

    private static BitSet collectSplits(TreeNode node, Map<String, Integer> index, int leafCount,
                                       Set<BitSet> splits) {
        if (node.isLeaf()) {
            Integer bit = index.get(node.getLabel());
            BitSet mask = new BitSet(leafCount);
            if (bit != null) {
                mask.set(bit);
            }
            return mask;
        }
        BitSet mask = new BitSet(leafCount);
        for (TreeNode child : node.getChildren()) {
            mask.or(collectSplits(child, index, leafCount, splits));
        }
        int bits = mask.cardinality();
        if (bits > 1 && bits < leafCount - 1) {
            splits.add(canonicalSplit(mask, leafCount));
        }
        return mask;
    }

    /**
     * Canonical orientation of a split: the side that does not contain leaf 0.
     * The returned instance is never mutated afterwards, so it is safe as a map key.
     */
    private static BitSet canonicalSplit(BitSet mask, int leafCount) {
        if (mask.get(0)) {
            BitSet complement = new BitSet(leafCount);
            complement.set(0, leafCount);
            complement.andNot(mask);
            return complement;
        }
        return (BitSet) mask.clone();
    }

    private static void annotateNode(TreeNode node, Map<String, Integer> index, int leafCount,
                                     Map<BitSet, Integer> splitCounts, int replicates) {
        if (node.isLeaf()) {
            return;
        }
        BitSet mask = new BitSet(leafCount);
        for (TreeNode child : node.getChildren()) {
            annotateNode(child, index, leafCount, splitCounts, replicates);
            mask.or(subtreeMask(child, index));
        }
        int bits = mask.cardinality();
        if (bits == leafCount) {
            node.setSupport(replicates == 0 ? 0.0 : 1.0);
            return;
        }
        BitSet split = canonicalSplit(mask, leafCount);
        int count = splitCounts.getOrDefault(split, 0);
        node.setSupport(replicates == 0 ? 0.0 : (double) count / replicates);
    }

    private static BitSet subtreeMask(TreeNode node, Map<String, Integer> index) {
        if (node.isLeaf()) {
            Integer bit = index.get(node.getLabel());
            BitSet mask = new BitSet();
            if (bit != null) {
                mask.set(bit);
            }
            return mask;
        }
        BitSet mask = new BitSet();
        for (TreeNode child : node.getChildren()) {
            mask.or(subtreeMask(child, index));
        }
        return mask;
    }

    private static TreeNode copyTree(TreeNode source) {
        TreeNode copy = new TreeNode();
        copy.setLabel(source.getLabel());
        copy.setBranchLength(source.getBranchLength());
        for (TreeNode child : source.getChildren()) {
            copy.addChild(copyTree(child));
        }
        return copy;
    }
}
