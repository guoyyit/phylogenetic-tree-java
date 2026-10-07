package com.github.guoyyit.phylogenetictreejava.consensus;

import com.github.guoyyit.phylogenetictreejava.model.TreeNode;

import java.util.BitSet;
import java.util.HashMap;
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
     * <p>Splits are represented with {@link BitSet}, so the analysis scales beyond
     * 63 sequences (at least 128 supported); behavior is unchanged for smaller
     * inputs.
     */
    public static TreeNode annotate(TreeNode reference, List<TreeNode> replicateTrees) {
        List<String> leaves = new java.util.ArrayList<>();
        collectLeaves(reference, leaves);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < leaves.size(); i++) {
            index.put(leaves.get(i), i);
        }
        int leafCount = leaves.size();

        int replicates = replicateTrees.size();
        Map<BitSet, Integer> splitCounts = new HashMap<>();
        for (TreeNode tree : replicateTrees) {
            Set<BitSet> splits = new java.util.HashSet<>();
            collectSplits(tree, index, leafCount, splits);
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
            BitSet leafMask = new BitSet(leafCount);
            if (bit != null) {
                leafMask.set(bit);
            }
            return leafMask;
        }
        BitSet mask = new BitSet(leafCount);
        for (TreeNode child : node.getChildren()) {
            mask.or(collectSplits(child, index, leafCount, splits));
        }
        int bits = mask.cardinality();
        if (bits > 1 && bits < leafCount) {
            splits.add(normalizeSplit(mask, leafCount));
        }
        return mask;
    }

    /**
     * Orientation-independent canonical representative of a bipartition. Mask and
     * its complement jointly cover all leaf positions and are disjoint, so the most
     * significant leaf bit uniquely decides which side is numerically smaller
     * (identical rule to the former {@code Long.compareUnsigned} representation).
     */
    private static BitSet normalizeSplit(BitSet mask, int leafCount) {
        if (leafCount == 0) {
            return (BitSet) mask.clone();
        }
        return mask.get(leafCount - 1) ? complement(mask, leafCount) : (BitSet) mask.clone();
    }

    private static BitSet complement(BitSet mask, int leafCount) {
        BitSet complement = new BitSet(leafCount);
        complement.set(0, leafCount);
        complement.andNot(mask);
        return complement;
    }

    private static void annotateNode(TreeNode node, Map<String, Integer> index, int leafCount,
                                     Map<BitSet, Integer> splitCounts, int replicates) {
        if (node.isLeaf()) {
            return;
        }
        BitSet mask = new BitSet(leafCount);
        for (TreeNode child : node.getChildren()) {
            annotateNode(child, index, leafCount, splitCounts, replicates);
            mask.or(subtreeMask(child, index, leafCount));
        }
        if (mask.cardinality() == leafCount) {
            node.setSupport(replicates == 0 ? 0.0 : 1.0);
            return;
        }
        BitSet split = normalizeSplit(mask, leafCount);
        int count = splitCounts.getOrDefault(split, 0);
        node.setSupport(replicates == 0 ? 0.0 : (double) count / replicates);
    }

    private static BitSet subtreeMask(TreeNode node, Map<String, Integer> index, int leafCount) {
        if (node.isLeaf()) {
            Integer bit = index.get(node.getLabel());
            BitSet leafMask = new BitSet(leafCount);
            if (bit != null) {
                leafMask.set(bit);
            }
            return leafMask;
        }
        BitSet mask = new BitSet(leafCount);
        for (TreeNode child : node.getChildren()) {
            mask.or(subtreeMask(child, index, leafCount));
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
