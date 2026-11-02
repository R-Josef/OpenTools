package io.opentools.analysis.enrichment;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.Compression;
import io.opentools.formats.FormatToolSupport;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GoLevelTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "go.level",
            "GO Term Levels",
            "Calculate shortest root distance for terms in a local GO parent hierarchy.",
            "Enrichment",
            List.of("GO level", "GO层级"),
            List.of("Term-parent TSV", "GO info TSV", "OBO 1.4"),
            List.of("TSV"),
            "1.1.0",
            List.of(
                    ToolParameter.requiredFile("hierarchy", "GO hierarchy",
                            "Two-column term/parent TSV or an OBO 1.4 file with [Term] stanzas."),
                    ToolParameter.optionalFile("termInfo", "Term information", "Optional term/name/namespace table."),
                    ToolParameter.optionalOutputFile("output", "Output TSV", "Leave blank to save in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public ValidationResult validate(ToolRequest request) {
        return validateParameters(request).merge(FormatToolSupport.validateOutput(request, request.path("hierarchy")));
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Hierarchy hierarchy = readHierarchy(request.path("hierarchy"));
        Map<String, List<String>> info = new LinkedHashMap<>(hierarchy.info());
        if (request.path("termInfo") != null) {
            info.putAll(readInfo(request.path("termInfo")));
        }
        LevelResult levelResult = levels(hierarchy.parents());
        Map<String, Integer> levels = levelResult.levels();
        Path output = FormatToolSupport.outputPath(request, context, "go-levels.tsv");
        FormatToolSupport.writeAtomically(output, writer -> {
            writer.write("term\tname\tnamespace\tlevel\tparents\n");
            for (Map.Entry<String, Integer> entry : levels.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue()
                            .thenComparing(Map.Entry.comparingByKey())).toList()) {
                List<String> details = info.getOrDefault(entry.getKey(), List.of());
                writer.write(entry.getKey());
                writer.write('\t');
                writer.write(details.size() > 0 ? details.get(0) : "");
                writer.write('\t');
                writer.write(details.size() > 1 ? details.get(1) : "");
                writer.write('\t');
                writer.write(Integer.toString(entry.getValue()));
                writer.write('\t');
                writer.write(String.join(",",
                        hierarchy.parents().getOrDefault(entry.getKey(), Set.of())));
                writer.newLine();
            }
        });
        context.progress(1.0, "GO level calculation completed");
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("terms", levels.size());
        metrics.put("format", hierarchy.format());
        metrics.put("altIds", hierarchy.altIds());
        metrics.put("unresolvedParents", hierarchy.unresolvedParents());
        metrics.put("cycleOrUnreachableTerms", levelResult.unreachableTerms());
        return new ToolResult(
                "Calculated levels for " + levels.size() + " GO terms",
                "result.go.level.summary", List.of(levels.size()),
                "Format: " + hierarchy.format()
                        + "; terms without a reachable root are assigned level 1.",
                "result.go.level.detail", List.of(),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Terms", Integer.toString(levels.size()))
                )),
                List.of(new OutputArtifact(output, "GO term levels", "text/tab-separated-values")),
                metrics
        );
    }

    private static Hierarchy readHierarchy(Path path) throws Exception {
        List<String> lines = readLines(path);
        boolean obo = lines.stream().anyMatch(line -> line.trim().equals("[Term]"));
        return obo ? readObo(lines) : readTsvHierarchy(lines);
    }

    private static Hierarchy readTsvHierarchy(List<String> lines) {
        Map<String, Set<String>> parents = new LinkedHashMap<>();
        Set<String> declared = new LinkedHashSet<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split("\\t", -1);
            if (fields.length < 2 || fields[0].equalsIgnoreCase("term")) {
                continue;
            }
            String term = fields[0].trim();
            String parent = fields[1].trim();
            if (term.isEmpty()) {
                continue;
            }
            declared.add(term);
            parents.computeIfAbsent(term, ignored -> new LinkedHashSet<>());
            if (!parent.isBlank() && !parent.equals(".")) {
                parents.get(term).add(parent);
                parents.computeIfAbsent(parent, ignored -> new LinkedHashSet<>());
            }
        }
        if (parents.isEmpty()) {
            throw new OpenToolException("GO_HIERARCHY_EMPTY", "No GO hierarchy edges were found.",
                    "Provide term/parent rows.");
        }
        return new Hierarchy(parents, Map.of(), "TSV", 0, 0, declared);
    }

    private static Hierarchy readObo(List<String> lines) {
        List<OboTerm> terms = new ArrayList<>();
        OboTermBuilder current = null;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.equals("[Term]")) {
                if (current != null) {
                    terms.add(current.build());
                }
                current = new OboTermBuilder();
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                if (current != null) {
                    terms.add(current.build());
                    current = null;
                }
                continue;
            }
            if (current == null || line.isEmpty() || line.startsWith("!")) {
                continue;
            }
            int separator = line.indexOf(':');
            if (separator < 1) {
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            switch (key) {
                case "id" -> current.id = value;
                case "name" -> current.name = value;
                case "namespace" -> current.namespace = value;
                case "alt_id" -> {
                    if (!value.isBlank()) {
                        current.altIds.add(value);
                    }
                }
                case "is_a" -> {
                    String parent = value.split("\\s+", 2)[0];
                    if (!parent.isBlank()) {
                        current.parents.add(parent);
                    }
                }
                default -> {
                }
            }
        }
        if (current != null) {
            terms.add(current.build());
        }

        Map<String, String> aliases = new LinkedHashMap<>();
        Set<String> declared = new LinkedHashSet<>();
        Map<String, OboTerm> byPrimary = new LinkedHashMap<>();
        for (OboTerm term : terms) {
            if (term.id().isBlank()) {
                throw new OpenToolException(
                        "GO_OBO_TERM_ID_MISSING",
                        "An OBO [Term] stanza has no id.",
                        "Add an id: GO:... line or remove the incomplete stanza."
                );
            }
            if (byPrimary.putIfAbsent(term.id(), term) != null) {
                throw new OpenToolException(
                        "GO_OBO_DUPLICATE_ID",
                        "Duplicate OBO term id: " + term.id(),
                        "Keep one stanza per GO identifier."
                );
            }
            declared.add(term.id());
        }
        for (OboTerm term : terms) {
            for (String alias : term.altIds()) {
                String existing = aliases.putIfAbsent(alias, term.id());
                if (existing != null && !existing.equals(term.id())) {
                    throw new OpenToolException(
                            "GO_OBO_ALT_ID_CONFLICT",
                            "OBO alternate id " + alias + " maps to multiple primary terms.",
                            "Keep each alt_id under one term."
                    );
                }
                if (declared.contains(alias) && !alias.equals(term.id())) {
                    throw new OpenToolException(
                            "GO_OBO_ALT_ID_CONFLICT",
                            "OBO alternate id " + alias + " is also a primary term id.",
                            "Keep each alt_id under one term."
                    );
                }
            }
        }

        Map<String, Set<String>> parents = new LinkedHashMap<>();
        Map<String, List<String>> info = new LinkedHashMap<>();
        for (OboTerm term : terms) {
            Set<String> canonicalParents = parents.computeIfAbsent(
                    term.id(), ignored -> new LinkedHashSet<>());
            for (String rawParent : term.parents()) {
                String parent = aliases.getOrDefault(rawParent, rawParent);
                canonicalParents.add(parent);
                parents.computeIfAbsent(parent, ignored -> new LinkedHashSet<>());
            }
            info.put(term.id(), List.of(term.name(), term.namespace()));
        }
        if (parents.isEmpty()) {
            throw new OpenToolException(
                    "GO_HIERARCHY_EMPTY",
                    "No GO terms were found in the OBO file.",
                    "Provide at least one [Term] stanza."
            );
        }
        long unresolved = parents.keySet().stream().filter(term -> !declared.contains(term)).count();
        return new Hierarchy(parents, info, "OBO", aliases.size(), unresolved, declared);
    }

    private static Map<String, List<String>> readInfo(Path path) throws Exception {
        Map<String, List<String>> info = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\\t", -1);
                if (fields.length >= 2 && !fields[0].equalsIgnoreCase("term")) {
                    info.put(fields[0], List.of(fields[1], fields.length > 2 ? fields[2] : ""));
                }
            }
        }
        return info;
    }

    private static LevelResult levels(Map<String, Set<String>> parents) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : parents.entrySet()) {
            for (String parent : entry.getValue()) {
                children.computeIfAbsent(parent, ignored -> new ArrayList<>())
                        .add(entry.getKey());
            }
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Set<String>> entry : parents.entrySet()) {
            if (entry.getValue().isEmpty()) {
                levels.put(entry.getKey(), 1);
                queue.add(entry.getKey());
            }
        }
        while (!queue.isEmpty()) {
            String parent = queue.removeFirst();
            int next = levels.get(parent) + 1;
            for (String child : children.getOrDefault(parent, List.of())) {
                Integer existing = levels.get(child);
                if (existing == null || next < existing) {
                    levels.put(child, next);
                    queue.addLast(child);
                }
            }
        }
        long unreachable = 0;
        for (String term : parents.keySet()) {
            if (!levels.containsKey(term)) {
                levels.put(term, 1);
                unreachable++;
            }
        }
        return new LevelResult(levels, unreachable);
    }

    private static List<String> readLines(Path path) throws Exception {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static final class OboTermBuilder {
        private String id = "";
        private String name = "";
        private String namespace = "";
        private final Set<String> altIds = new LinkedHashSet<>();
        private final Set<String> parents = new LinkedHashSet<>();

        private OboTerm build() {
            return new OboTerm(id, name, namespace, Set.copyOf(altIds), Set.copyOf(parents));
        }
    }

    private record OboTerm(
            String id,
            String name,
            String namespace,
            Set<String> altIds,
            Set<String> parents
    ) {
    }

    private record Hierarchy(
            Map<String, Set<String>> parents,
            Map<String, List<String>> info,
            String format,
            int altIds,
            long unresolvedParents,
            Set<String> declared
    ) {
    }

    private record LevelResult(Map<String, Integer> levels, long unreachableTerms) {
    }
}
