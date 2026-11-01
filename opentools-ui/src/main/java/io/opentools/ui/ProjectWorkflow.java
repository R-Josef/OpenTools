package io.opentools.ui;

import io.opentools.api.tool.OpenToolException;
import io.opentools.core.workspace.Project;
import io.opentools.core.workspace.ProjectService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class ProjectWorkflow {
    static final String PROJECT_METADATA = "project.json";

    private final ProjectService projects;
    private final ProjectHistoryStore history;

    ProjectWorkflow() {
        this(new ProjectService(), ProjectHistoryStore.defaultStore());
    }

    ProjectWorkflow(ProjectService projects, ProjectHistoryStore history) {
        this.projects = projects;
        this.history = history;
    }

    Project create(Path root, String name) throws Exception {
        Path normalized = root.toAbsolutePath().normalize();
        if (Files.isRegularFile(normalized.resolve(PROJECT_METADATA))) {
            throw new OpenToolException(
                    "PROJECT_ALREADY_EXISTS",
                    "An OpenTools project already exists at " + normalized,
                    "Use Open Project to load the existing project."
            );
        }
        if (Files.exists(normalized) && !Files.isDirectory(normalized)) {
            throw new OpenToolException(
                    "PROJECT_PATH_NOT_DIRECTORY",
                    "Project path is not a directory: " + normalized,
                    "Choose an existing directory or a new directory path."
            );
        }
        Project project = projects.create(normalized, name);
        history.record(project.root());
        return project;
    }

    Project open(Path root) throws Exception {
        Path normalized = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new OpenToolException(
                    "PROJECT_NOT_FOUND",
                    "Project directory does not exist: " + normalized,
                    "Choose the root directory that contains project.json."
            );
        }
        Project project = projects.open(normalized);
        history.record(project.root());
        return project;
    }

    Project defaultProject() throws Exception {
        Project project = projects.defaultProject();
        history.record(project.root());
        return project;
    }

    List<Path> recentProjects() {
        return history.load();
    }
}
