package io.opentools.analysis.enrichment;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.FormatTaskSupport;

import java.nio.file.Path;
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
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("annotations", "Gene-GO annotation", "Two columns: gene and semicolon/comma-separated GO terms."),
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
        return validateParameters(request).merge(FormatTaskSupport.validateOutput(request, request.path("annotations")));
    }

    @Override
    public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
        Map<String, java.util.Set<String>> annotations = EnrichmentSupport.readAnnotations(request.path("annotations"));
        java.util.Set<String> targets = EnrichmentSupport.readGeneList(request.path("genes"));
        java.util.Set<String> background = request.path("background") == null
                ? java.util.Set.of() : EnrichmentSupport.readGeneList(request.path("background"));
        List<EnrichmentEngine.Result> results = new EnrichmentEngine().enrich(annotations, background, targets);
        if (results.isEmpty()) {
            throw new OpenToolException("NO_ENRICHED_TERMS", "No GO terms were enriched.", "Check gene IDs and annotations.");
        }
        Path output = FormatTaskSupport.outputPath(request, context, "go-enrichment.tsv");
        FormatTaskSupport.writeAtomically(output, writer -> EnrichmentSupport.writeResults(writer, results, "hits"));
        context.progress(1.0, "GO enrichment completed");
        return result(request, output, results, "result.go.enrich");
    }

    private static TaskResult result(ToolRequest request, Path output,
                                     List<EnrichmentEngine.Result> results, String key) {
        return new TaskResult(
                "GO enrichment found " + results.size() + " terms",
                key + ".summary", List.of(results.size()),
                "Hypergeometric P-values with Benjamini-Hochberg correction.",
                key + ".detail", List.of(),
                new ResultTable(List.of("Term", "Adjusted P", "Hits"),
                        results.stream().limit(50).map(r -> List.of(r.term(),
                                Double.toString(r.adjustedP()), Integer.toString(r.hitCount()))).toList()),
                List.of(new OutputArtifact(output, "GO enrichment", "text/tab-separated-values")),
                Map.of("terms", results.size())
        );
    }
}
