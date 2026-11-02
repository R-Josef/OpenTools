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
import io.opentools.formats.FormatToolSupport;

import java.nio.file.Path;
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
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("annotations", "Gene-pathway annotation", "Two columns: gene and semicolon/comma-separated pathway IDs."),
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
        return validateParameters(request).merge(FormatToolSupport.validateOutput(request, request.path("annotations")));
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Map<String, java.util.Set<String>> annotations = EnrichmentSupport.readAnnotations(request.path("annotations"));
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
        return new ToolResult(
                "KEGG enrichment found " + results.size() + " pathways",
                "result.kegg.enrich.summary", List.of(results.size()),
                "Hypergeometric P-values with Benjamini-Hochberg correction.",
                "result.kegg.enrich.detail", List.of(),
                new ResultTable(List.of("Term", "Adjusted P", "Hits"),
                        results.stream().limit(50).map(r -> List.of(r.term(),
                                Double.toString(r.adjustedP()), Integer.toString(r.hitCount()))).toList()),
                List.of(new OutputArtifact(output, "KEGG enrichment", "text/tab-separated-values")),
                Map.of("terms", results.size())
        );
    }
}
