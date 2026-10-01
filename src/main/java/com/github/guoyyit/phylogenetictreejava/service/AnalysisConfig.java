package com.github.guoyyit.phylogenetictreejava.service;

import java.nio.file.Path;

/**
 * Per-task analysis options.
 *
 * @param replicates    number of bootstrap replicates (>= 0)
 * @param seed          deterministic RNG seed
 * @param concurrency   maximum concurrently executing replicates for this task
 * @param timeoutMillis wall-clock timeout; non-positive means no timeout
 * @param workDirectory directory used for checkpoints and output files
 */
public record AnalysisConfig(int replicates,
                             long seed,
                             int concurrency,
                             long timeoutMillis,
                             Path workDirectory) {

    public static AnalysisConfig defaults(Path workDirectory) {
        return new AnalysisConfig(100, 0L, 1, 0L, workDirectory);
    }
}
