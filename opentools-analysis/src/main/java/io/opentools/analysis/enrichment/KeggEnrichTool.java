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

public final class KeggEnrichTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "kegg.enrich",
            "KEGG Enrichment",
            "Run offline KEGG pathway over-representation analysis with BH correction.",
            "Enrichment",
            List.of("KEGG enrichment", "KEGG富集"),
            List.of("Gene-pathway TSV", "Gene list"),
            List.of("TSV"),
            "1.1.0",
            List.of(
                    ToolParameter.optionalFile("annotations", "Gene-pathway annotation",
                            "Two columns: gene and semicolon/comma-separated pathway IDs. Optional when resourceId selects a KEGG pack."),
                    ToolParameter.string("resourceId", "KEGG resource pack",
                            "Optional pack id from resource.manage; its mapping table replaces the file input.", ""),
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
                    "annotations", "Provide a gene-pathway file or select a KEGG resource pack.",
                    "Import a KEGG pack with resource.manage or choose a two-column mapping file.")));
        }
        if (!resourceId.isEmpty()) {
            try {
                var pack = ResourcePacks.open().find(resourceId);
                if (pack.isEmpty()) {
                    result = result.merge(ValidationResult.of(ValidationIssue.error(
                            "resourceId", "KEGG resource pack not found: " + resourceId,
                            "Run resource.manage with action=LIST.")));
                } else if (pack.get().kind() != ResourcePacks.Kind.KEGG) {
                    result = result.merge(ValidationResult.of(ValidationIssue.error(
                            "resourceId", "Resource pack is not a KEGG pack: " + resourceId,
                            "Select a KEGG pack.")));
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
        Path mappingPath = request.path("annotations");
        String resourceId = request.string("resourceId") == null ? "" : request.string("resourceId").trim();
        if (!resourceId.isEmpty()) {
            pack = ResourcePacks.open().find(resourceId).orElseThrow(() -> new OpenToolException(
                    "RESOURCE_NOT_FOUND",
                    "KEGG resource pack not found: " + resourceId,
                    "Run resource.manage with action=LIST."
            ));
            if (pack.kind() != ResourcePacks.Kind.KEGG) {
                throw new OpenToolException(
                        "RESOURCE_KIND_MISMATCH",
                        "Resource pack is not a KEGG pack: " + resourceId,
                        "Select a KEGG pack."
                );
            }
            mappingPath = pack.primary();
        }
        if (mappingPath == null) {
            throw new OpenToolException(
                    "ANNOTATION_REQUIRED",
                    "No gene-pathway annotation table was selected.",
                    "Provide annotations or a KEGG resource pack."
            );
        }
        Map<String, java.util.Set<String>> annotations = EnrichmentSupport.readAnnotations(mappingPath);
        java.util.Set<String> targets = EnrichmentSupport.readGeneList(request.path("genes"));
        java.util.Set<String> background = request.path("background") == null
                ? java.util.Set.of() : EnrichmentSupport.readGeneList(request.path("background"));
        List<EnrichmentEngine.Result> results = new EnrichmentEngine().enrich(annotations, background, targets);
        if (results.isEmpty()) {
            throw new OpenToolException("NO_ENRICHED_TERMS", "No KEGG pathways were enriched.", "Check gene IDs and annotations.");
        }
        Path output = FormatToolSupport.outputPath(request, context, "kegg-enrichment.tsv");
        FormatToolSupport.writeAtomically(output, writer -> EnrichmentSupport.writeResults(writer, results, "hits"));
        context.progress(1.0, "KEGG enrichment completed");
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("terms", results.size());
        if (pack != null) {
            metrics.put("resourceId", pack.id());
            metrics.put("resourceVersion", pack.version());
            metrics.put("resourceSha256", pack.sha256().values().stream().findFirst().orElse(""));
        }
        return new ToolResult(
                "KEGG enrichment found " + results.size() + " pathways", List.of(results.size()),
                "Hypergeometric P-values with Benjamini-Hochberg correction.", List.of(),
                new ResultTable(List.of("Term", "Adjusted P", "Hits"),
                        results.stream().limit(50).map(r -> List.of(r.term(),
                                Double.toString(r.adjustedP()), Integer.toString(r.hitCount()))).toList()),
                List.of(new OutputArtifact(output, "KEGG enrichment", "text/tab-separated-values")),
                metrics
        );
    }
}