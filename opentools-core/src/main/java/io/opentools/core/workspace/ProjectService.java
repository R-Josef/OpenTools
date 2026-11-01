package io.opentools.core.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.opentools.core.job.SqliteJobStore;
import io.opentools.api.tool.OpenToolException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

public final class ProjectService {
    private static final String PROJECT_FILE = "project.json";

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public Project create(Path projectRoot, String name) throws IOException {
        Path root = projectRoot.toAbsolutePath().normalize();
        if (Files.exists(root.resolve(PROJECT_FILE))) {
            return open(root);
        }

        Files.createDirectories(root.resolve(".opentools"));
        Files.createDirectories(root.resolve("data"));
        Files.createDirectories(root.resolve("runs"));
        Files.createDirectories(root.resolve("workflows"));
        Files.createDirectories(root.resolve("results"));

        ProjectMetadata metadata = new ProjectMetadata(
                UUID.randomUUID().toString(),
                name == null || name.isBlank() ? root.getFileName().toString() : name.trim(),
                Instant.now().toString()
        );
        objectMapper.writeValue(root.resolve(PROJECT_FILE).toFile(), metadata);
        Project project = toProject(root, metadata);
        initializeStore(project);
        return project;
    }

    public Project open(Path projectRoot) throws IOException {
        Path root = projectRoot.toAbsolutePath().normalize();
        Path metadataPath = root.resolve(PROJECT_FILE);
        if (!Files.isRegularFile(metadataPath)) {
            throw new OpenToolException(
                    "PROJECT_NOT_FOUND",
                    "No OpenTools project was found at " + root,
                    "Create a new project or select the project root that contains project.json."
            );
        }
        ProjectMetadata metadata = objectMapper.readValue(metadataPath.toFile(), ProjectMetadata.class);
        Project project = toProject(root, metadata);
        Files.createDirectories(project.runsDirectory());
        initializeStore(project);
        return project;
    }

    public Project defaultProject() throws IOException {
        String configuredHome = System.getenv("OPENTOOLS_HOME");
        Path home = configuredHome == null || configuredHome.isBlank()
                ? Path.of(System.getProperty("user.home"), ".opentools")
                : Path.of(configuredHome);
        Path root = home.resolve("default-project");
        if (Files.isRegularFile(root.resolve(PROJECT_FILE))) {
            return open(root);
        }
        return create(root, "Default Project");
    }

    private Project toProject(Path root, ProjectMetadata metadata) {
        return new Project(metadata.id(), metadata.name(), root, Instant.parse(metadata.createdAt()));
    }

    private static void initializeStore(Project project) throws IOException {
        try {
            new SqliteJobStore(project.databasePath()).initialize();
        } catch (java.sql.SQLException ex) {
            throw new IOException("Could not initialize project history database", ex);
        }
    }

    private record ProjectMetadata(String id, String name, String createdAt) {
        private ProjectMetadata {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Project id is missing");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Project name is missing");
            }
            if (createdAt == null || createdAt.isBlank()) {
                createdAt = Instant.now().toString();
            }
        }
    }
}
