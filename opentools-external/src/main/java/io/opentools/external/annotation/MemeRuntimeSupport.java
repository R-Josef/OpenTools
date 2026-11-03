package io.opentools.external.annotation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Locates the MEME Suite data directory and supplies resources needed by the
 * legacy Cygwin/Windows MEME builds.
 */
final class MemeRuntimeSupport {
    private MemeRuntimeSupport() {
    }

    static void copyTemplateIfAvailable(Path executable, Path workDirectory) {
        Path template = findTemplate(executable);
        if (template == null) {
            return;
        }
        try {
            Files.copy(template, workDirectory.resolve("template.eps"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // The tool will report the missing resource if it cannot continue.
        }
    }

    private static Path findTemplate(Path executable) {
        Path parent = executable == null ? null : executable.toAbsolutePath().getParent();
        if (parent == null) {
            return null;
        }
        for (String variable : new String[]{"MEME_HOME", "MEME_DATA_DIR"}) {
            String configured = System.getenv(variable);
            if (configured != null && !configured.isBlank()) {
                Path candidate = Path.of(configured).resolve("etc").resolve("template.eps");
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        Path[] candidates = {
                parent.resolve("etc").resolve("template.eps"),
                parent.resolve("../etc").resolve("template.eps").normalize(),
                parent.resolve("../share/meme/etc").resolve("template.eps").normalize(),
                parent.resolve("template.eps")
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
