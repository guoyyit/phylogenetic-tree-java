package com.github.guoyyit.phylogenetictreejava.bootstrap;

import com.github.guoyyit.phylogenetictreejava.tree.PhyloTree;

import java.util.SortedMap;

/**
 * Durable storage of finished bootstrap replicates, keyed by replicate
 * index. All writes are atomic; a partially written replicate is never
 * observable after a crash.
 */
public interface CheckpointStore {

    SortedMap<Integer, PhyloTree> loadCompleted();

    void saveReplicate(int replicateIndex, PhyloTree tree);
}
