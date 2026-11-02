package io.opentools.ui;

import io.opentools.core.job.SqliteJobStore;
import io.opentools.core.job.ToolExecutionService;
import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.api.tool.ToolRequest;
import io.opentools.core.workspace.Project;
import io.opentools.core.workspace.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UiSessionProjectSwitchTest {
    @TempDir
    Path tempDir;

    @Test
    void blocksSwitchWhileAJobRunsThenRebindsExecution() throws Exception {
        ProjectService projects = new ProjectService();
        Project firstProject = projects.create(tempDir.resolve("first"), "First");
        Project secondProject = projects.create(tempDir.resolve("second"), "Second");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ToolRegistry registry = new ToolRegistry();
        registry.register(new BlockingTool(started, release));
        SqliteJobStore store = new SqliteJobStore(firstProject.databasePath());
        store.initialize();
        ToolExecutionService execution = new ToolExecutionService(registry, store);
        OpenToolsGui.UiSession session = new OpenToolsGui.UiSession(
                registry, firstProject, execution, null, null);
        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                execution.runAndWait(firstProject, new ToolRequest("test.blocking", Map.of()));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));

        assertThrows(IllegalStateException.class, () -> session.switchProject(secondProject));

        release.countDown();
        worker.join(5_000);
        ToolExecutionService previous = session.execution();
        session.switchProject(secondProject);
        assertEquals(secondProject.id(), session.project().id());
        assertNotSame(previous, session.execution());
        session.execution().close();
    }

    @Test
    void failedSwitchLeavesTheCurrentProjectAndExecutionIntact() throws Exception {
        ProjectService projects = new ProjectService();
        Project firstProject = projects.create(tempDir.resolve("stable"), "Stable");
        ToolRegistry registry = new ToolRegistry();
        SqliteJobStore store = new SqliteJobStore(firstProject.databasePath());
        store.initialize();
        ToolExecutionService execution = new ToolExecutionService(registry, store);
        OpenToolsGui.UiSession session = new OpenToolsGui.UiSession(
                registry, firstProject, execution, null, null);

        Path invalidRoot = tempDir.resolve("invalid-project");
        Project invalid = new Project(
                "invalid",
                "Invalid",
                invalidRoot,
                Instant.now()
        );
        Files.createDirectories(invalid.databasePath());
        ToolExecutionService previousExecution = session.execution();

        assertThrows(Exception.class, () -> session.switchProject(invalid));
        assertEquals(firstProject.id(), session.project().id());
        assertTrue(previousExecution == session.execution());
        previousExecution.close();
    }

    private static final class BlockingTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
                "test.blocking",
                "Blocking test tool",
                "Blocks until a test releases it.",
                "Test",
                List.of(),
                List.of(),
                List.of(),
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
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test latch timed out");
            }
            return new ToolResult(
                    "Completed",
                    List.of(),
                    "Completed",
                    List.of(),
                    new ResultTable(List.of(), List.of()),
                    List.of(),
                    Map.of()
            );
        }
    }
}
