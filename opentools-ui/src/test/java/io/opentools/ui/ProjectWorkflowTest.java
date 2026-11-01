package io.opentools.ui;

import io.opentools.api.tool.OpenToolException;
import io.opentools.core.workspace.Project;
import io.opentools.core.workspace.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProjectWorkflowTest {
    @TempDir
    Path tempDir;

    @Test
    void createsOpensAndMaintainsRecentProjectOrder() throws Exception {
        ProjectHistoryStore history = new ProjectHistoryStore(
                tempDir.resolve("settings/recent.properties"), 5);
        ProjectWorkflow workflow = new ProjectWorkflow(new ProjectService(), history);
        Path firstRoot = tempDir.resolve("first-project");
        Path secondRoot = tempDir.resolve("second-project");

        Project first = workflow.create(firstRoot, "First");
        Project second = workflow.create(secondRoot, "Second");
        assertEquals("First", first.name());
        assertEquals("Second", second.name());
        assertEquals(List.of(secondRoot, firstRoot), workflow.recentProjects());

        Project reopened = workflow.open(firstRoot);
        assertEquals(first.id(), reopened.id());
        assertEquals(List.of(firstRoot, secondRoot), workflow.recentProjects());
        assertEquals(
                List.of(firstRoot, secondRoot),
                new ProjectHistoryStore(tempDir.resolve("settings/recent.properties"), 5).load()
        );
        Project startup = OpenToolsGui.startupProject(new ProjectWorkflow(
                new ProjectService(),
                new ProjectHistoryStore(tempDir.resolve("settings/recent.properties"), 5)
        ));
        assertEquals(first.id(), startup.id());
    }

    @Test
    void rejectsExistingProjectCreationAndInvalidOpen() throws Exception {
        ProjectHistoryStore history = new ProjectHistoryStore(
                tempDir.resolve("recent.properties"));
        ProjectWorkflow workflow = new ProjectWorkflow(new ProjectService(), history);
        Path root = tempDir.resolve("existing");
        workflow.create(root, "Existing");

        OpenToolException existing = assertThrows(
                OpenToolException.class,
                () -> workflow.create(root, "Duplicate")
        );
        assertEquals("PROJECT_ALREADY_EXISTS", existing.code());

        Path invalid = tempDir.resolve("not-a-project");
        Files.createDirectories(invalid);
        OpenToolException missing = assertThrows(
                OpenToolException.class,
                () -> workflow.open(invalid)
        );
        assertEquals("PROJECT_NOT_FOUND", missing.code());

        Path corrupt = tempDir.resolve("corrupt-project");
        Files.createDirectories(corrupt);
        Files.writeString(corrupt.resolve("project.json"), "{not-valid-json");
        assertThrows(Exception.class, () -> workflow.open(corrupt));

        assertEquals(List.of(root), workflow.recentProjects());
    }

    @Test
    void malformedHistoryDoesNotBreakStartup() throws Exception {
        Path historyPath = tempDir.resolve("recent.properties");
        Files.writeString(historyPath, "project.0=\\uZZZZ\n");

        List<Path> projects = new ProjectHistoryStore(historyPath).load();

        assertTrue(projects.isEmpty());
    }

    @Test
    void historySkipsOnlyInvalidEntriesAndMaintainsBoundedLruOrder() throws Exception {
        Path first = tempDir.resolve("first");
        Path second = tempDir.resolve("second");
        Path third = tempDir.resolve("third");
        Path historyPath = tempDir.resolve("mixed-history.properties");
        Files.writeString(historyPath,
                "project.0=" + propertyPath(first) + System.lineSeparator()
                        + "project.1=bad\\u0000path" + System.lineSeparator()
                        + "project.2=" + propertyPath(second) + System.lineSeparator()
                        + "project.3=" + propertyPath(third) + System.lineSeparator());

        ProjectHistoryStore store = new ProjectHistoryStore(historyPath, 2);
        assertEquals(List.of(first, second), store.load());

        store.record(third);
        assertEquals(List.of(third, first), store.load());
        store.record(first);
        assertEquals(List.of(first, third), store.load());
    }

    private static String propertyPath(Path path) {
        return path.toString().replace("\\", "\\\\");
    }
}
