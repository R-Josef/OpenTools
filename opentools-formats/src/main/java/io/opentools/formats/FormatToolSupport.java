package io.opentools.formats;

import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FormatToolSupport {
    private FormatToolSupport() {
    }

    public static Path outputPath(ToolRequest request, ToolExecutionContext context, String defaultName) {
        Path output = request.path("output");
        return output == null || output.toString().isBlank()
                ? context.defaultOutputPath(defaultName)
                : output;
    }

    public static ValidationResult validateOutput(ToolRequest request, Path input) {
        Path output = request.path("output");
        if (output == null || output.toString().isBlank()) {
            return ValidationResult.ok();
        }
        List<ValidationIssue> issues = new ArrayList<>();
        Path normalizedOutput = output.toAbsolutePath().normalize();
        Path normalizedInput = input == null ? null : input.toAbsolutePath().normalize();
        if (normalizedOutput.equals(normalizedInput)) {
            issues.add(ValidationIssue.error(
                    "output",
                    "Output must not replace the input file.",
                    "Choose a different output path."
            ));
        }
        if (Files.exists(output) && !request.bool("overwrite")) {
            issues.add(ValidationIssue.error(
                    "output",
                    "Output file already exists: " + output,
                    "Choose another path or enable overwrite."
            ));
        }
        return new ValidationResult(issues);
    }

    public static void writeAtomically(Path output, StreamAction action) throws Exception {
        createParent(output);
        Path temporary = temporarySibling(output);
        try {
            try (BufferedWriter writer = Compression.createWriter(temporary)) {
                action.write(writer);
            }
            replace(temporary, output);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static void writePairAtomically(
            Path primary,
            Path secondary,
            PairAction action
    ) throws Exception {
        createParent(primary);
        createParent(secondary);
        Path temporaryPrimary = temporarySibling(primary);
        Path temporarySecondary = temporarySibling(secondary);
        try {
            try (BufferedWriter primaryWriter = Compression.createWriter(temporaryPrimary);
                 BufferedWriter secondaryWriter = Compression.createWriter(temporarySecondary)) {
                action.write(primaryWriter, secondaryWriter);
            }
            replace(temporarySecondary, secondary);
            replace(temporaryPrimary, primary);
        } finally {
            Files.deleteIfExists(temporaryPrimary);
            Files.deleteIfExists(temporarySecondary);
        }
    }

    public static void createParent(Path output) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    public static String sanitizeFileName(String value) {
        String sanitized = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]+", "_");
        return sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")
                ? "annotation"
                : sanitized;
    }

    private static Path temporarySibling(Path target) {
        Path absolute = target.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        return parent.resolve(".opentools-" + UUID.randomUUID() + "-" + absolute.getFileName());
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @FunctionalInterface
    public interface StreamAction {
        void write(BufferedWriter writer) throws Exception;
    }

    @FunctionalInterface
    public interface PairAction {
        void write(BufferedWriter primary, BufferedWriter secondary) throws Exception;
    }
}
