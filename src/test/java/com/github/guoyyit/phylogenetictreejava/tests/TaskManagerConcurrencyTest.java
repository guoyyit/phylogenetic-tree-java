package com.github.guoyyit.phylogenetictreejava.tests;

import com.github.guoyyit.phylogenetictreejava.model.AlignmentInput;
import com.github.guoyyit.phylogenetictreejava.service.AnalysisConfig;
import com.github.guoyyit.phylogenetictreejava.service.AnalysisResult;
import com.github.guoyyit.phylogenetictreejava.service.TaskInfo;
import com.github.guoyyit.phylogenetictreejava.service.TaskManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskManagerConcurrencyTest {

    private List<AlignmentInput> inputs(long variant) {
        List<AlignmentInput> inputs = new ArrayList<>();
        String[] bases = {"ACGTACGTACGT", "ACGTACGTACGA", "TCGTTCGTTCGA",
                "TCGTTTGTTCGA", "TCGTTCGTTTGA"};
        for (int i = 0; i < bases.length; i++) {
            String sequence = bases[i];
            if (variant == 1 && i == 0) {
                sequence = "ACGTACGTACGG";
            }
            inputs.add(new AlignmentInput("seq" + i, sequence));
        }
        return inputs;
    }

    private AnalysisConfig config(Path dir, int replicates, long seed,
                                  long timeoutMillis) {
        return new AnalysisConfig(replicates, seed, 4, timeoutMillis, dir);
    }

    private AnalysisResult await(TaskManager manager, String taskId)
            throws InterruptedException {
        for (int i = 0; i < 200; i++) {
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

    @Test
    void multipleTasksRunConcurrentlyWithoutInterfering(@TempDir Path tempDir)
            throws Exception {
        TaskManager manager = new TaskManager(tempDir, 4);

        String firstId = manager.submit(inputs(0),
                config(tempDir.resolve("a"), 50, 9L, 0L));
        String secondId = manager.submit(inputs(1),
                config(tempDir.resolve("b"), 50, 9L, 0L));

        assertNotEquals(firstId, secondId);
        AnalysisResult first = await(manager, firstId);
        AnalysisResult second = await(manager, secondId);

        assertEquals(50, first.bootstrapNewick().split(";").length - 1);
        assertEquals(50, second.bootstrapNewick().split(";").length - 1);
    }

    @Test
    void cancelledTaskWritesNoFinalResult(@TempDir Path tempDir) throws Exception {
        TaskManager manager = new TaskManager(tempDir, 2);
        String taskId = manager.submit(inputs(0),
                config(tempDir, 5, 123L, 0L));
        manager.cancel(taskId);

        for (int i = 0; i < 50; i++) {
            TaskInfo info = manager.getStatus(taskId);
            if (info != null && info.state() == TaskInfo.State.CANCELLED) {
                break;
            }
            Thread.sleep(10);
        }

        assertTrue(manager.getResult(taskId).isEmpty());
        assertTrue(Files.list(tempDir.resolve(taskId))
                .noneMatch(path -> path.getFileName().toString()
                        .equals("consensus.nwk")));
    }

    @Test
    void timeoutLeavesNoPartialFinalOutput(@TempDir Path tempDir)
            throws Exception {
        TaskManager manager = new TaskManager(tempDir, 1);
        String taskId = manager.submit(inputs(0),
                config(tempDir, 100_000, 1L, 1L));

        TaskInfo.State terminal = null;
        for (int i = 0; i < 200; i++) {
            TaskInfo info = manager.getStatus(taskId);
            if (info != null && info.state() != TaskInfo.State.RUNNING) {
                terminal = info.state();
                break;
            }
            Thread.sleep(20);
        }

        assertEquals(TaskInfo.State.TIMEOUT, terminal);
        assertTrue(manager.getResult(taskId).isEmpty());
        assertTrue(Files.list(tempDir.resolve(taskId))
                .noneMatch(path -> path.getFileName().toString()
                        .equals("consensus.nwk")));
    }
}
