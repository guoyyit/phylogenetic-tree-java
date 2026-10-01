package com.github.guoyyit.phylogenetictreejava.web;

import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapConfig;
import com.github.guoyyit.phylogenetictreejava.exception.AlignmentException;
import com.github.guoyyit.phylogenetictreejava.service.AnalysisService;
import com.github.guoyyit.phylogenetictreejava.service.AnalysisTask;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Local-only REST API. Every endpoint works on in-process data or files under
 * the configured working directory; no external service is contacted.
 */
@RestController
@RequestMapping("/api/analyses")
public class PhyloController {

    private final AnalysisService analysisService;

    public PhyloController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping
    public ResponseEntity<?> submit(@RequestBody AnalysisRequest request) {
        try {
            BootstrapConfig config = new BootstrapConfig(
                    request.replicates() == null ? 100 : request.replicates(),
                    request.seed() == null ? 0L : request.seed(),
                    request.concurrency() == null ? 1 : request.concurrency(),
                    request.timeoutSeconds() == null ? 0L : request.timeoutSeconds());
            String taskId = analysisService.submit(request.fasta(), config);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("id", taskId));
        } catch (AlignmentException e) {
            return badRequest(e.getReason().name(), e.getMessage());
        }
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "replicates", required = false)
                                    Integer replicates,
                                    @RequestParam(value = "seed", required = false) Long seed,
                                    @RequestParam(value = "concurrency", required = false)
                                    Integer concurrency,
                                    @RequestParam(value = "timeoutSeconds", required = false)
                                    Long timeoutSeconds) throws IOException {
        try {
            BootstrapConfig config = new BootstrapConfig(
                    replicates == null ? 100 : replicates,
                    seed == null ? 0L : seed,
                    concurrency == null ? 1 : concurrency,
                    timeoutSeconds == null ? 0L : timeoutSeconds);
            String fasta = new String(file.getBytes(), StandardCharsets.UTF_8);
            String taskId = analysisService.submit(fasta, config);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("id", taskId));
        } catch (AlignmentException e) {
            return badRequest(e.getReason().name(), e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> status(@PathVariable String id) {
        return analysisService.status(id)
                .<ResponseEntity<?>>map(task -> {
                    AnalysisTask snapshot = task;
                    int total = snapshot.getConfig() == null ? 0
                            : snapshot.getConfig().replicates();
                    return ResponseEntity.ok(new AnalysisView(snapshot.getId(),
                            snapshot.getStatus().name(), snapshot.getMessage(),
                            snapshot.getCompletedReplicates(), total));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "NOT_FOUND", "message", "no such task: " + id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable String id) {
        if (!analysisService.cancel(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "no running task: " + id));
        }
        return ResponseEntity.ok(Map.of("id", id, "status", "CANCELLED"));
    }

    @GetMapping("/{id}/output/{file:consensus\\.nwk|bootstrap-trees\\.nwk}")
    public ResponseEntity<?> output(@PathVariable String id, @PathVariable String file) {
        if (analysisService.status(id).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", "no such task: " + id));
        }
        try {
            String content = analysisService.getStore().readOutput(id, file);
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
                    .body(content);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "OUTPUT_NOT_READY", "message", e.getMessage()));
        }
    }

    private static ResponseEntity<Map<String, String>> badRequest(String reason, String message) {
        return ResponseEntity.badRequest().body(Map.of("error", reason, "message", message));
    }
}
