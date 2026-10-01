package com.github.guoyyit.phylogenetictreejava.service;

/**
 * Final, immutable result of one analysis.
 */
public final class AnalysisResult {

    private final String taskId;
    private final String consensusNewick;
    private final String bootstrapNewick;

    public AnalysisResult(String taskId, String consensusNewick, String bootstrapNewick) {
        this.taskId = taskId;
        this.consensusNewick = consensusNewick;
        this.bootstrapNewick = bootstrapNewick;
    }

    public String taskId() {
        return taskId;
    }

    public String consensusNewick() {
        return consensusNewick;
    }

    public String bootstrapNewick() {
        return bootstrapNewick;
    }
}
