package io.opentools.external.alignment;

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
import io.opentools.api.tool.ValidationResult;
import io.opentools.external.process.ExternalArguments;
import io.opentools.external.process.TemporaryFiles;
import io.opentools.formats.FormatToolSupport;
import io.opentools.formats.bed.BedReader;
import io.opentools.formats.bed.BedRecord;
import io.opentools.formats.fasta.FastaRecord;
import io.opentools.formats.fasta.FastaSequenceStore;
import io.opentools.formats.fasta.FastaWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlastRegionsTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "alignment.blast.regions",
            "BLAST Genome Regions",
            "Extract BED-defined genome regions, write temporary query FASTA, and run shared local BLAST.",
            "Alignment",
            List.of("blast regions", "region blast", "区域 BLAST"),
            List.of("FASTA", "BED"),
            List.of("BLAST tabular", "TSV"),
            "1.3.0",
            List.of(
                    ToolParameter.requiredFile("genome", "Genome FASTA", "Source genome for BED regions."),
                    ToolParameter.requiredFile("regions", "BED regions", "BED intervals used as BLAST queries."),
                    ToolParameter.requiredFile("subject", "Subject FASTA", "BLAST subject sequences."),
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
                .merge(FormatToolSupport.validateOutput(request, request.path("genome")));
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
        Path output = FormatToolSupport.outputPath(request, context, "blast-region-hits.tsv");
        Path work = Files.createTempDirectory("opentools-blast-regions-");
        try {
            Path query = work.resolve("regions.fasta");
            ExtractionResult extraction = extractQueries(
                    request.path("genome"), request.path("regions"), query, context);
            long extracted = extraction.regions();
            if (extracted == 0) {
                throw new OpenToolException("NO_REGIONS_EXTRACTED", "No BED regions could be extracted.",
                        "Check sequence IDs, BED coordinates, and genome FASTA.");
            }
            ToolResult delegated = BlastLocalDelegation.run(request, context, query, output);
            context.progress(1.0, "Region BLAST completed");
            return new ToolResult(
                    delegated.summary(),
                    "result.blast.regions.summary", List.of(extracted),
                    extracted + " BED regions were used as BLAST queries.",
                    "result.blast.regions.detail", List.of(extracted),
                    new ResultTable(List.of("Metric", "Value"), List.of(
                            List.of("Regions", Long.toString(extracted)),
                            List.of("BLAST hits", String.valueOf(delegated.metrics().get("totalHits")))
                    )),
                    List.of(new OutputArtifact(output, "Region BLAST hits", "text/tab-separated-values")),
                    Map.of("regions", extracted, "blast", delegated.metrics(),
                            "genomeStorage", extraction.storageMode())
            );
        } finally {
            TemporaryFiles.deleteRecursively(work);
        }
    }

    static ExtractionResult extractQueries(
            Path genome,
            Path bed,
            Path output,
            ToolExecutionContext context
    ) throws Exception {
        Map<String, List<BedRecord>> regionsBySequence = new LinkedHashMap<>();
        try (BedReader reader = new BedReader(bed)) {
            BedRecord region;
            while ((region = reader.next()) != null) {
                regionsBySequence.computeIfAbsent(region.seqId(), ignored -> new ArrayList<>()).add(region);
            }
        }
        FastaSequenceStore sequences = FastaSequenceStore.load(genome);
        long[] count = new long[1];
        FormatToolSupport.writeAtomically(output, writer -> {
            for (Map.Entry<String, List<BedRecord>> entry : regionsBySequence.entrySet()) {
                String seqId = entry.getKey();
                if (!sequences.contains(seqId)) {
                    continue;
                }
                for (BedRecord region : entry.getValue()) {
                    context.cancellationToken().throwIfCancelled();
                    if (region.end() > sequences.length(seqId)) {
                        continue;
                    }
                    String sequence = sequences.subsequence(
                            seqId, region.start() + 1, region.end());
                    if (region.strand() == '-') {
                        sequence = reverseComplement(sequence);
                    }
                    String id = seqId + ":" + (region.start() + 1) + "-" + region.end();
                    FastaWriter.write(writer, new FastaRecord(
                            id, id + " strand=" + region.strand(), sequence), 80);
                    count[0]++;
                }
            }
        });
        return new ExtractionResult(count[0], sequences.storageMode());
    }

    private static String reverseComplement(String sequence) {
        StringBuilder result = new StringBuilder(sequence.length());
        for (int i = sequence.length() - 1; i >= 0; i--) {
            char value = Character.toUpperCase(sequence.charAt(i));
            result.append(switch (value) {
                case 'A' -> 'T';
                case 'T' -> 'A';
                case 'U' -> 'A';
                case 'C' -> 'G';
                case 'G' -> 'C';
                case 'R' -> 'Y';
                case 'Y' -> 'R';
                case 'K' -> 'M';
                case 'M' -> 'K';
                case 'B' -> 'V';
                case 'V' -> 'B';
                case 'D' -> 'H';
                case 'H' -> 'D';
                default -> value;
            });
        }
        return result.toString();
    }

    record ExtractionResult(long regions, String storageMode) {
    }
}
