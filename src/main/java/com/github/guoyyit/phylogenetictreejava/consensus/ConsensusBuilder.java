package com.github.guoyyit.phylogenetictreejava.consensus;

import com.github.guoyyit.phylogenetictreejava.model.TreeNode;

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
     */
    public static TreeNode annotate(TreeNode reference, List<TreeNode> replicateTrees) {
        List<String> leaves = new java.util.ArrayList<>();
        collectLeaves(reference, leaves);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < leaves.size(); i++) {
            index.put(leaves.get(i), i);
        }
        if (leaves.size() > 63) {
            throw new IllegalArgumentException(
                    "bootstrap consensus supports at most 63 sequences, got " + leaves.size());
        }

        int replicates = replicateTrees.size();
        Map<Long, Integer> splitCounts = new HashMap<>();
        for (TreeNode tree : replicateTrees) {
            Set<Long> splits = new java.util.HashSet<>();
            collectSplits(tree, index, leaves.size(), splits);
            for (long split : splits) {
                splitCounts.merge(split, 1, Integer::sum);
            }
        }

        TreeNode copy = copyTree(reference);
        annotateNode(copy, index, leaves.size(), splitCounts, replicates);
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

    private static long collectSplits(TreeNode node, Map<String, Integer> index, int leafCount,
                                      Set<Long> splits) {
        if (node.isLeaf()) {
            Integer bit = index.get(node.getLabel());
            return bit == null ? 0L : (1L << bit);
        }
        long mask = 0L;
        for (TreeNode child : node.getChildren()) {
            mask |= collectSplits(child, index, leafCount, splits);
        }
        int bits = Long.bitCount(mask);
        if (bits > 1 && bits < leafCount - 1) {
            splits.add(normalizeSplit(mask, leafCount));
        }
        return mask;
    }

    private static long normalizeSplit(long mask, int leafCount) {
        long allLeaves = (1L << leafCount) - 1L;
        long complement = allLeaves & ~mask;
        return Long.compareUnsigned(mask, complement) <= 0 ? mask : complement;
    }

    private static void annotateNode(TreeNode node, Map<String, Integer> index, int leafCount,
                                     Map<Long, Integer> splitCounts, int replicates) {
        if (node.isLeaf()) {
            return;
        }
        long mask = 0L;
        for (TreeNode child : node.getChildren()) {
            annotateNode(child, index, leafCount, splitCounts, replicates);
            mask |= subtreeMask(child, index);
        }
        int bits = Long.bitCount(mask);
        if (bits == leafCount) {
            node.setSupport(replicates == 0 ? 0.0 : 1.0);
            return;
        }
        long split = normalizeSplit(mask, leafCount);
        int count = splitCounts.getOrDefault(split, 0);
        node.setSupport(replicates == 0 ? 0.0 : (double) count / replicates);
    }

    private static long subtreeMask(TreeNode node, Map<String, Integer> index) {
        if (node.isLeaf()) {
            Integer bit = index.get(node.getLabel());
            return bit == null ? 0L : (1L << bit);
        }
        long mask = 0L;
        for (TreeNode child : node.getChildren()) {
            mask |= subtreeMask(child, index);
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
