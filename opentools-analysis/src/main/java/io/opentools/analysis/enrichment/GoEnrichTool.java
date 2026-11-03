package io.opentools.analysis.enrichment;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.FormatToolSupport;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GoEnrichTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "go.enrich",
            "GO Enrichment",
            "Run offline GO over-representation analysis with hypergeometric tests and BH correction.",
            "Enrichment",
            List.of("GO enrichment", "GO富集"),
            List.of("Gene-GO TSV", "Gene list"),
            List.of("TSV"),
            "1.1.0",
            List.of(
                    ToolParameter.optionalFile("annotations", "Gene-GO annotation",
                            "Two columns: gene and semicolon/comma-separated GO terms. Optional when resourceId selects a GO pack."),
                    ToolParameter.string("resourceId", "GO resource pack",
                            "Optional pack id from resource.manage; its annotation table replaces the file input.", ""),
                    ToolParameter.requiredFile("genes", "Target genes", "One gene ID per line."),
                    ToolParameter.optionalFile("background", "Background genes", "Optional one-gene-per-line background list."),
                    ToolParameter.optionalOutputFile("output", "Output TSV", "Leave blank to save in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request)
                .merge(FormatToolSupport.validateOutput(request, request.path("annotations")));
        String resourceId = request.string("resourceId") == null ? "" : request.string("resourceId").trim();
        if (resourceId.isEmpty() && request.path("annotations") == null) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "annotations", "Provide a Gene-GO file or select a GO resource pack.",
                    "Import a GO pack with resource.manage or choose a two-column annotation file.")));
        }
        if (!resourceId.isEmpty()) {
            try {
                var pack = ResourcePacks.open().find(resourceId);
                if (pack.isEmpty()) {
                    result = result.merge(ValidationResult.of(ValidationIssue.error(
                            "resourceId", "GO resource pack not found: " + resourceId,
                            "Run resource.manage with action=LIST.")));
                } else if (pack.get().kind() != ResourcePacks.Kind.GO || pack.get().secondary() == null) {
                    result = result.merge(ValidationResult.of(ValidationIssue.error(
                            "resourceId", "Resource pack has no Gene-GO annotation table: " + resourceId,
                            "Import a GO pack with both OBO and annotation files.")));
                }
            } catch (Exception ex) {
                result = result.merge(ValidationResult.of(ValidationIssue.error(
                        "resourceId", "Could not read the resource library: " + ex.getMessage(),
                        "Check the resource directory permissions.")));
            }
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        ResourcePacks.Pack pack = null;
        Path annotationsPath = request.path("annotations");
        String resourceId = request.string("resourceId") == null ? "" : request.string("resourceId").trim();
        if (!resourceId.isEmpty()) {
            pack = ResourcePacks.open().find(resourceId).orElseThrow(() -> new OpenToolException(
                    "RESOURCE_NOT_FOUND",
                    "GO resource pack not found: " + resourceId,
                    "Run resource.manage with action=LIST."
            ));
            if (pack.kind() != ResourcePacks.Kind.GO || pack.secondary() == null) {
                throw new OpenToolException(
                        "RESOURCE_KIND_MISMATCH",
                        "Resource pack has no Gene-GO annotation table: " + resourceId,
                        "Import a GO pack with both OBO and annotation files."
                );
            }
            annotationsPath = pack.secondary();
        }
        if (annotationsPath == null) {
            throw new OpenToolException(
                    "ANNOTATION_REQUIRED",
                    "No Gene-GO annotation table was selected.",
                    "Provide annotations or a GO resource pack."
            );
        }
        Map<String, java.util.Set<String>> annotations = EnrichmentSupport.readAnnotations(annotationsPath);
        java.util.Set<String> targets = EnrichmentSupport.readGeneList(request.path("genes"));
        java.util.Set<String> background = request.path("background") == null
                ? java.util.Set.of() : EnrichmentSupport.readGeneList(request.path("background"));
        List<EnrichmentEngine.Result> results = new EnrichmentEngine().enrich(annotations, background, targets);
        if (results.isEmpty()) {
            throw new OpenToolException("NO_ENRICHED_TERMS", "No GO terms were enriched.", "Check gene IDs and annotations.");
        }
        Path output = FormatToolSupport.outputPath(request, context, "go-enrichment.tsv");
        FormatToolSupport.writeAtomically(output, writer -> EnrichmentSupport.writeResults(writer, results, "hits"));
        context.progress(1.0, "GO enrichment completed");
        return result(request, output, results, pack);
    }

    private static ToolResult result(ToolRequest request, Path output,
                                     List<EnrichmentEngine.Result> results, ResourcePacks.Pack pack) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("terms", results.size());
        if (pack != null) {
            metrics.put("resourceId", pack.id());
            metrics.put("resourceVersion", pack.version());
            metrics.put("resourceSha256", pack.sha256().values().stream().findFirst().orElse(""));
        }
        return new ToolResult(
                "GO enrichment found " + results.size() + " terms", List.of(results.size()),
                "Hypergeometric P-values with Benjamini-Hochberg correction.", List.of(),
                new ResultTable(List.of("Term", "Adjusted P", "Hits"),
                        results.stream().limit(50).map(r -> List.of(r.term(),
                                Double.toString(r.adjustedP()), Integer.toString(r.hitCount()))).toList()),
                List.of(new OutputArtifact(output, "GO enrichment", "text/tab-separated-values")),
                metrics
        );
    }
}