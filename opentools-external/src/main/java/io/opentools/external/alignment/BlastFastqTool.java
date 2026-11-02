package io.opentools.external.alignment;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.external.process.ExternalArguments;
import io.opentools.external.process.TemporaryFiles;
import io.opentools.formats.FormatToolSupport;
import io.opentools.formats.fastq.FastqFastaConverter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class BlastFastqTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "alignment.blast.fastq",
            "BLAST FASTQ Query",
            "Convert FASTQ queries to temporary FASTA and run the shared local BLAST workflow.",
            "Alignment",
            List.of("blast fastq", "fastq blast", "FASTQ 比对"),
            List.of("FASTQ", "FASTQ.gz"),
            List.of("BLAST tabular", "TSV"),
            "1.2.0",
            List.of(
                    ToolParameter.requiredFile("query", "Query FASTQ", "FASTQ or FASTQ.gz query sequences."),
                    ToolParameter.requiredFile("subject", "Subject FASTA", "Subject sequences."),
                    new ToolParameter("program", "BLAST program", "BLAST program or AUTO.", ParameterType.ENUM,
                            false, "AUTO", List.of("AUTO", "BLASTN", "BLASTP", "BLASTX", "TBLASTN", "TBLASTX"),
                            null, null, false, null),
                    ToolParameter.decimal("evalue", "E-value threshold", "Maximum reported E-value.", 1e-5, 0.0, 1e9),
                    ToolParameter.integer("maxTargetSeqs", "Maximum target sequences", "Maximum hits per query.", 10, 1, 10_000),
                    ToolParameter.integer("threads", "Threads", "BLAST worker threads.", 4, 1, 256),
                    ToolParameter.integer("timeoutMinutes", "Timeout minutes", "Maximum external command runtime.", 30, 1, 1440),
                    ToolParameter.optionalDirectory("blastBin", "BLAST bin directory", "Directory containing BLAST+."),
                    ToolParameter.bool("cacheEnabled", "Reuse BLAST database",
                            "Reuse an immutable database keyed by subject hash, BLAST version, program, and database type.",
                            true),
                    ToolParameter.bool("refreshDatabase", "Refresh BLAST cache",
                            "Rebuild the cache entry even when a matching database exists.", false),
                    ToolParameter.optionalDirectory("cacheDirectory", "BLAST cache directory",
                            "Optional cache directory. Defaults to the system temporary OpenTools cache."),
                    ToolParameter.integer("cacheMaxMegabytes", "BLAST cache limit",
                            "Least-recently-used cache entries are evicted above this size.",
                            BlastDatabaseCache.DEFAULT_MAX_MEGABYTES, 1, 1_048_576),
                    ToolParameter.integer("cacheMaxEntries", "BLAST cache entry limit",
                            "Least-recently-used cache entries are evicted above this count.",
                            BlastDatabaseCache.DEFAULT_MAX_ENTRIES, 1, 1_000_000),
                    ToolParameter.optionalOutputFile("output", "Output BLAST table", "Leave blank to save in the run directory."),
                    new ToolParameter("extraArgs", "Extra BLAST arguments", "Quote-aware extra CLI arguments.",
                            ParameterType.TEXT, false, "", List.of(), null, null, true, null),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request)
                .merge(FormatToolSupport.validateOutput(request, request.path("query")));
        try {
            ExternalArguments.parse(request.string("extraArgs"));
        } catch (IllegalArgumentException ex) {
            result = result.merge(ValidationResult.of(io.opentools.api.tool.ValidationIssue.error(
                    "extraArgs", ex.getMessage(), "Close quoted arguments or remove them.")));
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path output = FormatToolSupport.outputPath(request, context, "blast-fastq-hits.tsv");
        Path work = Files.createTempDirectory("opentools-blast-fastq-");
        try {
            Path query = work.resolve("query.fasta");
            long records = FastqFastaConverter.convert(request.path("query"), query);
            ToolResult delegated = BlastLocalDelegation.run(request, context, query, output);
            context.progress(1.0, "FASTQ BLAST completed");
            return new ToolResult(
                    delegated.summary(),
                    "result.blast.fastq.summary", List.of(records),
                    records + " FASTQ records were converted to temporary FASTA queries.",
                    "result.blast.fastq.detail", List.of(records),
                    new ResultTable(List.of("Metric", "Value"), List.of(
                            List.of("FASTQ records", Long.toString(records)),
                            List.of("BLAST hits", String.valueOf(delegated.metrics().get("totalHits")))
                    )),
                    List.of(new OutputArtifact(output, "FASTQ BLAST hits", "text/tab-separated-values")),
                    Map.of("fastqRecords", records, "blast", delegated.metrics())
            );
        } finally {
            TemporaryFiles.deleteRecursively(work);
        }
    }
}
