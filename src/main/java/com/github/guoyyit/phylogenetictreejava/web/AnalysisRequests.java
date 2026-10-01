package com.github.guoyyit.phylogenetictreejava.web;

import java.util.List;

/**
 * Local HTTP DTOs. All data travels in-process or from local files; no
 * external endpoints are ever contacted.
 */
public final class AnalysisRequests {

    private AnalysisRequests() {
    }

    public record SequenceDto(String name, String sequence) {
    }

    public record SubmitRequest(List<SequenceDto> sequences,
                                Integer replicates,
                                Long seed,
                                Integer concurrency,
                                Long timeoutMillis) {
    }

    public record SubmitResponse(String taskId) {
    }

    public record StatusResponse(String taskId,
                                 String state,
                                 int completedReplicates,
                                 int totalReplicates,
                                 String errorMessage) {
    }

    public record ResultResponse(String taskId,
                                 String consensusNewick,
                                 String bootstrapNewick) {
    }
}
