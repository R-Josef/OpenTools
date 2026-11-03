package io.opentools.mcp;

import io.opentools.core.job.JobStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorkflowServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void runsDependentStepsAndPassesArtifacts() throws Exception {
        Path project = tempDir.resolve("project");
        Files.createDirectories(project);
        Path fasta = project.resolve("sample.fa");
        Files.writeString(fasta, ">seq1\nACGTACGT\n>seq2\nACGT\n");

        try (AutomationRuntime runtime = AutomationRuntime.open(project, tempDir, List.of())) {
            ToolAutomationService.WorkflowSpec spec = new ToolAutomationService.WorkflowSpec(
                    "demo", false, List.of(
                    new ToolAutomationService.WorkflowStep(
                            "stats", "fasta.stats", Map.of("input", fasta.toString()), List.of()),
                    new ToolAutomationService.WorkflowStep(
                            "preview", "table.preview", Map.of("input", "${stats.output}"), List.of("stats"))
            ));
            Map<String, Object> result = runtime.toolAutomation().runWorkflow(spec);

            assertEquals(2, ((Number) result.get("succeeded")).intValue());
            assertEquals(0, ((Number) result.get("failed")).intValue());
            List<?> steps = (List<?>) result.get("steps");
            Map<?, ?> second = (Map<?, ?>) steps.get(1);
            assertEquals("SUCCEEDED", second.get("status"));
            assertTrue(((List<?>) second.get("outputs")).size() >= 1);
        }
    }

    @Test
    void runsWorkflowFromJsonFile() throws Exception {
        Path project = tempDir.resolve("project-file");
        Files.createDirectories(project);
        Path fasta = project.resolve("sample.fa");
        Files.writeString(fasta, ">seq1\nACGTACGT\n");
        Path workflow = project.resolve("workflow.json");
        Files.writeString(workflow, """
                {
                  "name": "file-demo",
                  "steps": [
                    {"id": "stats", "toolId": "fasta.stats", "parameters": {"input": "%s"}},
                    {"id": "preview", "toolId": "table.preview",
                     "parameters": {"input": "${stats.output}"}, "dependsOn": ["stats"]}
                  ]
                }
                """.formatted(fasta.toString().replace("\\", "\\\\")));

        try (AutomationRuntime runtime = AutomationRuntime.open(project, tempDir, List.of())) {
            Map<String, Object> result = runtime.toolAutomation().runWorkflow(workflow);
            assertEquals(2, ((Number) result.get("succeeded")).intValue());
            assertEquals("file-demo", result.get("name"));
        }
    }

    @Test
    void rejectsCycles() throws Exception {
        Path project = tempDir.resolve("project-cycle");
        try (AutomationRuntime runtime = AutomationRuntime.open(project, tempDir, List.of())) {
            ToolAutomationService.WorkflowSpec spec = new ToolAutomationService.WorkflowSpec(
                    "cycle", false, List.of(
                    new ToolAutomationService.WorkflowStep("a", "fasta.stats", Map.of(), List.of("b")),
                    new ToolAutomationService.WorkflowStep("b", "fasta.stats", Map.of(), List.of("a"))
            ));
            AutomationException error = org.junit.jupiter.api.Assertions.assertThrows(
                    AutomationException.class,
                    () -> runtime.toolAutomation().runWorkflow(spec)
            );
            assertEquals("WORKFLOW_CYCLE", error.code());
        }
    }

    @Test
    void stopsAfterFailureUnlessContinueOnError() throws Exception {
        Path project = tempDir.resolve("project-failure");
        try (AutomationRuntime runtime = AutomationRuntime.open(project, tempDir, List.of())) {
            ToolAutomationService.WorkflowSpec stop = new ToolAutomationService.WorkflowSpec(
                    "stop", false, List.of(
                    new ToolAutomationService.WorkflowStep(
                            "bad", "fasta.stats", Map.of("input", project.resolve("missing.fa").toString()), List.of()),
                    new ToolAutomationService.WorkflowStep(
                            "next", "fasta.stats", Map.of("input", project.resolve("missing2.fa").toString()), List.of())
            ));
            Map<String, Object> stopped = runtime.toolAutomation().runWorkflow(stop);
            assertEquals(1, ((Number) stopped.get("failed")).intValue());
            assertEquals(1, ((Number) stopped.get("skipped")).intValue());
            assertTrue((Boolean) stopped.get("stoppedOnError"));

            ToolAutomationService.WorkflowSpec keepGoing = new ToolAutomationService.WorkflowSpec(
                    "continue", true, List.of(
                    new ToolAutomationService.WorkflowStep(
                            "bad", "fasta.stats", Map.of("input", project.resolve("missing.fa").toString()), List.of()),
                    new ToolAutomationService.WorkflowStep(
                            "next", "fasta.stats", Map.of("input", project.resolve("missing2.fa").toString()), List.of())
            ));
            Map<String, Object> continued = runtime.toolAutomation().runWorkflow(keepGoing);
            assertEquals(2, ((Number) continued.get("failed")).intValue());
            assertEquals(0, ((Number) continued.get("skipped")).intValue());
        }
    }
}
