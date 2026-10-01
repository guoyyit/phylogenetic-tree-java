package com.github.guoyyit.phylogenetictreejava.web;

import com.github.guoyyit.phylogenetictreejava.io.FastaParser;
import com.github.guoyyit.phylogenetictreejava.model.AlignmentInput;
import com.github.guoyyit.phylogenetictreejava.service.AnalysisConfig;
import com.github.guoyyit.phylogenetictreejava.service.TaskManager;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Accepts a local FASTA file upload and starts an analysis. The file is
 * parsed in-process; nothing is sent to an external service.
 */
@RestController
public final class FastaUploadController {

    private final TaskManager taskManager;
    private final FastaParser fastaParser = new FastaParser();

    public FastaUploadController(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @PostMapping(value = "/api/analyses/fasta",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnalysisRequests.SubmitResponse> uploadFasta(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "replicates", defaultValue = "100") int replicates,
            @RequestParam(value = "seed", defaultValue = "0") long seed,
            @RequestParam(value = "concurrency",
                    defaultValue = "4") int concurrency,
            @RequestParam(value = "timeoutMillis", defaultValue = "0")
            long timeoutMillis) throws IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        List<AlignmentInput> inputs = fastaParser.parse(content);
        AnalysisConfig config = new AnalysisConfig(
                replicates, seed, concurrency, timeoutMillis, null);
        String taskId = taskManager.submit(inputs, config);
        return ResponseEntity.accepted()
                .body(new AnalysisRequests.SubmitResponse(taskId));
    }
}
