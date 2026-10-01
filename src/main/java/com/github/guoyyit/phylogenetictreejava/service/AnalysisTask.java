package com.github.guoyyit.phylogenetictreejava.service;

import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapConfig;

/**
 * Snapshot of a submitted analysis task.
 */
public final class AnalysisTask {

    private final String id;
    private volatile AnalysisStatus status = AnalysisStatus.QUEUED;
    private volatile String message = "";
    private volatile BootstrapConfig config;
    private volatile int completedReplicates;

    public AnalysisTask(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public void setStatus(AnalysisStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public BootstrapConfig getConfig() {
        return config;
    }

    public void setConfig(BootstrapConfig config) {
        this.config = config;
    }

    public int getCompletedReplicates() {
        return completedReplicates;
    }

    public void setCompletedReplicates(int completedReplicates) {
        this.completedReplicates = completedReplicates;
    }
}
