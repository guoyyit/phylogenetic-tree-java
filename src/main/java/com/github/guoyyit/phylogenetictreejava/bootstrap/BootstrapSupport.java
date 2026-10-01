package com.github.guoyyit.phylogenetictreejava.bootstrap;

import com.github.guoyyit.phylogenetictreejava.tree.PhyloTree;

import java.util.List;

/**
 * Result of a bootstrap run: every replicate tree in replicate order and
 * the labeled majority-rule consensus tree.
 */
public final class BootstrapSupport {

    private final List<PhyloTree> replicateTrees;
    private final PhyloTree consensusTree;

    public BootstrapSupport(List<PhyloTree> replicateTrees, PhyloTree consensusTree) {
        this.replicateTrees = List.copyOf(replicateTrees);
        this.consensusTree = consensusTree;
    }

    public List<PhyloTree> replicateTrees() {
        return replicateTrees;
    }

    public PhyloTree consensusTree() {
        return consensusTree;
    }
}
