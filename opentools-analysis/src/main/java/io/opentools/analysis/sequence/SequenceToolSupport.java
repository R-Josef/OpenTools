package io.opentools.analysis.sequence;

import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.Compression;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class SequenceToolSupport {
    private SequenceToolSupport() {
    }

    static Path outputPath(ToolRequest request, ToolExecutionContext context, String defaultName) {
        Path output = request.path("output");
        return output == null || output.toString().isBlank()
                ? context.defaultOutputPath(defaultName)
                : output;
    }

    static ValidationResult validateOutput(ToolRequest request, Path input) {
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

    static void writeAtomically(
            Path output,
            Path report,
            StreamAction action
    ) throws Exception {
        createParent(output);
        createParent(report);
        Path temporaryOutput = temporarySibling(output);
        Path temporaryReport = temporarySibling(report);
        try {
            try (BufferedWriter outputWriter = Compression.createWriter(temporaryOutput);
                 BufferedWriter reportWriter = Compression.createWriter(temporaryReport)) {
                action.write(outputWriter, reportWriter);
            }
            replace(temporaryOutput, output);
            replace(temporaryReport, report);
        } finally {
            Files.deleteIfExists(temporaryOutput);
            Files.deleteIfExists(temporaryReport);
        }
    }

    static List<String> parseIds(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return List.of();
        }
        for (String line : text.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            for (String value : trimmed.split("[,;\\s]+")) {
                if (!value.isBlank()) {
                    result.add(value);
                }
            }
        }
        return List.copyOf(result);
    }

    static String percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return "0.00";
        }
        return String.format(Locale.ROOT, "%.2f", numerator * 100.0 / denominator);
    }

    static String escapeTsv(String value) {
        return value == null
                ? ""
                : value.replace("\\", "\\\\")
                        .replace("\t", "\\t")
                        .replace("\r", "\\r")
                        .replace("\n", "\\n");
    }

    static String codepoint(char value) {
        return String.format(Locale.ROOT, "U+%04X", (int) value);
    }

    static void createParent(Path output) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    interface StreamAction {
        void write(BufferedWriter output, BufferedWriter report) throws Exception;
    }

    private static Path temporarySibling(Path target) {
        Path absolute = target.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        String suffix = "-" + absolute.getFileName();
        return parent.resolve(".opentools-" + UUID.randomUUID() + suffix);
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
