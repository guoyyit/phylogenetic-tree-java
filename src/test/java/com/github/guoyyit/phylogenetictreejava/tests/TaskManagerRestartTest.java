package com.github.guoyyit.phylogenetictreejava.tests;

import com.github.guoyyit.phylogenetictreejava.bootstrap.FileCheckpointStore;
import com.github.guoyyit.phylogenetictreejava.model.AlignmentInput;
import com.github.guoyyit.phylogenetictreejava.service.AnalysisConfig;
import com.github.guoyyit.phylogenetictreejava.service.AnalysisResult;
import com.github.guoyyit.phylogenetictreejava.service.TaskInfo;
import com.github.guoyyit.phylogenetictreejava.service.TaskManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskManagerRestartTest {

    private List<AlignmentInput> inputs() {
        String[] bases = {"ACGTACGTACGTAA", "ACGTACGTACGATA",
                "TCGTTCGTTCGATA", "TCGTTTGTTCGATA", "TCGTTCGTTTGATA"};
        String[] names = {"alpha", "bravo", "charlie", "delta", "echo"};
        List<AlignmentInput> inputs = new ArrayList<>();
        for (int i = 0; i < bases.length; i++) {
            inputs.add(new AlignmentInput(names[i], bases[i]));
        }
        return inputs;
    }

    @Test
    void resubmittingAfterRestartResumesAndProducesIdenticalResult(
            @TempDir Path tempDir) throws Exception {
        List<AlignmentInput> inputs = inputs();
        AnalysisConfig config = new AnalysisConfig(40, 77L, 4, 0L, null);

        TaskManager firstManager = new TaskManager(tempDir, 4);
        String uninterruptedId = firstManager.submit(inputs, config);
        AnalysisResult uninterrupted = await(firstManager, uninterruptedId);

        String firstId = uninterruptedId;
        Path replicateDirectory = tempDir.resolve(firstId).resolve("replicates");
        try (var stream = java.nio.file.Files.list(replicateDirectory)) {
            stream.filter(path -> path.getFileName().toString()
                            .matches("replicate-0(001[5-9]|00[2-9]\\d)\\.nwk"))
                    .forEach(path -> {
                        try {
                            java.nio.file.Files.delete(path);
                        } catch (java.io.IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        java.nio.file.Files.deleteIfExists(
                tempDir.resolve(firstId).resolve("consensus.nwk"));
        java.nio.file.Files.deleteIfExists(
                tempDir.resolve(firstId).resolve("bootstrap-trees.nwk"));

        TaskManager restartedManager = new TaskManager(tempDir, 4);
        String resumedId = restartedManager.submit(inputs, config);
        assertEquals(firstId, resumedId,
                "identical input and config must map to the same task id");
        AnalysisResult resumed = await(restartedManager, resumedId);

        assertEquals(uninterrupted.consensusNewick(), resumed.consensusNewick());
        assertEquals(uninterrupted.bootstrapNewick(), resumed.bootstrapNewick());
        assertTrue(restartedManager.getStatus(resumedId).state()
                == TaskInfo.State.COMPLETED);
    }

    private AnalysisResult await(TaskManager manager, String taskId)
            throws InterruptedException {
        for (int i = 0; i < 300; i++) {
            Optional<AnalysisResult> result = manager.getResult(taskId);
            if (result.isPresent()) {
                return result.get();
            }
            TaskInfo info = manager.getStatus(taskId);
            if (info != null && info.state() != TaskInfo.State.RUNNING) {
                throw new AssertionError(
                        "task ended in state " + info.state() + ": "
                                + info.errorMessage());
            }
            Thread.sleep(20);
        }
        throw new AssertionError("task did not finish in time");
    }
}
