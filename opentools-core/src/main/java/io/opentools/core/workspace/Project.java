package io.opentools.core.workspace;

import java.nio.file.Path;
import java.time.Instant;

public record Project(String id, String name, Path root, Instant createdAt) {
    public Path metadataDirectory() {
        return root.resolve(".opentools");
    }

    public Path databasePath() {
        return metadataDirectory().resolve("project.db");
    }

    public Path dataDirectory() {
        return root.resolve("data");
    }

    public Path runsDirectory() {
        return root.resolve("runs");
    }

    public Path workflowsDirectory() {
        return root.resolve("workflows");
    }

    public Path resultsDirectory() {
        return root.resolve("results");
    }
}
