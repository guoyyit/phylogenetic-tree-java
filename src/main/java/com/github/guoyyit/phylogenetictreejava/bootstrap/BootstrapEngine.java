package com.github.guoyyit.phylogenetictreejava.bootstrap;

import com.github.guoyyit.phylogenetictreejava.model.Alignment;
import com.github.guoyyit.phylogenetictreejava.consensus.ConsensusBuilder;
import com.github.guoyyit.phylogenetictreejava.distance.DistanceCalculator;
import com.github.guoyyit.phylogenetictreejava.model.DistanceMatrix;
import com.github.guoyyit.phylogenetictreejava.model.Sequence;
import com.github.guoyyit.phylogenetictreejava.model.TreeNode;
import com.github.guoyyit.phylogenetictreejava.tree.NeighborJoining;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Runs bootstrap resampling experiments, deterministically and concurrently.
 */
public final class BootstrapEngine {

    private final int concurrency;

    public BootstrapEngine(int concurrency) {
        if (concurrency < 1) {
            throw new IllegalArgumentException("concurrency must be >= 1, got " + concurrency);
        }
        this.concurrency = concurrency;
    }

    public BootstrapResult run(Alignment alignment, BootstrapConfig config) {
        return run(alignment, config, () -> false);
    }

    public BootstrapResult run(Alignment alignment, BootstrapConfig config, BooleanSupplier cancelled) {
        validate(config);
        TreeNode reference = NeighborJoining.build(DistanceCalculator.compute(alignment));

        int workers = Math.min(concurrency, Math.max(1, config.concurrency()));
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Future<TreeNode>> futures = new ArrayList<>(config.replicates());
        try {
            for (int replicate = 0; replicate < config.replicates(); replicate++) {
                final int index = replicate;
                futures.add(pool.submit(() -> buildReplicate(alignment, config.seed(), index)));
            }
            List<TreeNode> trees = new ArrayList<>(config.replicates());
            for (Future<TreeNode> future : futures) {
                trees.add(await(future, cancelled));
            }
            TreeNode consensus = ConsensusBuilder.annotate(reference, trees);
            return new BootstrapResult(consensus, List.copyOf(trees));
        } finally {
            pool.shutdownNow();
        }
    }

    private static TreeNode await(Future<TreeNode> future, BooleanSupplier cancelled) {
        try {
            while (true) {
                if (cancelled.getAsBoolean()) {
                    future.cancel(true);
                    throw new BootstrapCancelledException();
                }
                try {
                    return future.get(200, TimeUnit.MILLISECONDS);
                } catch (java.util.concurrent.TimeoutException ignored) {
                    // poll the cancellation flag again
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BootstrapCancelledException();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        }
    }

    private static TreeNode buildReplicate(Alignment alignment, long seed, int replicate) {
        Random random = replicateRandom(seed, replicate);
        int length = alignment.length();
        int[] columns = new int[length];
        for (int k = 0; k < length; k++) {
            columns[k] = random.nextInt(length);
        }
        List<Sequence> resampled = new ArrayList<>(alignment.size());
        for (Sequence sequence : alignment.sequences()) {
            StringBuilder data = new StringBuilder(length);
            String source = sequence.data();
            for (int column : columns) {
                data.append(source.charAt(column));
            }
            resampled.add(new Sequence(sequence.name(), data.toString()));
        }
        DistanceMatrix matrix = DistanceCalculator.computeLenient(new Alignment(resampled, length));
        return NeighborJoining.build(matrix);
    }

    /**
     * Builds a single replicate tree deterministically from (seed, replicateIndex).
     * Used by the service layer for crash-recovery scheduling.
     */
    public static TreeNode buildSingleReplicate(Alignment alignment, long seed, int replicate) {
        return buildReplicate(alignment, seed, replicate);
    }

    /** NJ tree of the original alignment, used as the consensus reference topology. */
    public static TreeNode referenceTree(Alignment alignment) {
        return NeighborJoining.build(DistanceCalculator.compute(alignment));
    }

    /**
     * Assembles the final result from a complete, index-ordered list of replicate trees
     * (also used after crash recovery, so resumed results equal uninterrupted results).
     */
    public static BootstrapResult assembleResult(Alignment alignment, List<TreeNode> orderedTrees) {
        TreeNode reference = referenceTree(alignment);
        return new BootstrapResult(ConsensusBuilder.annotate(reference, orderedTrees),
                List.copyOf(orderedTrees));
    }

    /**
     * Per-replicate deterministic stream: replicate r uses Random(seed ^ mix(r)),
     * so results depend only on (seed, replicate index) and never on execution order.
     */
    private static Random replicateRandom(long seed, int replicate) {
        long mixed = (long) replicate;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        mixed = mixed ^ (mixed >>> 31);
        return new Random(seed ^ mixed);
    }

    private static void validate(BootstrapConfig config) {
        if (config.replicates() < 1) {
            throw new IllegalArgumentException("replicates must be >= 1");
        }
        if (config.timeoutSeconds() < 0) {
            throw new IllegalArgumentException("timeoutSeconds must be >= 0");
        }
    }
}
