package io.opentools.analysis.hts;

import htsjdk.samtools.AlignmentBlock;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileHeader.SortOrder;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.MergingSamRecordIterator;
import htsjdk.samtools.SamFileHeaderMerger;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.IntervalList;
import htsjdk.samtools.util.SamLocusIterator;
import htsjdk.tribble.index.tabix.TabixIndexType;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
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
import io.opentools.core.util.OrderedParallelProcessor;
import io.opentools.formats.FormatToolSupport;
import io.opentools.formats.bed.BedReader;
import io.opentools.formats.bed.BedRecord;

import java.io.BufferedWriter;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class HtsTools {
    private HtsTools() {
    }

    public static List<AbstractOpenTool> tools() {
        return List.of(
                new SamStatsTool(),
                new BamSubsetTool(),
                new BamCoverageTool(),
                new VcfStatsTool(),
                new VcfFilterTool(),
                new BedCoverageTool()
        );
    }

    static SamReader sam(Path path) {
        return sam(path, null);
    }

    static SamReader sam(Path path, Path reference) {
        SamReaderFactory factory = SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.LENIENT);
        if (reference != null) {
            factory.referenceSequence(reference);
        }
        return factory.open(path);
    }

    static final class SamStatsTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
                "hts.sam-stats", "SAM/BAM/CRAM Statistics", "Stream local alignment statistics with HTSJDK.",
                "HTS", List.of("samtools stats", "BAM 统计"),
                List.of("SAM", "BAM", "CRAM"), List.of("TSV"), "1.0.0",
                List.of(ToolParameter.requiredFile("input", "Input alignment", "SAM, BAM, or CRAM."),
                        ToolParameter.optionalFile("reference", "Reference FASTA", "Required for CRAM when not embedded."),
                        ToolParameter.optionalOutputFile("output", "Output TSV", "Leave blank to save in the run directory."),
                        ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)));

        @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
        @Override public ValidationResult validate(ToolRequest request) {
            return validateParameters(request).merge(FormatToolSupport.validateOutput(request, request.path("input")));
        }
        @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
            long[] counts = new long[7];
            long min = Long.MAX_VALUE;
            long max = 0;
            try (SamReader reader = sam(request.path("input"), request.path("reference"))) {
                for (SAMRecord record : reader) {
                    context.cancellationToken().throwIfCancelled();
                    counts[0]++;
                    if (record.getReadUnmappedFlag()) {
                        counts[1]++;
                    } else {
                        counts[2]++;
                    }
                    if (record.getReadPairedFlag() && record.getFirstOfPairFlag()) {
                        counts[3]++;
                    }
                    if (record.getDuplicateReadFlag()) {
                        counts[4]++;
                    }
                    if (record.getReadFailsVendorQualityCheckFlag()) {
                        counts[5]++;
                    }
                    int length = record.getReadLength();
                    counts[6] += length;
                    min = Math.min(min, length);
                    max = Math.max(max, length);
                }
            }
            if (counts[0] == 0) {
                throw new OpenToolException("EMPTY_ALIGNMENT", "No alignment records were found.", "Check the file.");
            }
            Path output = FormatToolSupport.outputPath(request, context, "sam-stats.tsv");
            long records = counts[0];
            long minimum = min == Long.MAX_VALUE ? 0 : min;
            long maximum = max;
            long average = counts[6] / Math.max(1, records);
            FormatToolSupport.writeAtomically(output, writer -> {
                writer.write("metric\tvalue\n");
                String[] labels = {"records", "unmapped", "mapped", "first_in_pair",
                        "duplicates", "failed_qc", "total_bases_min", "total_bases_max",
                        "total_bases_mean"};
                long[] values = {records, counts[1], counts[2], counts[3], counts[4], counts[5],
                        minimum, maximum, average};
                for (int index = 0; index < labels.length; index++) {
                    writer.write(labels[index] + "\t" + values[index] + "\n");
                }
            });
            context.progress(1.0, "Alignment statistics completed");
            return new ToolResult("Summarized " + records + " alignment records", List.of(records),
                    counts[2] + " mapped and " + counts[1] + " unmapped reads.", List.of(counts[2], counts[1]),
                    new ResultTable(List.of("Metric", "Value"), List.of(
                            List.of("Records", Long.toString(records)),
                            List.of("Mapped", Long.toString(counts[2])),
                            List.of("Unmapped", Long.toString(counts[1])),
                            List.of("Bases", Long.toString(counts[6])))),
                    List.of(new OutputArtifact(output, "Alignment statistics", "text/tab-separated-values")),
                    Map.of("records", records, "mapped", counts[2], "unmapped", counts[1], "bases", counts[6]));
        }
    }

    static final class BamSubsetTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
                "hts.bam.subset", "Subset SAM/BAM/CRAM", "Subset alignments to simple 1-based inclusive contig regions.",
                "HTS", List.of("BAM subset", "区域提取"),
            List.of("SAM", "BAM", "CRAM", "Regions"), List.of("SAM", "BAM"), "1.2.0",
                List.of(ToolParameter.requiredFile("input", "Input alignment", "SAM, BAM, or CRAM."),
                        ToolParameter.optionalFile("reference", "Reference FASTA", "Required for CRAM when not embedded."),
                        ToolParameter.requiredText("regions", "Regions", "One CONTIG:START-END region per line."),
                        ToolParameter.integer("threads", "Threads",
                                "0 uses AUTO for large indexed alignments; 1 disables parallel regions.",
                                0, 0, 64),
                        ToolParameter.optionalOutputFile("output", "Output alignment", ".bam writes BAM; otherwise SAM."),
                        ToolParameter.bool("createIndex", "Create BAM index",
                                "Write a BAI index when the output is BAM.", true),
                        ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)));

        @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
        @Override public ValidationResult validate(ToolRequest request) {
            return validateParameters(request).merge(FormatToolSupport.validateOutput(request, request.path("input")));
        }
        @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
            List<Region> mergedRegions = mergeRegions(parseRegions(request.string("regions")));
            Path output = FormatToolSupport.outputPath(request, context, "subset.bam");
            boolean bam = output.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bam");
            boolean createIndex = bam && request.bool("createIndex");
            int threads = OrderedParallelProcessor.resolveThreads(
                    request.integer("threads"), Files.size(request.path("input")),
                    32L * 1024 * 1024);
            boolean indexed;
            SAMFileHeader inputHeader;
            try (SamReader probe = sam(request.path("input"), request.path("reference"))) {
                indexed = probe.hasIndex();
                inputHeader = probe.getFileHeader().clone();
            }
            List<Region> regions = indexed
                    ? orderRegionsForHeader(mergedRegions, inputHeader)
                    : mergedRegions;
            boolean parallelRegions = indexed && threads > 1 && regions.size() > 1;
            long records;
            Path stagedOutput = stageSibling(output, bam ? ".bam" : ".sam");
            Path stagedIndex = null;
            Path publishedIndex = createIndex ? bamIndexPath(output) : null;
            try {
                FormatToolSupport.createParent(output);
                ensurePrimaryTarget(
                        output,
                        request.bool("overwrite"),
                        "ALIGNMENT_OUTPUT_TARGET_CONFLICT",
                        "Alignment output");
                if (publishedIndex != null) {
                    ensureSidecarTarget(
                            publishedIndex,
                            request.bool("overwrite"),
                            "ALIGNMENT_INDEX_TARGET_CONFLICT",
                            "BAM index");
                }
                Path workDirectory = Files.createTempDirectory(
                        Files.createDirectories(context.runDirectory().resolve("tmp")),
                        "bam-subset-"
                );
                ConcurrentLinkedQueue<Path> workerOutputs = new ConcurrentLinkedQueue<>();
                try {
                    try {
                        records = parallelRegions
                                ? writeParallelIndexedSubset(
                                request.path("input"),
                                request.path("reference"),
                                inputHeader,
                                regions,
                                stagedOutput,
                                createIndex,
                                threads,
                                workDirectory,
                                workerOutputs,
                                context
                        )
                                : writeSequentialSubset(
                                request.path("input"),
                                request.path("reference"),
                                inputHeader,
                                indexed,
                                regions,
                                stagedOutput,
                                createIndex,
                                workDirectory,
                                context
                        );
                    } catch (IllegalArgumentException ex) {
                        OpenToolException translated = translateReferenceFailure(
                                request.path("input"), request.path("reference"), ex);
                        if (translated != null) {
                            throw translated;
                        }
                        throw ex;
                    }
                } finally {
                    for (Path workerOutput : workerOutputs) {
                        Files.deleteIfExists(workerOutput);
                    }
                    deleteRecursivelyQuietly(workDirectory);
                }
                if (createIndex) {
                    stagedIndex = locateCreatedBamIndex(stagedOutput);
                    if (stagedIndex == null) {
                        throw new OpenToolException(
                                "BAM_INDEX_MISSING",
                                "The BAM writer did not create the requested BAI index.",
                                "Check output permissions and available space."
                        );
                    }
                    publishPrimaryWithSidecar(
                            stagedOutput, output, stagedIndex, publishedIndex);
                } else {
                    moveAtomically(stagedOutput, output);
                }
            } finally {
                Files.deleteIfExists(stagedOutput);
                if (stagedIndex != null) {
                    Files.deleteIfExists(stagedIndex);
                }
            }
            Path index = publishedIndex;
            List<OutputArtifact> artifacts = new ArrayList<>();
            artifacts.add(new OutputArtifact(output, "Subset alignments",
                    bam ? "application/x-bam" : "text/x-sam"));
            if (createIndex && Files.exists(index)) {
                artifacts.add(new OutputArtifact(index, "BAM index", "application/octet-stream"));
            }
            context.progress(1.0, "Alignment subset completed");
            return new ToolResult("Wrote " + records + " alignment records", List.of(records),
                    "Output format: " + (bam ? "BAM" : "SAM")
                            + (indexed ? "; input index used." : "; input scanned linearly."), List.of(),
                    new ResultTable(List.of("Metric", "Value"),
                            List.of(List.of("Records", Long.toString(records)))),
                    artifacts,
                    Map.of("records", records, "format", bam ? "BAM" : "SAM",
                            "inputIndexUsed", indexed, "outputIndexCreated",
                            createIndex && index != null && Files.exists(index),
                            "threads", threads,
                            "parallelRegions", parallelRegions));
        }
    }

    static final class BamCoverageTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
                "hts.bam.coverage", "BAM Coverage",
                "Compute per-base covered depth for BED regions, optionally counting overlapping mates once.",
                "HTS", List.of("BAM coverage", "覆盖度"),
                List.of("SAM", "BAM", "CRAM", "BED"), List.of("BEDGraph"), "1.3.0",
                List.of(ToolParameter.requiredFile("input", "Input alignment", "SAM, BAM, or CRAM."),
                        ToolParameter.optionalFile("reference", "Reference FASTA", "Required for CRAM when not embedded."),
                        ToolParameter.requiredFile("regions", "BED regions", "Regions for per-base coverage."),
                        ToolParameter.integer("threads", "Threads",
                                "0 uses AUTO for large indexed alignments; 1 disables parallel regions.",
                                0, 0, 64),
                        ToolParameter.bool("countOverlappingPairsOnce", "Count overlapping pairs once",
                                "Count a read pair's overlapping reference bases once; slower than the default event path.",
                                false),
                        ToolParameter.optionalOutputFile("output", "Output BEDGraph", "Leave blank to save in the run directory."),
                        ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)));

        @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
        @Override public ValidationResult validate(ToolRequest request) {
            return validateParameters(request).merge(FormatToolSupport.validateOutput(request, request.path("input")));
        }
        @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
            List<BedRegion> regions = bedRegions(request.path("regions"));
            int threads = OrderedParallelProcessor.resolveThreads(
                    request.integer("threads"), Files.size(request.path("input")),
                    32L * 1024 * 1024);
            boolean indexed;
            try (SamReader reader = sam(request.path("input"), request.path("reference"))) {
                indexed = reader.hasIndex();
                if (!indexed) {
                    if (request.bool("countOverlappingPairsOnce")) {
                        for (BedRegion region : regions) {
                            addDeduplicatedCoverage(reader, region, context);
                        }
                    } else {
                        for (SAMRecord record : reader) {
                            if (record.getReadUnmappedFlag()) {
                                continue;
                            }
                            context.cancellationToken().throwIfCancelled();
                            addCoverage(regions, record);
                        }
                    }
                } else if (threads == 1 && request.bool("countOverlappingPairsOnce")) {
                    for (BedRegion region : regions) {
                        addDeduplicatedCoverage(reader, region, context);
                    }
                } else if (threads == 1) {
                    for (BedRegion region : regions) {
                        queryCoverage(reader, region, context);
                    }
                }
            }
            if (indexed && threads > 1) {
                addParallelIndexedCoverage(
                        request.path("input"),
                        request.path("reference"),
                        regions,
                        request.bool("countOverlappingPairsOnce"),
                        threads,
                        context
                );
            }
            Path output = FormatToolSupport.outputPath(request, context, "coverage.bedgraph");
            long[] bases = new long[2];
            FormatToolSupport.writeAtomically(output, writer -> {
                for (BedRegion region : regions) {
                    CoverageStats stats = writeCoverageRuns(writer, region);
                    bases[0] += stats.bases();
                    bases[1] += stats.depthBases();
                }
            });
            context.progress(1.0, "BAM coverage completed");
            return new ToolResult("Calculated coverage for " + regions.size() + " BED regions", List.of(regions.size()),
                    bases[0] + " bases searched; mean depth " + String.format(Locale.ROOT, "%.3f",
                            bases[1] / (double) Math.max(1, bases[0]))
                            + (indexed ? "; input index used." : "; input scanned linearly."), List.of(bases[0]),
                    new ResultTable(List.of("Metric", "Value"), List.of(
                            List.of("Regions", Integer.toString(regions.size())),
                            List.of("Bases", Long.toString(bases[0])))),
                    List.of(new OutputArtifact(output, "BEDGraph coverage", "text/plain")),
                    Map.of("regions", regions.size(), "bases", bases[0],
                            "inputIndexUsed", indexed, "coverageMemory", "interval-events",
                            "threads", threads, "parallelRegions", indexed && threads > 1,
                            "overlappingPairsCountedOnce",
                            request.bool("countOverlappingPairsOnce")));
        }
    }

    static final class VcfStatsTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
                "hts.vcf.stats", "VCF Statistics", "Summarize variants, samples, SNPs, indels, transitions, and transversions.",
                "HTS", List.of("VCF stats", "变异统计"),
                List.of("VCF", "VCF.gz", "BCF"), List.of("TSV"), "1.1.0",
                List.of(ToolParameter.requiredFile("input", "Input VCF/BCF", "VCF, bgzipped VCF, or BCF."),
                        ToolParameter.integer("threads", "Threads",
                                "0 uses AUTO for large files; 1 disables parallel variant processing.",
                                0, 0, 64),
                        ToolParameter.optionalOutputFile("output", "Output TSV", "Leave blank to save in the run directory."),
                        ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)));

        @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
        @Override public ValidationResult validate(ToolRequest request) {
            return validateParameters(request).merge(FormatToolSupport.validateOutput(request, request.path("input")));
        }
        @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
            Path output = FormatToolSupport.outputPath(request, context, "vcf-stats.tsv");
            int threads = OrderedParallelProcessor.resolveThreads(
                    request.integer("threads"), Files.size(request.path("input")),
                    32L * 1024 * 1024);
            long[] totals = new long[9];
            VcfSummary summary = new VcfSummary();
            try (VCFFileReader reader = new VCFFileReader(request.path("input"), false)) {
                VCFHeader header = reader.getFileHeader();
                totals[8] = header.getSampleNamesInOrder().size();
                var variants = reader.iterator();
                OrderedParallelProcessor.processChunks(
                        threads,
                        256,
                        context.cancellationToken(),
                        () -> variants.hasNext() ? variants.next() : null,
                        chunk -> {
                            VcfSummary local = new VcfSummary();
                            for (VariantContext variant : chunk) {
                                local.add(variant);
                            }
                            return local;
                        },
                        summary::merge
                );
            }
            totals[0] = summary.variants;
            totals[1] = summary.snps;
            totals[2] = summary.indels;
            totals[3] = summary.biallelic;
            totals[4] = summary.other;
            totals[5] = summary.transitions;
            totals[6] = summary.transversions;
            totals[7] = summary.missingQual;
            if (totals[0] == 0) {
                throw new OpenToolException("EMPTY_VCF", "No variant records were found.", "Check the VCF/BCF.");
            }
            FormatToolSupport.writeAtomically(output, writer -> {
                writer.write("metric\tvalue\n");
                String[] labels = {"variants", "snps", "indels", "biallelic", "other",
                        "transitions", "transversions", "missing_qual", "samples"};
                for (int index = 0; index < labels.length; index++) {
                    writer.write(labels[index] + "\t" + totals[index] + "\n");
                }
                writer.write("ts_tv\t" + (totals[6] == 0 ? "NA"
                        : String.format(Locale.ROOT, "%.4f", totals[5] / (double) totals[6])) + "\n");
            });
            context.progress(1.0, "VCF statistics completed");
            return new ToolResult("Summarized " + totals[0] + " variant records", List.of(totals[0]),
                    totals[1] + " SNPs and " + totals[2] + " indels.",
                    List.of(totals[1], totals[2]),
                    new ResultTable(List.of("Metric", "Value"), List.of(
                            List.of("Variants", Long.toString(totals[0])),
                            List.of("SNPs", Long.toString(totals[1])),
                            List.of("Indels", Long.toString(totals[2])),
                            List.of("Samples", Long.toString(totals[8])))),
                    List.of(new OutputArtifact(output, "VCF statistics", "text/tab-separated-values")),
                    Map.of("variants", totals[0], "snps", totals[1], "indels", totals[2],
                            "samples", totals[8], "threads", threads,
                            "parallelRecords", threads > 1));
        }

        private static final class VcfSummary {
            private long variants;
            private long snps;
            private long indels;
            private long biallelic;
            private long other;
            private long transitions;
            private long transversions;
            private long missingQual;

            private void add(VariantContext variant) {
                variants++;
                if (variant.isSNP()) snps++;
                if (variant.isIndel()) indels++;
                if (variant.isBiallelic()) biallelic++;
                if (!(variant.isSNP() || variant.isIndel())) other++;
                if (variant.isSNP()
                        && variant.getReference().length() == 1
                        && variant.getAlternateAlleles().size() == 1
                        && variant.getAlternateAllele(0).length() == 1) {
                    String change = variant.getReference().getBaseString()
                            + variant.getAlternateAllele(0).getBaseString();
                    if (List.of("AG", "GA", "CT", "TC").contains(change)) {
                        transitions++;
                    } else if (List.of("AC", "CA", "AT", "TA",
                            "CG", "GC", "GT", "TG").contains(change)) {
                        transversions++;
                    }
                }
                if (Double.isNaN(variant.getPhredScaledQual())) missingQual++;
            }

            private void merge(VcfSummary other) {
                variants += other.variants;
                snps += other.snps;
                indels += other.indels;
                biallelic += other.biallelic;
                this.other += other.other;
                transitions += other.transitions;
                transversions += other.transversions;
                missingQual += other.missingQual;
            }
        }
    }

    static final class VcfFilterTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
                "hts.vcf.filter", "Filter VCF",
                "Filter VCF/BCF and optionally create a TBI or CSI index for compressed output.",
                "HTS", List.of("VCF filter", "变异过滤"),
                List.of("VCF", "VCF.gz", "BCF"), List.of("VCF", "VCF.gz", "BCF"), "1.3.0",
                List.of(ToolParameter.requiredFile("input", "Input VCF/BCF", "Input variants."),
                        new ToolParameter("variantType", "Variant type", "Variant class filter.", ParameterType.ENUM,
                                false, "ALL", List.of("ALL", "SNP", "INDEL"), null, null, false, null),
                        ToolParameter.decimal("minQual", "Minimum QUAL", "Minimum Phred-scaled QUAL.", 0.0, 0.0, 1e9),
                        ToolParameter.decimal("maxMissing", "Maximum missing fraction", "Maximum no-call genotype fraction.", 1.0, 0.0, 1.0),
                        ToolParameter.bool("biallelicOnly", "Biallelic only", "Keep only biallelic sites.", false),
                        ToolParameter.integer("threads", "Threads",
                                "0 uses AUTO for large files; 1 disables parallel variant decisions.",
                                0, 0, 64),
                        new ToolParameter("indexType", "Output index",
                                "AUTO indexes compressed VCF with TBI and BCF with CSI; NONE disables index creation.",
                                ParameterType.ENUM, false, "AUTO",
                                List.of("AUTO", "TBI", "CSI", "NONE"),
                                null, null, false, null),
                        ToolParameter.optionalOutputFile("output", "Output VCF", "Leave blank to save in the run directory."),
                        ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)));

        @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
        @Override public ValidationResult validate(ToolRequest request) {
            ValidationResult result = validateParameters(request)
                    .merge(FormatToolSupport.validateOutput(request, request.path("input")));
            Path output = request.path("output");
            if (output != null) {
                try {
                    indexType(request.string("indexType"), output);
                } catch (OpenToolException ex) {
                    result = result.merge(ValidationResult.of(ValidationIssue.error(
                            "indexType", ex.getMessage(), ex.suggestion())));
                }
            }
            return result;
        }
        @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
            Path output = FormatToolSupport.outputPath(request, context, "filtered.vcf");
            String indexType = indexType(request.string("indexType"), output);
            int threads = OrderedParallelProcessor.resolveThreads(
                    request.integer("threads"), Files.size(request.path("input")),
                    32L * 1024 * 1024);
            long[] totals = new long[2];
            final SAMSequenceDictionary dictionary;
            try (VCFFileReader validationReader = new VCFFileReader(request.path("input"), false)) {
                dictionary = !"NONE".equals(indexType)
                        ? requireIndexDictionary(validationReader.getFileHeader())
                        : null;
            }
            Path stagedOutput = stageSibling(output, "");
            Path stagedIndex = null;
            Path index = null;
            try {
                FormatToolSupport.createParent(output);
                ensurePrimaryTarget(
                        output,
                        request.bool("overwrite"),
                        "VCF_OUTPUT_TARGET_CONFLICT",
                        "Variant output");
                if (!"NONE".equals(indexType)) {
                    index = variantIndexPath(output, indexType);
                    ensureSidecarTarget(
                            index,
                            request.bool("overwrite"),
                            "VCF_INDEX_TARGET_CONFLICT",
                            indexType + " index");
                }
                try (VCFFileReader reader = new VCFFileReader(request.path("input"), false)) {
                    VCFHeader header = reader.getFileHeader();
                    int[] order = {-1, -1};
                    VariantContextWriterBuilder builder = new VariantContextWriterBuilder()
                            .setOutputPath(stagedOutput)
                            .clearOptions();
                    if (header.getSequenceDictionary() != null) {
                        builder.setReferenceDictionary(header.getSequenceDictionary());
                    }
                    if (!"NONE".equals(indexType)) {
                        builder.setOption(Options.INDEX_ON_THE_FLY);
                        builder.setTabixIndexType("CSI".equals(indexType)
                                ? TabixIndexType.CSI : TabixIndexType.TBI);
                    }
                    try (VariantContextWriter writer = builder.build()) {
                        writer.writeHeader(header);
                        var variants = reader.iterator();
                        OrderedParallelProcessor.processChunks(
                                threads,
                                128,
                                context.cancellationToken(),
                                () -> variants.hasNext() ? variants.next() : null,
                                chunk -> {
                                    List<VariantContext> retained = new ArrayList<>();
                                    for (VariantContext variant : chunk) {
                                        if (keep(request, variant)) {
                                            retained.add(variant);
                                        }
                                    }
                                    return new VcfFilterChunk(
                                            List.copyOf(retained), chunk.size(), retained.size());
                                },
                                chunk -> {
                                    totals[0] += chunk.input();
                                    totals[1] += chunk.retained();
                                    for (VariantContext variant : chunk.variants()) {
                                        if (dictionary != null) {
                                            ensureDictionaryOrder(dictionary, variant, order);
                                        }
                                        writer.add(variant);
                                    }
                                }
                        );
                    }
                }
                if (!"NONE".equals(indexType)) {
                    stagedIndex = locateIndex(stagedOutput, indexType);
                    if (stagedIndex == null) {
                        throw new OpenToolException(
                                "VCF_INDEX_MISSING",
                                "The VCF writer did not create the requested " + indexType + " index.",
                                "Use a compressed VCF (.vcf.gz) or BCF output and check HTSJDK index support."
                        );
                    }
                    publishPrimaryWithSidecar(
                            stagedOutput, output, stagedIndex, index);
                } else {
                    moveAtomically(stagedOutput, output);
                }
            } finally {
                Files.deleteIfExists(stagedOutput);
                if (stagedIndex != null) {
                    Files.deleteIfExists(stagedIndex);
                }
            }
            context.progress(1.0, "VCF filter completed");
            List<OutputArtifact> artifacts = new ArrayList<>();
            artifacts.add(new OutputArtifact(output, "Filtered variants", mediaType(output)));
            if (index != null) {
                artifacts.add(new OutputArtifact(index, "Variant index", "application/octet-stream"));
            }
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("inputVariants", totals[0]);
            metrics.put("retained", totals[1]);
            metrics.put("indexType", indexType);
            metrics.put("indexCreated", index != null && Files.isRegularFile(index));
            metrics.put("threads", threads);
            metrics.put("parallelRecords", threads > 1);
            if (index != null) {
                metrics.put("indexPath", index.toString());
            }
            return new ToolResult("Retained " + totals[1] + " of " + totals[0] + " variants", List.of(totals[1], totals[0]),
                    "Filtered " + (totals[0] - totals[1]) + " variants.", List.of(totals[0] - totals[1]),
                    new ResultTable(List.of("Metric", "Value"), List.of(
                            List.of("Input", Long.toString(totals[0])),
                            List.of("Retained", Long.toString(totals[1])))),
                    artifacts,
                    metrics);
        }

        private static Path variantIndexPath(Path output, String type) {
            String suffix = "CSI".equals(type) ? ".csi" : ".tbi";
            return output.resolveSibling(output.getFileName().toString() + suffix);
        }

        private static SAMSequenceDictionary requireIndexDictionary(VCFHeader header) {
            var dictionary = header.getSequenceDictionary();
            if (dictionary == null) {
                throw new OpenToolException(
                        "VCF_INDEX_DICTIONARY_REQUIRED",
                        "An indexed VCF/BCF output requires a sequence dictionary in the header.",
                        "Add ##contig lines or use a sequence dictionary."
                );
            }
            return dictionary;
        }

        private static void ensureDictionaryOrder(
                SAMSequenceDictionary dictionary,
                VariantContext variant,
                int[] previous
        ) {
            int rank = dictionary.getSequenceIndex(variant.getContig());
            if (rank < 0) {
                throw new OpenToolException(
                        "VCF_INDEX_CONTIG_MISSING",
                        "Variant contig is absent from the sequence dictionary: " + variant.getContig(),
                        "Make the VCF contig names match the dictionary."
                );
            }
            if (rank < previous[0] || (rank == previous[0]
                    && variant.getStart() < previous[1])) {
                throw new OpenToolException(
                        "VCF_NOT_SORTED",
                        "Variants are not sorted in dictionary order; an index cannot be created safely.",
                        "Sort the VCF/BCF before filtering with index creation."
                );
            }
            previous[0] = rank;
            previous[1] = variant.getStart();
        }

        private static String indexType(String requested, Path output) {
            String name = output.getFileName().toString().toLowerCase(Locale.ROOT);
            boolean bcf = name.endsWith(".bcf");
            boolean compressedVcf = name.endsWith(".vcf.gz") || name.endsWith(".vcf.bgz");
            String selected = requested == null || requested.isBlank()
                    ? "AUTO" : requested.toUpperCase(Locale.ROOT);
            if ("NONE".equals(selected)) {
                return "NONE";
            }
            if ("AUTO".equals(selected)) {
                return bcf ? "CSI" : compressedVcf ? "TBI" : "NONE";
            }
            if ("TBI".equals(selected) && !compressedVcf) {
                throw new OpenToolException(
                        "VCF_INDEX_FORMAT",
                        "TBI output requires a compressed .vcf.gz or .vcf.bgz path.",
                        "Use CSI, BCF, or NONE."
                );
            }
            if ("CSI".equals(selected) && !(bcf || compressedVcf)) {
                throw new OpenToolException(
                        "VCF_INDEX_FORMAT",
                        "CSI output requires a compressed VCF or BCF path.",
                        "Choose a .vcf.gz/.vcf.bgz/.bcf output or NONE."
                );
            }
            if (!"TBI".equals(selected) && !"CSI".equals(selected)) {
                throw new OpenToolException(
                        "VCF_INDEX_FORMAT",
                        "Unsupported output index type: " + selected,
                        "Use AUTO, TBI, CSI, or NONE."
                );
            }
            return selected;
        }

        private static Path locateIndex(Path output, String type) {
            String suffix = "CSI".equals(type) ? ".csi" : ".tbi";
            String name = output.getFileName().toString();
            List<Path> candidates = List.of(
                    output.resolveSibling(name + suffix),
                    output.resolveSibling(stripVariantSuffix(name) + suffix)
            );
            return candidates.stream().filter(Files::isRegularFile).findFirst().orElse(null);
        }

        private static String stripVariantSuffix(String name) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (String suffix : List.of(".vcf.gz", ".vcf.bgz", ".bcf", ".vcf")) {
                if (lower.endsWith(suffix)) {
                    return name.substring(0, name.length() - suffix.length());
                }
            }
            return name;
        }

        private static String mediaType(Path output) {
            String name = output.getFileName().toString().toLowerCase(Locale.ROOT);
            return name.endsWith(".bcf") ? "application/x-bcf"
                    : name.endsWith(".gz") || name.endsWith(".bgz")
                    ? "application/gzip" : "text/x-vcf";
        }

        private static boolean keep(ToolRequest request, VariantContext variant) {
            double qual = variant.getPhredScaledQual();
            if (variant.hasLog10PError() && qual < parseDouble(request, "minQual")) return false;
            if (request.bool("biallelicOnly") && !variant.isBiallelic()) return false;
            if ("SNP".equals(request.string("variantType")) && !variant.isSNP()) return false;
            if ("INDEL".equals(request.string("variantType")) && !variant.isIndel()) return false;
            if (variant.getNSamples() > 0) {
                long missing = variant.getGenotypes().stream().filter(gt -> gt.isNoCall()).count();
                if (missing / (double) variant.getNSamples() > parseDouble(request, "maxMissing")) return false;
            }
            return true;
        }
    }

    static final class BedCoverageTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
                "bed.coverage", "BED Mean Coverage", "Calculate mean depth for each BED interval from SAM/BAM/CRAM.",
                "HTS", List.of("BED coverage", "区间覆盖度"),
                List.of("BED", "SAM", "BAM", "CRAM"), List.of("TSV"), "1.3.0",
                List.of(ToolParameter.requiredFile("alignment", "Alignment", "SAM, BAM, or CRAM."),
                        ToolParameter.optionalFile("reference", "Reference FASTA", "Required for CRAM when not embedded."),
                        ToolParameter.requiredFile("regions", "BED regions", "Intervals to summarize."),
                        ToolParameter.integer("threads", "Threads",
                                "0 uses AUTO for large indexed alignments; 1 disables parallel regions.",
                                0, 0, 64),
                        ToolParameter.bool("countOverlappingPairsOnce", "Count overlapping pairs once",
                                "Count a read pair's overlapping reference bases once; slower than the default event path.",
                                false),
                        ToolParameter.optionalOutputFile("output", "Output TSV", "Leave blank to save in the run directory."),
                        ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)));

        @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
        @Override public ValidationResult validate(ToolRequest request) {
            return validateParameters(request).merge(FormatToolSupport.validateOutput(request, request.path("alignment")));
        }
        @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
            List<BedRegion> regions = bedRegions(request.path("regions"));
            int threads = OrderedParallelProcessor.resolveThreads(
                    request.integer("threads"), Files.size(request.path("alignment")),
                    32L * 1024 * 1024);
            boolean indexed;
            try (SamReader reader = sam(request.path("alignment"), request.path("reference"))) {
                indexed = reader.hasIndex();
                if (!indexed) {
                    if (request.bool("countOverlappingPairsOnce")) {
                        for (BedRegion region : regions) {
                            addDeduplicatedCoverage(reader, region, context);
                        }
                    } else {
                        for (SAMRecord record : reader) {
                            if (record.getReadUnmappedFlag()) {
                                continue;
                            }
                            context.cancellationToken().throwIfCancelled();
                            addCoverage(regions, record);
                        }
                    }
                } else if (threads == 1 && request.bool("countOverlappingPairsOnce")) {
                    for (BedRegion region : regions) {
                        addDeduplicatedCoverage(reader, region, context);
                    }
                } else if (threads == 1) {
                    for (BedRegion region : regions) {
                        queryCoverage(reader, region, context);
                    }
                }
            }
            if (indexed && threads > 1) {
                addParallelIndexedCoverage(
                        request.path("alignment"),
                        request.path("reference"),
                        regions,
                        request.bool("countOverlappingPairsOnce"),
                        threads,
                        context
                );
            }
            Path output = FormatToolSupport.outputPath(request, context, "bed-coverage.tsv");
            FormatToolSupport.writeAtomically(output, writer -> {
                writer.write("seq_id\tstart\tend\tmean_coverage\tcovered_bases\n");
                for (BedRegion region : regions) {
                    CoverageStats stats = summarizeCoverage(region);
                    writer.write(region.contig + "\t" + (region.start - 1) + "\t" + region.end + "\t"
                            + String.format(Locale.ROOT, "%.6f",
                            stats.depthBases() / (double) Math.max(1, stats.bases()))
                            + "\t" + stats.coveredBases() + "\n");
                }
            });
            context.progress(1.0, "BED coverage completed");
            return new ToolResult("Calculated mean coverage for " + regions.size() + " BED intervals", List.of(regions.size()), "Per-interval mean depth reported.", List.of(),
                    new ResultTable(List.of("Metric", "Value"),
                            List.of(List.of("Regions", Integer.toString(regions.size())))),
                    List.of(new OutputArtifact(output, "BED coverage", "text/tab-separated-values")),
                    Map.of("regions", regions.size(), "inputIndexUsed", indexed,
                            "coverageMemory", "interval-events",
                            "threads", threads, "parallelRegions", indexed && threads > 1,
                            "overlappingPairsCountedOnce",
                            request.bool("countOverlappingPairsOnce")));
        }
    }

    private static long writeSequentialSubset(
            Path input,
            Path reference,
            SAMFileHeader inputHeader,
            boolean indexed,
            List<Region> regions,
            Path stagedOutput,
            boolean createIndex,
            Path workDirectory,
            ToolExecutionContext context
    ) throws Exception {
        SAMFileHeader outputHeader = inputHeader.clone();
        outputHeader.setSortOrder(SortOrder.coordinate);
        Map<String, List<Region>> byContig = regionsByContig(regions);
        long records = 0;
        try (SamReader reader = sam(input, reference);
             SAMFileWriter writer = finalAlignmentWriter(
                     outputHeader, indexed, createIndex, stagedOutput, workDirectory)) {
            if (indexed) {
                for (Region region : regions) {
                    context.cancellationToken().throwIfCancelled();
                    try (CloseableIterator<SAMRecord> iterator = reader.queryOverlapping(
                            region.contig, (int) region.start, (int) region.end)) {
                        while (iterator.hasNext()) {
                            context.cancellationToken().throwIfCancelled();
                            SAMRecord record = iterator.next();
                            if (isCanonicalRegion(record, region, byContig)) {
                                writer.addAlignment(record);
                                records++;
                            }
                        }
                    }
                }
            } else {
                for (SAMRecord record : reader) {
                    context.cancellationToken().throwIfCancelled();
                    if (!record.getReadUnmappedFlag() && matches(regions, record)) {
                        writer.addAlignment(record);
                        records++;
                    }
                }
            }
        }
        return records;
    }

    private static OpenToolException translateReferenceFailure(
            Path input,
            Path reference,
            IllegalArgumentException failure
    ) {
        String name = input.getFileName().toString().toLowerCase(Locale.ROOT);
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        if (reference == null && name.endsWith(".cram")
                && message.toLowerCase(Locale.ROOT).contains("reference")) {
            return new OpenToolException(
                    "CRAM_REFERENCE_REQUIRED",
                    "The CRAM input requires a reference FASTA containing its sequences.",
                    "Provide the reference parameter or use a CRAM with embedded reference bases."
            );
        }
        return null;
    }

    private static long writeParallelIndexedSubset(
            Path input,
            Path reference,
            SAMFileHeader inputHeader,
            List<Region> regions,
            Path stagedOutput,
            boolean createIndex,
            int threads,
            Path workDirectory,
            ConcurrentLinkedQueue<Path> workerOutputs,
            ToolExecutionContext context
    ) throws Exception {
        Map<String, List<Region>> byContig = regionsByContig(regions);
        java.util.Iterator<Region> iterator = regions.iterator();
        int batchSize = Math.max(1, (regions.size() + threads - 1) / threads);
        List<WorkerAlignmentOutput> sortedWorkers = new ArrayList<>();
        OrderedParallelProcessor.processChunks(
                threads,
                batchSize,
                context.cancellationToken(),
                () -> iterator.hasNext() ? iterator.next() : null,
                chunk -> queryIndexedChunkToBam(
                        input,
                        reference,
                        inputHeader,
                        chunk,
                        byContig,
                        workDirectory,
                        workerOutputs,
                        context
                ),
                sortedWorkers::add
        );

        SAMFileHeader outputHeader = inputHeader.clone();
        outputHeader.setSortOrder(SortOrder.coordinate);
        List<SamReader> readers = new ArrayList<>();
        Map<SamReader, CloseableIterator<SAMRecord>> iterators = new LinkedHashMap<>();
        long records = 0;
        try {
            for (WorkerAlignmentOutput worker : sortedWorkers) {
                SamReader reader = sam(worker.path(), null);
                readers.add(reader);
                iterators.put(reader, reader.iterator());
            }
            SamFileHeaderMerger headerMerger = new SamFileHeaderMerger(
                    readers, SortOrder.coordinate, false);
            try (MergingSamRecordIterator merged = new MergingSamRecordIterator(
                    headerMerger, iterators, true);
                 SAMFileWriter writer = finalAlignmentWriter(
                         outputHeader, true, createIndex, stagedOutput, workDirectory)) {
                while (merged.hasNext()) {
                    context.cancellationToken().throwIfCancelled();
                    writer.addAlignment(merged.next());
                    records++;
                }
            }
        } finally {
            for (CloseableIterator<SAMRecord> iteratorToClose : iterators.values()) {
                iteratorToClose.close();
            }
            for (SamReader reader : readers) {
                reader.close();
            }
        }
        return records;
    }

    private static WorkerAlignmentOutput queryIndexedChunkToBam(
            Path input,
            Path reference,
            SAMFileHeader inputHeader,
            List<Region> regions,
            Map<String, List<Region>> byContig,
            Path workDirectory,
            ConcurrentLinkedQueue<Path> workerOutputs,
            ToolExecutionContext context
    ) throws Exception {
        Path output = workDirectory.resolve("query-" + UUID.randomUUID() + ".bam");
        workerOutputs.add(output);
        SAMFileHeader workerHeader = inputHeader.clone();
        workerHeader.setSortOrder(SortOrder.coordinate);
        long records = 0;
        try (SamReader reader = sam(input, reference);
             SAMFileWriter writer = new SAMFileWriterFactory()
                     .setCreateIndex(false)
                     .makeWriter(workerHeader, true, output, null)) {
            if (!reader.hasIndex()) {
                throw new OpenToolException(
                        "ALIGNMENT_INDEX_UNAVAILABLE",
                        "The alignment index became unavailable during parallel subsetting.",
                        "Recreate the index or set threads=1."
                );
            }
            for (Region region : regions) {
                context.cancellationToken().throwIfCancelled();
                try (CloseableIterator<SAMRecord> iterator = reader.queryOverlapping(
                        region.contig, (int) region.start, (int) region.end)) {
                    while (iterator.hasNext()) {
                        context.cancellationToken().throwIfCancelled();
                        SAMRecord record = iterator.next();
                        if (isCanonicalRegion(record, region, byContig)) {
                            writer.addAlignment(record);
                            records++;
                        }
                    }
                }
            }
        }
        return new WorkerAlignmentOutput(output, records);
    }

    private static SAMFileWriter finalAlignmentWriter(
            SAMFileHeader header,
            boolean presorted,
            boolean createIndex,
            Path output,
            Path workDirectory
    ) {
        return new SAMFileWriterFactory()
                .setCreateIndex(createIndex)
                .setMaxRecordsInRam(100_000)
                .setTempDirectory(workDirectory)
                .makeWriter(header, presorted, output, null);
    }

    private static Map<String, List<Region>> regionsByContig(List<Region> regions) {
        Map<String, List<Region>> result = new LinkedHashMap<>();
        for (Region region : regions) {
            result.computeIfAbsent(region.contig(), ignored -> new ArrayList<>()).add(region);
        }
        result.replaceAll((ignored, values) -> List.copyOf(values));
        return Map.copyOf(result);
    }

    private static boolean isCanonicalRegion(
            SAMRecord record,
            Region queried,
            Map<String, List<Region>> byContig
    ) {
        if (record.getReadUnmappedFlag()) {
            return false;
        }
        List<Region> contigRegions = byContig.get(record.getReferenceName());
        if (contigRegions == null || contigRegions.isEmpty()) {
            return false;
        }
        int low = 0;
        int high = contigRegions.size() - 1;
        int canonical = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (contigRegions.get(middle).end() >= record.getAlignmentStart()) {
                canonical = middle;
                high = middle - 1;
            } else {
                low = middle + 1;
            }
        }
        if (canonical < 0) {
            return false;
        }
        Region owner = contigRegions.get(canonical);
        return owner.start() <= record.getAlignmentEnd() && owner.equals(queried);
    }

    private static List<Region> orderRegionsForHeader(
            List<Region> regions,
            SAMFileHeader header
    ) {
        SAMSequenceRecord[] sequences = header.getSequenceDictionary() == null
                ? new SAMSequenceRecord[0]
                : header.getSequenceDictionary().getSequences().toArray(SAMSequenceRecord[]::new);
        Map<String, Integer> order = new LinkedHashMap<>();
        for (int index = 0; index < sequences.length; index++) {
            order.put(sequences[index].getSequenceName(), index);
        }
        List<Region> ordered = new ArrayList<>(regions);
        for (Region region : ordered) {
            validateQueryRegion(region.contig(), region.start(), region.end());
            if (!order.containsKey(region.contig())) {
                throw new OpenToolException(
                        "ALIGNMENT_CONTIG_MISSING",
                        "Query contig is absent from the alignment sequence dictionary: " + region.contig(),
                        "Check the region name or the alignment header."
                );
            }
        }
        ordered.sort(Comparator.comparingInt((Region region) -> order.get(region.contig()))
                .thenComparingLong(Region::start)
                .thenComparingLong(Region::end));
        return List.copyOf(ordered);
    }

    private static Path locateCreatedBamIndex(Path bam) {
        String fileName = bam.getFileName().toString();
        String stem = fileName.toLowerCase(Locale.ROOT).endsWith(".bam")
                ? fileName.substring(0, fileName.length() - 4) : fileName;
        for (Path candidate : List.of(
                bam.resolveSibling(stem + ".bai"),
                bam.resolveSibling(fileName + ".bai"))) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static Path stageSibling(Path target, String suffix) {
        Path absolute = target.toAbsolutePath().normalize();
        return absolute.getParent().resolve(
                ".opentools-" + UUID.randomUUID() + "-" + absolute.getFileName() + suffix);
    }

    private static void moveAtomically(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void ensureSidecarTarget(
            Path path,
            boolean overwrite,
            String conflictCode,
            String label
    ) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new OpenToolException(
                    conflictCode,
                    label + " target is not a regular file: " + path,
                    "Remove or rename the conflicting path."
            );
        }
        if (!overwrite) {
            throw new OpenToolException(
                    conflictCode,
                    label + " target already exists: " + path,
                    "Choose another output name or enable overwrite."
            );
        }
    }

    private static void ensurePrimaryTarget(
            Path path,
            boolean overwrite,
            String conflictCode,
            String label
    ) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new OpenToolException(
                    conflictCode,
                    label + " target is not a regular file: " + path,
                    "Remove or rename the conflicting path."
            );
        }
        if (!overwrite) {
            throw new OpenToolException(
                    conflictCode,
                    label + " target already exists: " + path,
                    "Choose another output name or enable overwrite."
            );
        }
    }

    private static void publishPrimaryWithSidecar(
            Path stagedPrimary,
            Path primary,
            Path stagedSidecar,
            Path sidecar
    ) throws Exception {
        Path primaryBackup = null;
        Path sidecarBackup = null;
        boolean primaryPublished = false;
        boolean sidecarPublished = false;
        try {
            primaryBackup = backupExisting(primary);
            sidecarBackup = backupExisting(sidecar);
            moveAtomically(stagedPrimary, primary);
            primaryPublished = true;
            moveAtomically(stagedSidecar, sidecar);
            sidecarPublished = true;
        } catch (Throwable failure) {
            rollbackPublished(sidecar, sidecarPublished, sidecarBackup, failure);
            rollbackPublished(primary, primaryPublished, primaryBackup, failure);
            rethrow(failure);
        } finally {
            deleteIfExistsQuietly(primaryBackup);
            deleteIfExistsQuietly(sidecarBackup);
        }
    }

    private static Path backupExisting(Path target) throws Exception {
        if (!Files.exists(target)) {
            return null;
        }
        Path backup = target.resolveSibling(".opentools-backup-" + UUID.randomUUID());
        moveAtomically(target, backup);
        return backup;
    }

    private static void rollbackPublished(
            Path target,
            boolean published,
            Path backup,
            Throwable failure
    ) {
        try {
            if (published) {
                Files.deleteIfExists(target);
            }
            if (backup != null && Files.exists(backup)) {
                moveAtomically(backup, target);
            }
        } catch (Exception rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException(failure);
    }

    private static void deleteIfExistsQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    private static void deleteRecursivelyQuietly(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static List<Region> parseRegions(String text) {
        List<Region> regions = new ArrayList<>();
        for (String line : text.lines().map(String::trim).filter(value -> !value.isBlank()).toList()) {
            int colon = line.lastIndexOf(':');
            int dash = line.lastIndexOf('-');
            if (colon <= 0 || dash <= colon) {
                throw new OpenToolException("REGION_FORMAT_ERROR",
                        "Invalid region: " + line, "Use CONTIG:START-END with 1-based inclusive coordinates.");
            }
            regions.add(new Region(line.substring(0, colon),
                    Long.parseLong(line.substring(colon + 1, dash)),
                    Long.parseLong(line.substring(dash + 1))));
        }
        return regions;
    }

    private static List<Region> mergeRegions(List<Region> regions) {
        if (regions.isEmpty()) {
            throw new OpenToolException(
                    "REGION_REQUIRED", "At least one region is required.",
                    "Enter one CONTIG:START-END region per line."
            );
        }
        List<Region> sorted = new ArrayList<>(regions);
        sorted.sort(java.util.Comparator.comparing(Region::contig)
                .thenComparingLong(Region::start)
                .thenComparingLong(Region::end));
        List<Region> merged = new ArrayList<>();
        for (Region region : sorted) {
            validateQueryRegion(region.contig, region.start, region.end);
            if (merged.isEmpty()) {
                merged.add(region);
                continue;
            }
            Region previous = merged.getLast();
            if (previous.contig.equals(region.contig) && region.start <= previous.end + 1) {
                merged.set(merged.size() - 1, new Region(
                        previous.contig,
                        previous.start,
                        Math.max(previous.end, region.end)
                ));
            } else {
                merged.add(region);
            }
        }
        return merged;
    }

    private static List<BedRegion> bedRegions(Path path) throws Exception {
        List<BedRegion> regions = new ArrayList<>();
        try (BedReader reader = new BedReader(path)) {
            BedRecord record;
            while ((record = reader.next()) != null) {
                regions.add(new BedRegion(record.seqId(), record.start() + 1, record.end(),
                        new TreeMap<>()));
            }
        }
        if (regions.isEmpty()) {
            throw new OpenToolException(
                    "BED_EMPTY", "No BED regions were found.",
                    "Provide at least one BED interval."
            );
        }
        return regions;
    }

    private static void queryCoverage(
            SamReader reader,
            BedRegion region,
            ToolExecutionContext context
    ) throws Exception {
        validateQueryRegion(region.contig, region.start, region.end);
        try (CloseableIterator<SAMRecord> records = reader.queryOverlapping(
                region.contig, (int) region.start, (int) region.end)) {
            while (records.hasNext()) {
                context.cancellationToken().throwIfCancelled();
                SAMRecord record = records.next();
                if (!record.getReadUnmappedFlag()) {
                    addCoverage(region, record);
                }
            }
        }
    }

    private static void addParallelIndexedCoverage(
            Path alignment,
            Path reference,
            List<BedRegion> regions,
            boolean deduplicatePairs,
            int threads,
            ToolExecutionContext context
    ) throws Exception {
        java.util.Iterator<BedRegion> iterator = regions.iterator();
        OrderedParallelProcessor.processChunks(
                threads,
                4,
                context.cancellationToken(),
                () -> iterator.hasNext() ? iterator.next() : null,
                chunk -> {
                    try (SamReader worker = sam(alignment, reference)) {
                        if (!worker.hasIndex()) {
                            throw new OpenToolException(
                                    "ALIGNMENT_INDEX_UNAVAILABLE",
                                    "The alignment index became unavailable during parallel coverage.",
                                    "Recreate the index or set threads=1."
                            );
                        }
                        for (BedRegion region : chunk) {
                            if (deduplicatePairs) {
                                addDeduplicatedCoverage(worker, region, context);
                            } else {
                                queryCoverage(worker, region, context);
                            }
                        }
                    }
                    return Boolean.TRUE;
                },
                ignored -> {
                }
        );
    }

    private static void addDeduplicatedCoverage(
            SamReader reader,
            BedRegion region,
            ToolExecutionContext context
    ) {
        validateQueryRegion(region.contig, region.start, region.end);
        IntervalList intervals = new IntervalList(reader.getFileHeader());
        intervals.add(new Interval(region.contig, (int) region.start, (int) region.end));
        try (SamLocusIterator loci = new SamLocusIterator(
                reader, intervals, reader.hasIndex())) {
            loci.setIncludeNonPfReads(true);
            loci.setQualityScoreCutoff(0);
            loci.setMappingQualityScoreCutoff(0);
            loci.setEmitUncoveredLoci(false);
            int runningDepth = 0;
            long previousPosition = -1;
            while (loci.hasNext()) {
                context.cancellationToken().throwIfCancelled();
                SamLocusIterator.LocusInfo locus = loci.next();
                long position = locus.getPosition();
                if (position < region.start || position > region.end) {
                    continue;
                }
                if (previousPosition >= 0 && position != previousPosition + 1) {
                    region.events.merge(previousPosition + 1, (long) -runningDepth, Long::sum);
                    runningDepth = 0;
                }
                Set<Object> templates = new HashSet<>();
                for (SamLocusIterator.RecordAndOffset record : locus.getRecordAndOffsets()) {
                    if (record.getAlignmentType()
                            == htsjdk.samtools.util.AbstractRecordAndOffset.AlignmentType.Match) {
                        templates.add(coverageTemplateKey(record.getRecord()));
                    }
                }
                int depth = templates.size();
                if (depth != runningDepth) {
                    region.events.merge(position, (long) depth - runningDepth, Long::sum);
                    runningDepth = depth;
                }
                previousPosition = position;
            }
            if (previousPosition >= 0 && runningDepth != 0) {
                region.events.merge(previousPosition + 1, (long) -runningDepth, Long::sum);
            }
        }
    }

    private static Object coverageTemplateKey(SAMRecord record) {
        boolean primary = !record.getNotPrimaryAlignmentFlag()
                && !record.getSupplementaryAlignmentFlag();
        boolean overlappingMate = record.getReadPairedFlag()
                && primary
                && !record.getMateUnmappedFlag()
                && record.getFirstOfPairFlag() != record.getSecondOfPairFlag();
        if (overlappingMate
                && record.getReferenceName().equals(record.getMateReferenceName())) {
            String readGroup = record.getStringAttribute("RG");
            return "template:" + (readGroup == null ? "" : readGroup)
                    + "\0" + record.getReadName();
        }
        return new RecordIdentity(record);
    }

    private static void addCoverage(List<BedRegion> regions, SAMRecord record) {
        for (BedRegion region : regions) {
            if (record.getReferenceName().equals(region.contig)) {
                addCoverage(region, record);
            }
        }
    }

    private static void addCoverage(BedRegion region, SAMRecord record) {
        for (AlignmentBlock block : record.getAlignmentBlocks()) {
            long blockStart = block.getReferenceStart();
            long blockEnd = blockStart + block.getLength() - 1;
            long left = Math.max(blockStart, region.start);
            long right = Math.min(blockEnd, region.end);
            if (left <= right) {
                region.events.merge(left, 1L, Long::sum);
                region.events.merge(right + 1, -1L, Long::sum);
            }
        }
    }

    private static CoverageStats summarizeCoverage(BedRegion region) {
        long cursor = region.start;
        long depth = 0;
        long bases = 0;
        long depthBases = 0;
        long coveredBases = 0;
        region.events.putIfAbsent(region.end + 1, 0L);
        for (Map.Entry<Long, Long> event : region.events.entrySet()) {
            long coordinate = event.getKey();
            if (coordinate < region.start || coordinate > region.end + 1) {
                continue;
            }
            if (coordinate > cursor) {
                long length = coordinate - cursor;
                bases += length;
                depthBases += depth * length;
                if (depth > 0) {
                    coveredBases += length;
                }
            }
            depth += event.getValue();
            cursor = coordinate;
        }
        return new CoverageStats(bases, depthBases, coveredBases);
    }

    private static CoverageStats writeCoverageRuns(
            BufferedWriter writer,
            BedRegion region
    ) throws Exception {
        long cursor = region.start;
        long depth = 0;
        long bases = 0;
        long depthBases = 0;
        long coveredBases = 0;
        region.events.putIfAbsent(region.end + 1, 0L);
        for (Map.Entry<Long, Long> event : region.events.entrySet()) {
            long coordinate = event.getKey();
            if (coordinate < region.start || coordinate > region.end + 1) {
                continue;
            }
            if (coordinate > cursor) {
                long length = coordinate - cursor;
                writer.write(region.contig + "\t" + (cursor - 1) + "\t"
                        + (coordinate - 1) + "\t" + depth + "\n");
                bases += length;
                depthBases += depth * length;
                if (depth > 0) {
                    coveredBases += length;
                }
            }
            depth += event.getValue();
            cursor = coordinate;
        }
        return new CoverageStats(bases, depthBases, coveredBases);
    }

    private static void validateQueryRegion(String contig, long start, long end) {
        if (start < 1 || end < start || end > Integer.MAX_VALUE) {
            throw new OpenToolException(
                    "REGION_OUT_OF_RANGE",
                    "Region is outside the supported 1-based signed-integer coordinate range: "
                            + contig + ":" + start + "-" + end,
                    "Split or correct the region."
            );
        }
    }

    private static Path bamIndexPath(Path bam) {
        String fileName = bam.getFileName().toString();
        String stem = fileName.toLowerCase(Locale.ROOT).endsWith(".bam")
                ? fileName.substring(0, fileName.length() - 4) : fileName;
        Path shortName = bam.resolveSibling(stem + ".bai");
        if (Files.exists(shortName)) {
            return shortName;
        }
        return bam.resolveSibling(fileName + ".bai");
    }

    private static boolean matches(List<Region> regions, SAMRecord record) {
        return regions.stream().anyMatch(region -> region.contig.equals(record.getReferenceName())
                && record.getAlignmentStart() <= region.end
                && record.getAlignmentEnd() >= region.start);
    }

    private static double parseDouble(ToolRequest request, String id) {
        Object value = request.value(id);
        return value == null || String.valueOf(value).isBlank()
                ? 0 : Double.parseDouble(String.valueOf(value));
    }

    private record Region(String contig, long start, long end) {
    }

    private static final class BedRegion {
        private final String contig;
        private final long start;
        private final long end;
        private final TreeMap<Long, Long> events;

        private BedRegion(String contig, long start, long end, TreeMap<Long, Long> events) {
            this.contig = contig;
            this.start = start;
            this.end = end;
            this.events = events;
        }
    }

    private record CoverageStats(long bases, long depthBases, long coveredBases) {
    }

    private static final class RecordIdentity {
        private final SAMRecord record;

        private RecordIdentity(SAMRecord record) {
            this.record = record;
        }
    }

    private record WorkerAlignmentOutput(Path path, long records) {
    }

    private record VcfFilterChunk(
            List<VariantContext> variants,
            int input,
            int retained
    ) {
    }
}
