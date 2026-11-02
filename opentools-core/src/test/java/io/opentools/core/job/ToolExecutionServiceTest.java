package io.opentools.core.job;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.core.workspace.Project;
import io.opentools.core.workspace.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ToolExecutionServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsSuccessfulRunAndManifest() throws Exception {
        Project project = new ProjectService().create(tempDir.resolve("project"), "Test");
        ToolRegistry registry = new ToolRegistry();
        registry.register(new EchoTool(false));
        SqliteJobStore store = new SqliteJobStore(project.databasePath());
        store.initialize();

        try (ToolExecutionService service = new ToolExecutionService(registry, store)) {
            Path input = tempDir.resolve("input.txt");
            Files.writeString(input, "hello");
            JobRecord job = service.runAndWait(
                    project,
                    new ToolRequest("test.echo", Map.of("input", input.toString()))
            );

            assertEquals(JobStatus.SUCCEEDED, job.status());
            assertTrue(Files.isRegularFile(job.runDirectory().resolve("manifest.json")));
            assertTrue(Files.isRegularFile(job.runDirectory().resolve("result.json")));
            assertTrue(Files.isRegularFile(job.runDirectory().resolve("outputs/echo.txt")));
            assertTrue(job.manifestJson().contains("sha256"));
            assertEquals(1, service.recentJobs(10).size());
            ToolRequest copied = service.requestFromJob(job.runId());
            assertEquals("test.echo", copied.toolId());
            String retryId = service.retry(project, job.runId());
            assertEquals("test.echo", service.find(retryId).toolId());
        }
    }

    @Test
    void recordsFailureWithDiagnostic() throws Exception {
        Project project = new ProjectService().create(tempDir.resolve("failed-project"), "Test");
        ToolRegistry registry = new ToolRegistry();
        registry.register(new EchoTool(true));
        SqliteJobStore store = new SqliteJobStore(project.databasePath());
        store.initialize();

        try (ToolExecutionService service = new ToolExecutionService(registry, store)) {
            Path input = tempDir.resolve("input-failed.txt");
            Files.writeString(input, "hello");
            JobRecord job = service.runAndWait(
                    project,
                    new ToolRequest("test.echo", Map.of("input", input.toString()))
            );

            assertEquals(JobStatus.FAILED, job.status());
            assertEquals("TEST_FAILURE", job.errorCode());
            assertTrue(Files.readString(job.runDirectory().resolve("run.log")).contains("TEST_FAILURE"));
        }
    }

    @Test
    void recordsDeclaredDefaultsInStoredParameters() throws Exception {
        Project project = new ProjectService().create(tempDir.resolve("defaults-project"), "Test");
        ToolRegistry registry = new ToolRegistry();
        registry.register(new DefaultsTool());
        SqliteJobStore store = new SqliteJobStore(project.databasePath());
        store.initialize();

        try (ToolExecutionService service = new ToolExecutionService(registry, store)) {
            JobRecord job = service.runAndWait(project, new ToolRequest("test.defaults", Map.of()));

            assertEquals(JobStatus.SUCCEEDED, job.status());
            assertTrue(job.paramsJson().contains("\"mode\""));
            assertTrue(job.paramsJson().contains("\"DEFAULT\""));
            assertEquals("DEFAULT", service.requestFromJob(job.runId()).string("mode"));
        }
    }

    private static final class EchoTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
                "test.echo",
                "Test echo",
                "Creates a deterministic output.",
                "Test",
                List.of(),
                List.of("Text"),
                List.of("Text"),
                "1.0.0",
                List.of(ToolParameter.requiredFile("input", "Input", "Input file"))
        );

        private final boolean fail;

        private EchoTool(boolean fail) {
            this.fail = fail;
        }

        @Override
        public ToolDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public ValidationResult validate(ToolRequest request) {
            return validateParameters(request);
        }

        @Override
        public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
            if (fail) {
                throw new io.opentools.api.tool.OpenToolException(
                        "TEST_FAILURE",
                        "Requested test failure",
                        "No action is required."
                );
            }
            Path output = context.defaultOutputPath("echo.txt");
            Files.createDirectories(output.getParent());
            Files.writeString(output, "ok");
            return ToolResult.success(
                    "Echo completed",
                    "",
                    null,
                    List.of(new OutputArtifact(output, "Echo", "text/plain"))
            );
        }
    }

    private static final class DefaultsTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
                "test.defaults",
                "Defaults",
                "Writes the declared default parameter.",
                "Test",
                List.of(),
                List.of(),
                List.of("Text"),
                "1.0.0",
                List.of(ToolParameter.string("mode", "Mode", "Default mode", "DEFAULT"))
        );

        @Override
        public ToolDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
            Path output = context.defaultOutputPath("default.txt");
            Files.writeString(output, request.string("mode"));
            return ToolResult.success(
                    "Defaults applied",
                    "",
                    null,
                    List.of(new OutputArtifact(output, "Defaults", "text/plain"))
            );
        }
    }
}
