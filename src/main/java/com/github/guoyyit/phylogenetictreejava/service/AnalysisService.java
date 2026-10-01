package com.github.guoyyit.phylogenetictreejava.service;

import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapRunner;
import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapSupport;
import com.github.guoyyit.phylogenetictreejava.bootstrap.CancellationToken;
import com.github.guoyyit.phylogenetictreejava.bootstrap.CheckpointStore;
import com.github.guoyyit.phylogenetictreejava.distance.DistanceMatrix;
import com.github.guoyyit.phylogenetictreejava.distance.PairwiseDistanceCalculator;
import com.github.guoyyit.phylogenetictreejava.io.AlignmentValidator;
import com.github.guoyyit.phylogenetictreejava.io.NewickCodec;
import com.github.guoyyit.phylogenetictreejava.model.Alignment;
import com.github.guoyyit.phylogenetictreejava.model.AlignmentInput;
import com.github.guoyyit.phylogenetictreejava.tree.NeighborJoiningTreeBuilder;
import com.github.guoyyit.phylogenetictreejava.tree.PhyloTree;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Pure in-process analysis pipeline with no external services:
 * validation, pairwise distances, NJ tree, bootstrap support and output.
 */
public final class AnalysisService {

    private final AlignmentValidator validator = new AlignmentValidator();
    private final PairwiseDistanceCalculator distanceCalculator =
            new PairwiseDistanceCalculator();
    private final NeighborJoiningTreeBuilder treeBuilder =
            new NeighborJoiningTreeBuilder();
    private final NewickCodec newickCodec = new NewickCodec();

    public AnalysisService() {
    }

    public Alignment validate(List<AlignmentInput> inputs) {
        return validator.validate(inputs);
    }

    public DistanceMatrix distances(Alignment alignment) {
        return distanceCalculator.calculate(alignment);
    }

    public PhyloTree tree(DistanceMatrix matrix) {
        return treeBuilder.build(matrix);
    }

    public BootstrapSupport bootstrap(Alignment alignment,
                                      int replicates,
                                      long seed,
                                      int concurrency,
                                      CheckpointStore checkpointStore,
                                      CancellationToken cancellationToken) {
        return bootstrap(alignment, replicates, seed, concurrency,
                checkpointStore, cancellationToken, completed -> { });
    }

    public BootstrapSupport bootstrap(Alignment alignment,
                                      int replicates,
                                      long seed,
                                      int concurrency,
                                      CheckpointStore checkpointStore,
                                      CancellationToken cancellationToken,
                                      IntConsumer progressListener) {
        int threads = Math.max(1, concurrency);
        ExecutorService executor = Executors.newFixedThreadPool(threads,
                runnable -> {
                    Thread thread = new Thread(runnable, "bootstrap-worker");
                    thread.setDaemon(true);
                    return thread;
                });
        try {
            BootstrapRunner runner = new BootstrapRunner(
                    executor, checkpointStore, progressListener);
            return runner.run(alignment, replicates, seed, cancellationToken);
        } finally {
            executor.shutdownNow();
        }
    }

    public String toNewick(PhyloTree tree) {
        return newickCodec.write(tree);
    }

    public String toNewick(List<PhyloTree> trees) {
        return newickCodec.writeAll(trees);
    }
}
