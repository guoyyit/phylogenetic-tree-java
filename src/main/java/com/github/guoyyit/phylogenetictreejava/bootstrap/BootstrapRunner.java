package com.github.guoyyit.phylogenetictreejava.bootstrap;

import com.github.guoyyit.phylogenetictreejava.model.Alignment;
import com.github.guoyyit.phylogenetictreejava.tree.ConsensusBuilder;
import com.github.guoyyit.phylogenetictreejava.tree.PhyloTree;
import com.github.guoyyit.phylogenetictreejava.distance.DistanceMatrix;
import com.github.guoyyit.phylogenetictreejava.distance.PairwiseDistanceCalculator;
import com.github.guoyyit.phylogenetictreejava.tree.NeighborJoiningTreeBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.SortedMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;

/**
 * Executes bootstrap replicates with a configurable executor.
 *
 * <p>Determinism contract: for identical input and seed the replicate trees
 * are identical regardless of concurrency level or scheduling order; each
 * replicate uses an independent {@link java.util.Random} stream derived
 * deterministically from the seed and its index.</p>
 */
public final class BootstrapRunner {

    private final ExecutorService executor;
    private final CheckpointStore checkpointStore;
    private final BootstrapResampler resampler = new BootstrapResampler();
    private final PairwiseDistanceCalculator distanceCalculator =
            new PairwiseDistanceCalculator();
    private final NeighborJoiningTreeBuilder treeBuilder =
            new NeighborJoiningTreeBuilder();
    private final ConsensusBuilder consensusBuilder = new ConsensusBuilder();
    private final IntConsumer progressListener;

    public BootstrapRunner(ExecutorService executor, CheckpointStore checkpointStore) {
        this(executor, checkpointStore, completed -> { });
    }

    public BootstrapRunner(ExecutorService executor, CheckpointStore checkpointStore,
                           IntConsumer progressListener) {
        this.executor = executor;
        this.checkpointStore = checkpointStore;
        this.progressListener = progressListener;
    }

    public BootstrapSupport run(Alignment alignment,
                                int replicateCount,
                                long seed,
                                CancellationToken cancellationToken) {
        if (replicateCount < 0) {
            throw new IllegalArgumentException(
                    "replicate count must not be negative");
        }

        SortedMap<Integer, PhyloTree> completed = checkpointStore.loadCompleted();
        List<Future<?>> futures = new ArrayList<>();
        for (int replicate = 0; replicate < replicateCount; replicate++) {
            if (cancellationToken.isCancelled()) {
                break;
            }
            final int index = replicate;
            futures.add(executor.submit(() -> {
                if (cancellationToken.isCancelled()
                        || completed.containsKey(index)) {
                    return;
                }
                Random random = replicateRandom(seed, index);
                Alignment resampled = resampler.resample(alignment, random);
                DistanceMatrix distances = distanceCalculator.calculate(resampled);
                PhyloTree tree = treeBuilder.build(distances);
                synchronized (checkpointStore) {
                    checkpointStore.saveReplicate(index, tree);
                }
                progressListener.accept(1);
            }));
        }

        await(futures, cancellationToken);

        if (cancellationToken.isCancelled()) {
            throw new BootstrapCancelledException();
        }

        SortedMap<Integer, PhyloTree> all = checkpointStore.loadCompleted();
        List<PhyloTree> orderedTrees = new ArrayList<>(replicateCount);
        for (int replicate = 0; replicate < replicateCount; replicate++) {
            PhyloTree tree = all.get(replicate);
            if (tree == null) {
                throw new ReplicateMissingException(replicate);
            }
            orderedTrees.add(tree);
        }
        PhyloTree consensus = consensusBuilder.build(orderedTrees);
        return new BootstrapSupport(orderedTrees, consensus);
    }

    private void await(List<Future<?>> futures, CancellationToken cancellationToken) {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancellationToken.cancel();
                throw new BootstrapInterruptedException(e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new RuntimeException(cause);
            }
        }
    }

    static Random replicateRandom(long seed, int replicateIndex) {
        long mixed = seed;
        mixed ^= (long) (replicateIndex + 1) * 0x9E3779B97F4A7C15L;
        mixed = Long.rotateLeft(mixed, 31);
        mixed ^= mixed >>> 27;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 31;
        mixed *= 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return new Random(mixed);
    }

    public static final class ReplicateMissingException extends RuntimeException {
        public ReplicateMissingException(int replicateIndex) {
            super("replicate " + replicateIndex + " is missing after bootstrap run");
        }
    }

    public static final class BootstrapInterruptedException extends RuntimeException {
        public BootstrapInterruptedException(Throwable cause) {
            super(cause);
        }
    }

    public static final class BootstrapCancelledException extends RuntimeException {
        public BootstrapCancelledException() {
            super("bootstrap run was cancelled");
        }
    }
}
