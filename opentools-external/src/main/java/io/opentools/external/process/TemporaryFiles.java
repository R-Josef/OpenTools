package io.opentools.external.process;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class TemporaryFiles {
    private TemporaryFiles() {
    }

    public static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }
}
