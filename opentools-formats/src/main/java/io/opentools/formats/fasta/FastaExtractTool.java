package io.opentools.formats.fasta;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FastaExtractTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "fasta.extract",
            "Extract FASTA by ID",
            "Extract complete FASTA records using an ID list.",
            "Sequence",
            List.of("fasta extract", "extract fasta", "按 ID 提取", "FASTA 提取"),
            List.of("FASTA", "FASTA.gz", "Text ID list"),
            List.of("FASTA", "Text"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input FASTA", "The FASTA file to search."),
                    ToolParameter.requiredText("ids", "IDs", "One sequence ID per line. Lines starting with # are ignored."),
                    new ToolParameter(
                            "matchMode",
                            "Match mode",
                            "Choose how each selector is compared with a FASTA header.",
                            ParameterType.ENUM,
                            false,
                            "ID_TOKEN",
                            List.of("ID_TOKEN", "FULL_HEADER", "HEADER_PREFIX"),
                            null,
                            null,
                            false,
                            null
                    ),
                    ToolParameter.bool("ignoreCase", "Ignore case", "Compare IDs without case sensitivity.", true),
                    ToolParameter.optionalOutputFile("output", "Output FASTA", "Leave blank to save inside the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing custom output file.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult base = validateParameters(request);
        List<String> selectors = parseSelectors(request.string("ids"));
        if (selectors.isEmpty()) {
            base = base.merge(ValidationResult.of(ValidationIssue.error(
                    "ids",
                    "No sequence IDs were provided.",
                    "Add one ID per line."
            )));
        }
        Path output = request.path("output");
        if (output != null && Files.exists(output) && !request.bool("overwrite")) {
            base = base.merge(ValidationResult.of(ValidationIssue.error(
                    "output",
                    "Output file already exists: " + output,
                    "Choose another path or enable overwrite."
            )));
        }
        return base;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path input = request.path("input");
        Path output = request.path("output");
        if (output == null || output.toString().isBlank()) {
            output = context.defaultOutputPath("extracted.fa");
        }
        createParent(output);

        List<String> requested = parseSelectors(request.string("ids"));
        Set<String> remaining = new LinkedHashSet<>(requested);
        Set<String> found = new LinkedHashSet<>();
        boolean ignoreCase = request.bool("ignoreCase");
        String matchMode = request.string("matchMode").isBlank() ? "ID_TOKEN" : request.string("matchMode");

        context.log("Extracting " + requested.size() + " selectors from " + input);
        try (FastaReader reader = new FastaReader(input);
             BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            FastaRecord record;
            while ((record = reader.next()) != null) {
                context.cancellationToken().throwIfCancelled();
                String matchedSelector = findMatch(record, remaining, matchMode, ignoreCase);
                if (matchedSelector == null) {
                    continue;
                }
                writer.write(">");
                writer.write(record.header());
                writer.newLine();
                writeSequence(writer, record.sequence(), 80);
                found.add(matchedSelector);
                remaining.remove(matchedSelector);
            }
        }

        if (found.isEmpty()) {
            Files.deleteIfExists(output);
            throw new OpenToolException(
                    "NO_IDS_MATCHED",
                    "None of the requested IDs matched the FASTA file.",
                    "Check the ID list and match mode."
            );
        }

        List<OutputArtifact> artifacts = new ArrayList<>();
        artifacts.add(new OutputArtifact(output, "Extracted FASTA", "text/x-fasta"));
        if (!remaining.isEmpty()) {
            Path missingFile = context.defaultOutputPath("missing-ids.txt");
            createParent(missingFile);
            Files.write(missingFile, remaining, StandardCharsets.UTF_8);
            artifacts.add(new OutputArtifact(missingFile, "Missing IDs", "text/plain"));
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("requested", requested.size());
        metrics.put("found", found.size());
        metrics.put("missing", remaining.size());
        ResultTable table = new ResultTable(
                List.of("Metric", "Value"),
                List.of(
                        List.of("Requested IDs", Integer.toString(requested.size())),
                        List.of("Found", Integer.toString(found.size())),
                        List.of("Missing", Integer.toString(remaining.size()))
                )
        );
        String summary = found.size() + " of " + requested.size() + " IDs matched";
        String detail = remaining.isEmpty() ? "All requested IDs were found." :
                "Missing IDs were written to missing-ids.txt.";
        context.progress(1.0, summary);
        return new ToolResult(
                summary,
                "result.fasta.extract.summary",
                List.of(found.size(), requested.size()),
                detail,
                remaining.isEmpty()
                        ? "result.fasta.extract.detail.all"
                        : "result.fasta.extract.detail.missing",
                List.of(),
                table,
                artifacts,
                metrics
        );
    }

    private static String findMatch(FastaRecord record, Set<String> remaining, String matchMode, boolean ignoreCase) {
        for (String selector : remaining) {
            String left;
            String right;
            switch (matchMode) {
                case "FULL_HEADER" -> {
                    left = record.header();
                    right = selector;
                }
                case "HEADER_PREFIX" -> {
                    left = record.header();
                    right = selector;
                }
                default -> {
                    left = record.id();
                    right = selector.trim().split("\\s+", 2)[0];
                }
            }
            boolean matches = "HEADER_PREFIX".equals(matchMode)
                    ? normalize(left, ignoreCase).startsWith(normalize(right, ignoreCase))
                    : normalize(left, ignoreCase).equals(normalize(right, ignoreCase));
            if (matches) {
                return selector;
            }
        }
        return null;
    }

    private static String normalize(String value, boolean ignoreCase) {
        return ignoreCase ? value.toLowerCase(Locale.ROOT) : value;
    }

    private static List<String> parseSelectors(String text) {
        List<String> selectors = new ArrayList<>();
        for (String line : text == null ? List.<String>of() : text.lines().toList()) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !selectors.contains(trimmed)) {
                selectors.add(trimmed);
            }
        }
        return selectors;
    }

    private static void writeSequence(BufferedWriter writer, String sequence, int width) throws java.io.IOException {
        for (int start = 0; start < sequence.length(); start += width) {
            writer.write(sequence, start, Math.min(width, sequence.length() - start));
            writer.newLine();
        }
        if (sequence.isEmpty()) {
            writer.newLine();
        }
    }

    private static void createParent(Path output) throws java.io.IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
