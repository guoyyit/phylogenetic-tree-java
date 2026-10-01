package com.github.guoyyit.phylogenetictreejava.web;

/**
 * REST view of a task's current state.
 */
public record AnalysisView(String id, String status, String message, int completedReplicates,
                           int totalReplicates) {
}
