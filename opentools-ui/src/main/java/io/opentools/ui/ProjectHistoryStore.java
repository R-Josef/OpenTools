package io.opentools.ui;

import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeMap;

final class ProjectHistoryStore {
    private static final int DEFAULT_LIMIT = 10;

    private final Path path;
    private final int limit;

    ProjectHistoryStore(Path path) {
        this(path, DEFAULT_LIMIT);
    }

    ProjectHistoryStore(Path path, int limit) {
        this.path = path.toAbsolutePath().normalize();
        this.limit = Math.max(1, limit);
    }

    static ProjectHistoryStore defaultStore() {
        String configured = System.getenv("OPENTOOLS_HOME");
        Path home = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home"), ".opentools")
                : Path.of(configured);
        return new ProjectHistoryStore(home.resolve("recent-projects.properties"));
    }

    synchronized List<Path> load() {
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        try {
            Map<Integer, Path> projectsByIndex = new TreeMap<>();
            for (String line : Files.readAllLines(path)) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")
                        || trimmed.startsWith("!")) {
                    continue;
                }
                try {
                    Properties single = new Properties();
                    single.load(new StringReader(line));
                    for (String key : single.stringPropertyNames()) {
                        if (!key.startsWith("project.")) {
                            continue;
                        }
                        int index;
                        try {
                            index = Integer.parseInt(key.substring("project.".length()));
                        } catch (NumberFormatException ignored) {
                            continue;
                        }
                        String value = single.getProperty(key, "").trim();
                        if (index < 0 || value.isEmpty()) {
                            continue;
                        }
                        try {
                            projectsByIndex.put(
                                    index,
                                    Path.of(value).toAbsolutePath().normalize()
                            );
                        } catch (RuntimeException ignored) {
                            // Skip invalid path values without losing other entries.
                        }
                    }
                } catch (IOException | IllegalArgumentException ignored) {
                    // Skip only the malformed line.
                }
            }
            List<Path> projects = new ArrayList<>();
            for (Path project : projectsByIndex.values()) {
                projects.add(project);
                if (projects.size() >= limit) {
                    break;
                }
            }
            return List.copyOf(projects);
        } catch (IOException ex) {
            return List.of();
        }
    }

    synchronized void record(Path projectRoot) throws IOException {
        Path normalized = projectRoot.toAbsolutePath().normalize();
        List<Path> updated = new ArrayList<>();
        updated.add(normalized);
        String key = normalized.toString().toLowerCase(Locale.ROOT);
        for (Path existing : load()) {
            if (!existing.toString().toLowerCase(Locale.ROOT).equals(key)) {
                updated.add(existing);
            }
            if (updated.size() >= limit) {
                break;
            }
        }
        write(updated);
    }

    private void write(List<Path> projects) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Properties properties = new Properties();
        for (int index = 0; index < projects.size(); index++) {
            properties.setProperty("project." + index, projects.get(index).toString());
        }
        Path temporary = Files.createTempFile(parent, "opentools-recent-", ".tmp");
        boolean published = false;
        try {
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                properties.store(writer, "OpenTools recent projects");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
