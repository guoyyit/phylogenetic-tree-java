package com.github.guoyyit.phylogenetictreejava.web;

import com.github.guoyyit.phylogenetictreejava.model.AlignmentInput;
import com.github.guoyyit.phylogenetictreejava.model.InputValidationException;
import com.github.guoyyit.phylogenetictreejava.service.AnalysisConfig;
import com.github.guoyyit.phylogenetictreejava.service.TaskInfo;
import com.github.guoyyit.phylogenetictreejava.service.TaskManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Local-only REST surface over {@link TaskManager}.
 */
@RestController
@RequestMapping("/api/analyses")
public final class AnalysisController {

    private final TaskManager taskManager;

    public AnalysisController(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @PostMapping
    public ResponseEntity<AnalysisRequests.SubmitResponse> submit(
            @RequestBody(required = false) AnalysisRequests.SubmitRequest request) {
        if (request == null || request.sequences() == null
                || request.sequences().isEmpty()) {
            throw new InputValidationException(
                    InputValidationException.Reason.EMPTY_INPUT,
                    "request body must contain at least two sequences");
        }
        List<AlignmentInput> inputs = new ArrayList<>(request.sequences().size());
        for (AnalysisRequests.SequenceDto sequence : request.sequences()) {
            if (sequence == null) {
                throw new InputValidationException(
                        InputValidationException.Reason.EMPTY_INPUT,
                        "sequence entries must not be null");
            }
            inputs.add(new AlignmentInput(sequence.name(), sequence.sequence()));
        }

        int replicates = request.replicates() == null ? 100 : request.replicates();
        long seed = request.seed() == null ? 0L : request.seed();
        int concurrency = request.concurrency() == null
                ? Runtime.getRuntime().availableProcessors()
                : request.concurrency();
        long timeoutMillis = request.timeoutMillis() == null
                ? 0L : request.timeoutMillis();
        if (replicates < 0 || concurrency < 1) {
            throw new IllegalArgumentException(
                    "replicates must be >= 0 and concurrency must be >= 1");
        }

        AnalysisConfig config =
                new AnalysisConfig(replicates, seed, concurrency, timeoutMillis, null);
        String taskId = taskManager.submit(inputs, config);
        return ResponseEntity.accepted()
                .body(new AnalysisRequests.SubmitResponse(taskId));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<AnalysisRequests.StatusResponse> status(
            @PathVariable String taskId) {
        TaskInfo info = taskManager.getStatus(taskId);
        if (info == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new AnalysisRequests.StatusResponse(
                info.taskId(), info.state().name(), info.completedReplicates(),
                info.totalReplicates(), info.errorMessage()));
    }

    @GetMapping("/{taskId}/result")
    public ResponseEntity<AnalysisRequests.ResultResponse> result(
            @PathVariable String taskId) {
        return taskManager.getResult(taskId)
                .map(result -> ResponseEntity.ok(new AnalysisRequests.ResultResponse(
                        result.taskId(), result.consensusNewick(),
                        result.bootstrapNewick())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String taskId) {
        return taskManager.cancel(taskId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
