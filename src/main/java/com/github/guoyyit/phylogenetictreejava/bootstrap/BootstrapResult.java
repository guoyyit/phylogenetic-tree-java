package com.github.guoyyit.phylogenetictreejava.bootstrap;

import com.github.guoyyit.phylogenetictreejava.model.TreeNode;

import java.util.List;

/**
 * Result of a bootstrap analysis: the supported consensus tree plus every replicate tree.
 */
public record BootstrapResult(TreeNode consensusTree, List<TreeNode> bootstrapTrees) {
}
