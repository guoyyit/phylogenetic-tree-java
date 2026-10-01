package com.github.guoyyit.phylogenetictreejava.web;

/**
 * REST request body for submitting an analysis.
 */
public record AnalysisRequest(String fasta, Integer replicates, Long seed, Integer concurrency,
                              Long timeoutSeconds) {
}
