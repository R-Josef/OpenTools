package io.opentools.core.runtime;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AppPaths {
    private AppPaths() {
    }

    /**
     * Resolves the application resource root for generic and jpackage layouts.
     * A configured property always wins; otherwise the launcher JAR location is
     * preferred over the caller's working directory.
     */
    public static Path applicationHome() {
        String configured = System.getProperty("opentools.home");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }

        Path classPath = launcherPath();
        if (classPath != null) {
            Path parent = classPath.getParent();
            if (parent != null) {
                Path root = "app".equalsIgnoreCase(String.valueOf(parent.getFileName()))
                        ? parent.getParent()
                        : parent;
                if (root != null && looksLikeApplicationRoot(root)) {
                    return root.toAbsolutePath().normalize();
                }
            }
        }
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    private static Path launcherPath() {
        String classPath = System.getProperty("java.class.path");
        if (classPath == null || classPath.isBlank()) {
            return null;
        }
        String first = classPath.split(java.util.regex.Pattern.quote(File.pathSeparator))[0];
        try {
            Path path = Path.of(first).toAbsolutePath().normalize();
            return Files.isRegularFile(path) && first.toLowerCase().endsWith(".jar") ? path : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean looksLikeApplicationRoot(Path root) {
        return Files.isDirectory(root.resolve("lang"))
                || Files.isDirectory(root.resolve("docs"))
                || Files.isDirectory(root.resolve("runtime"))
                || Files.isDirectory(root.resolve("tools"));
    }
}
