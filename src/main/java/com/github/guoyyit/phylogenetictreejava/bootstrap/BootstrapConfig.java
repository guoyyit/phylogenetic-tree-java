package com.github.guoyyit.phylogenetictreejava.bootstrap;

/**
 * Configuration of a bootstrap analysis.
 *
 * @param replicates      number of bootstrap replicates
 * @param seed            random seed for site resampling
 * @param concurrency     worker thread count
 * @param timeoutSeconds  wall-clock timeout; 0 means no timeout
 */
public record BootstrapConfig(int replicates, long seed, int concurrency, long timeoutSeconds) {
}
