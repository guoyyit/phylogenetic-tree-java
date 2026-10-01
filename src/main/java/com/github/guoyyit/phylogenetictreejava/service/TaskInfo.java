package com.github.guoyyit.phylogenetictreejava.service;

/**
 * Observable status of one analysis task.
 */
public final class TaskInfo {

    public enum State {
        RUNNING,
        COMPLETED,
        CANCELLED,
        FAILED,
        TIMEOUT
    }

    private final String taskId;
    private final State state;
    private final int completedReplicates;
    private final int totalReplicates;
    private final String errorMessage;

    public TaskInfo(String taskId, State state, int completedReplicates,
                    int totalReplicates, String errorMessage) {
        this.taskId = taskId;
        this.state = state;
        this.completedReplicates = completedReplicates;
        this.totalReplicates = totalReplicates;
        this.errorMessage = errorMessage;
    }

    public String taskId() {
        return taskId;
    }

    public State state() {
        return state;
    }

    public int completedReplicates() {
        return completedReplicates;
    }

    public int totalReplicates() {
        return totalReplicates;
    }

    public String errorMessage() {
        return errorMessage;
    }
}
