package io.opentools.external.annotation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Locates the MEME Suite data directory and supplies resources needed by the
 * legacy Cygwin/Windows MEME builds.
 */
final class MemeRuntimeSupport {
    private MemeRuntimeSupport() {
    }

    static Map<String, String> environment(Path executable) {
        Path dataDirectory = findDataDirectory(executable);
        return dataDirectory == null
                ? Map.of()
                : Map.of("MEME_DATA_DIR", dataDirectory.toString());
    }

    static void copyTemplateIfAvailable(Path executable, Path workDirectory) {
        Path dataDirectory = findDataDirectory(executable);
        if (dataDirectory == null) {
            return;
        }
        Path template = dataDirectory.resolve("template.eps");
        if (!Files.isRegularFile(template)) {
            return;
        }
        try {
            Files.copy(template, workDirectory.resolve("template.eps"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // The tool will report the missing resource if it cannot continue.
        }
    }

    private static Path findDataDirectory(Path executable) {
        for (String variable : new String[]{"MEME_DATA_DIR", "MEME_HOME"}) {
            String configured = System.getenv(variable);
            if (configured == null || configured.isBlank()) {
                continue;
            }
            Path root = Path.of(configured);
            for (Path candidate : new Path[]{
                    root,
                    root.resolve("etc"),
                    root.resolve("share")
            }) {
                Path found = firstDataDirectory(candidate);
                if (found != null) {
                    return found;
                }
            }
        }
        Path parent = executable == null ? null : executable.toAbsolutePath().getParent();
        if (parent == null) {
            return null;
        }
        Path[] candidates = {
                parent.resolve("..").resolve("share").normalize(),
                parent.resolve("..").resolve("etc").normalize(),
                parent.resolve("..").resolve("share").resolve("meme-5.5.9").normalize()
        };
        for (Path candidate : candidates) {
            Path found = firstDataDirectory(candidate);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Path firstDataDirectory(Path candidate) {
        if (candidate == null || !Files.isDirectory(candidate)) {
            return null;
        }
        if (Files.isRegularFile(candidate.resolve("template.eps"))) {
            return candidate;
        }
        try (Stream<Path> children = Files.list(candidate)) {
            return children
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("meme-"))
                    .filter(path -> Files.isRegularFile(path.resolve("template.eps")))
                    .sorted(Comparator.comparing(Path::toString).reversed())
                    .findFirst()
                    .orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }
}
