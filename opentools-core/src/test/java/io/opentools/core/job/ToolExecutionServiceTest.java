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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
            String resultJson = Files.readString(job.runDirectory().resolve("result.json"));
            assertTrue(resultJson.contains("\"summaryKey\" : \"result.test.echo.summary\""));
            assertTrue(resultJson.contains("\"detailKey\" : \"result.test.echo.detail\""));
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

    @Test
    void cancelWhileQueuedMarksJobCancelledAndFreesTheSlot() throws Exception {
        String previous = System.getProperty("opentools.jobs.maxConcurrent");
        System.setProperty("opentools.jobs.maxConcurrent", "1");
        try {
            Project project = new ProjectService().create(tempDir.resolve("queue-project"), "Test");
            ToolRegistry registry = new ToolRegistry();
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            registry.register(new BlockingTool(started, release));
            SqliteJobStore store = new SqliteJobStore(project.databasePath());
            store.initialize();

            try (ToolExecutionService service = new ToolExecutionService(registry, store)) {
                String first = service.submit(project, new ToolRequest("test.block", Map.of()));
                assertTrue(started.await(5, TimeUnit.SECONDS));

                String second = service.submit(project, new ToolRequest("test.block", Map.of()));
                assertEquals(JobStatus.QUEUED, service.find(second).status());

                assertTrue(service.cancel(second));
                assertEquals(JobStatus.CANCELLED, service.find(second).status());

                release.countDown();
                JobRecord finished = awaitTerminal(service, first);
                assertEquals(JobStatus.SUCCEEDED, finished.status());
            }
        } finally {
            if (previous == null) {
                System.clearProperty("opentools.jobs.maxConcurrent");
            } else {
                System.setProperty("opentools.jobs.maxConcurrent", previous);
            }
        }
    }

    @Test
    void observesCancellationRequestedByAnotherProcess() throws Exception {
        Project project = new ProjectService().create(tempDir.resolve("cross-process-project"), "Test");
        ToolRegistry registry = new ToolRegistry();
        CountDownLatch started = new CountDownLatch(1);
        registry.register(new InterruptibleTool(started));
        SqliteJobStore store = new SqliteJobStore(project.databasePath());
        store.initialize();

        try (ToolExecutionService owner = new ToolExecutionService(registry, store)) {
            String runId = owner.submit(project, new ToolRequest("test.interruptible", Map.of()));
            assertTrue(started.await(5, TimeUnit.SECONDS));

            try (ToolExecutionService other = new ToolExecutionService(new ToolRegistry(), store)) {
                assertTrue(other.cancel(runId));
                JobRecord cancelled = awaitTerminal(owner, runId);
                assertEquals(JobStatus.CANCELLED, cancelled.status());
                assertEquals("CANCELLED", cancelled.errorCode());
            }
        }
    }

    @Test
    void reportsProgressWhileTheJobRuns() throws Exception {
        Project project = new ProjectService().create(tempDir.resolve("progress-project"), "Test");
        ToolRegistry registry = new ToolRegistry();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        registry.register(new BlockingTool(started, release));
        SqliteJobStore store = new SqliteJobStore(project.databasePath());
        store.initialize();

        try (ToolExecutionService service = new ToolExecutionService(registry, store)) {
            String runId = service.submit(project, new ToolRequest("test.block", Map.of()));
            assertTrue(started.await(5, TimeUnit.SECONDS));

            JobRecord running = awaitProgress(service, runId, 0.5);
            assertEquals(JobStatus.RUNNING, running.status());
            assertEquals(0.5, running.progress(), 0.001);
            assertEquals("Halfway", running.progressMessage());

            release.countDown();
            assertEquals(JobStatus.SUCCEEDED, awaitTerminal(service, runId).status());
        }
    }

    @Test
    void recoversStaleRunningJobsOnInitialize() throws Exception {
        String previous = System.getProperty("opentools.jobs.staleAfterSeconds");
        System.setProperty("opentools.jobs.staleAfterSeconds", "1");
        try {
            Project project = new ProjectService().create(tempDir.resolve("recovery-project"), "Test");
            SqliteJobStore store = new SqliteJobStore(project.databasePath());
            store.initialize();
            Path runDirectory = Files.createDirectories(project.runsDirectory().resolve("stale-run"));
            store.create(new JobRecord(
                    "stale-run", "test.echo", "1.0.0", JobStatus.RUNNING,
                    Instant.now().minusSeconds(30), Instant.now().minusSeconds(30), null,
                    0.4, "Halfway", "{}", null, null, null, runDirectory
            ));
            Thread.sleep(1_200);
            store.initialize();

            JobRecord recovered = store.find("stale-run").orElseThrow();
            assertEquals(JobStatus.FAILED, recovered.status());
            assertEquals("JOB_INTERRUPTED", recovered.errorCode());
        } finally {
            if (previous == null) {
                System.clearProperty("opentools.jobs.staleAfterSeconds");
            } else {
                System.setProperty("opentools.jobs.staleAfterSeconds", previous);
            }
        }
    }

    private static JobRecord awaitTerminal(ToolExecutionService service, String runId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        JobRecord job = service.find(runId);
        while (!List.of(JobStatus.SUCCEEDED, JobStatus.FAILED, JobStatus.CANCELLED, JobStatus.PARTIAL)
                .contains(job.status()) && System.nanoTime() < deadline) {
            Thread.sleep(25);
            job = service.find(runId);
        }
        return job;
    }

    private static JobRecord awaitProgress(ToolExecutionService service, String runId, double minimum)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        JobRecord job = service.find(runId);
        while (job.progress() < minimum && System.nanoTime() < deadline) {
            Thread.sleep(25);
            job = service.find(runId);
        }
        return job;
    }

    private static final class InterruptibleTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
                "test.interruptible",
                "Test interruptible",
                "Waits until its cancellation token is triggered.",
                "Test",
                List.of(),
                List.of(),
                List.of("Text"),
                "1.0.0",
                List.of()
        );

        private final CountDownLatch started;

        private InterruptibleTool(CountDownLatch started) {
            this.started = started;
        }

        @Override
        public ToolDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
            started.countDown();
            while (!context.cancellationToken().isCancelled()) {
                Thread.sleep(25);
            }
            context.cancellationToken().throwIfCancelled();
            return ToolResult.success("Interrupted task completed", "", null, List.of());
        }
    }

    private static final class BlockingTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
                "test.block",
                "Test block",
                "Reports progress and waits for a release signal.",
                "Test",
                List.of(),
                List.of(),
                List.of("Text"),
                "1.0.0",
                List.of()
        );

        private final CountDownLatch started;
        private final CountDownLatch release;

        private BlockingTool(CountDownLatch started, CountDownLatch release) {
            this.started = started;
            this.release = release;
        }

        @Override
        public ToolDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
            context.progress(0.5, "Halfway");
            started.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new io.opentools.api.tool.OpenToolException(
                        "TEST_TIMEOUT", "Release signal was not received.", "Check the test wiring.");
            }
            context.cancellationToken().throwIfCancelled();
            Path output = context.defaultOutputPath("block.txt");
            Files.writeString(output, "ok");
            return ToolResult.success(
                    "Blocking task completed", "", null,
                    List.of(new OutputArtifact(output, "Block", "text/plain")));
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
