package io.opentools.external.annotation;

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

public final class PfamImportTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "annotation.pfam.import",
            "Import Pfam Domains",
            "Normalize Pfam/HMMER domtblout domain hits into a stable TSV annotation table.",
            "Annotation",
            List.of("pfam import", "hmmscan import", "Pfam 导入"),
            List.of("HMMER domtblout"),
            List.of("TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "domtblout", "Pfam HMMER domtblout file."),
                    ToolParameter.optionalOutputFile("output", "Output TSV", "Leave blank to save in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        return validateParameters(request)
                .merge(FormatToolSupport.validateOutput(request, request.path("input")));
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path output = FormatToolSupport.outputPath(request, context, "pfam-domains.tsv");
        List<HmmerDomainHit> hits = new HmmerDomTableParser().parse(request.path("input"));
        if (hits.isEmpty()) {
            throw new OpenToolException("NO_PFAM_HITS", "No Pfam domain hits were found.",
                    "Check the domtblout file and HMM database.");
        }
        FormatToolSupport.writeAtomically(output, writer -> {
            writer.write("query\tpfam_id\tpfam_accession\tfull_evalue\tfull_score\tindependent_evalue"
                    + "\tali_from\tali_to\thmm_from\thmm_to\taccuracy\tdescription\n");
            for (HmmerDomainHit hit : hits) {
                writer.write(escape(hit.queryName()));
                writer.write('\t');
                writer.write(escape(hit.targetName()));
                writer.write('\t');
                writer.write(escape(hit.targetAccession()));
                writer.write('\t');
                writer.write(Double.toString(hit.fullEvalue()));
                writer.write('\t');
                writer.write(Double.toString(hit.fullScore()));
                writer.write('\t');
                writer.write(Double.toString(hit.independentEvalue()));
                writer.write('\t');
                writer.write(Long.toString(hit.alignmentFrom()));
                writer.write('\t');
                writer.write(Long.toString(hit.alignmentTo()));
                writer.write('\t');
                writer.write(Long.toString(hit.hmmFrom()));
                writer.write('\t');
                writer.write(Long.toString(hit.hmmTo()));
                writer.write('\t');
                writer.write(Double.toString(hit.accuracy()));
                writer.write('\t');
                writer.write(escape(hit.description()));
                writer.newLine();
            }
        });
        long queries = hits.stream().map(HmmerDomainHit::queryName).distinct().count();
        context.progress(1.0, "Pfam import completed");
        return new ToolResult(
                "Imported " + hits.size() + " Pfam domain hits", List.of(hits.size()),
                queries + " query sequences contain domain hits.", List.of(queries),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Domain hits", Integer.toString(hits.size())),
                        List.of("Queries", Long.toString(queries))
                )),
                List.of(new OutputArtifact(output, "Pfam domain annotation", "text/tab-separated-values")),
                Map.of("domainHits", hits.size(), "queries", queries)
        );
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\t", " ").replace("\n", " ");
    }
}
