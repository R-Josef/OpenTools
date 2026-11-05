package io.opentools.analysis.sequence;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.fasta.FastaReader;
import io.opentools.formats.fasta.FastaRecord;
import io.opentools.formats.fastq.FastqReader;
import io.opentools.formats.fastq.FastqRecord;
import io.opentools.formats.sanger.Ab1Reader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Overlap-first Sanger assembly with optional reference anchoring. */
public final class SangerAssembleTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "sanger.assemble",
            "Sanger Assembly",
            "Assemble AB1, FASTQ, or FASTA Sanger reads by overlap, optionally anchor them to a reference, and report consensus provenance.",
            "Sequence",
            List.of("Sanger", "AB1", "assembly", "consensus", "测序拼接"),
            List.of("AB1", "FASTQ", "FASTA"),
            List.of("FASTA", "TSV", "BED"),
            "1.2.0",
            List.of(
                    new ToolParameter("readsDirectory", "Reads directory",
                            "Directory containing AB1, FASTQ, or FASTA reads. AB1 is preferred when present.",
                            ParameterType.DIRECTORY, true, null, List.of(), null, null, false, null),
                    ToolParameter.optionalFile("reference", "Reference FASTA",
                            "Optional reference sequence used to anchor reads and fill uncovered positions."),
                    ToolParameter.integer("minOverlap", "Minimum overlap",
                            "Minimum overlap length for joining reads.", 30, 10, 10_000),
                    ToolParameter.decimal("minIdentity", "Minimum overlap identity",
                            "Minimum fraction of matching bases in an overlap.", 0.85, 0.5, 1.0),
                    ToolParameter.integer("trimQuality", "End-trim quality",
                            "Mean Phred quality required in the end-trimming window.", 20, 0, 60),
                    ToolParameter.integer("trimWindow", "End-trim window",
                            "Sliding window used to trim low-quality read ends.", 8, 1, 100),
                    ToolParameter.integer("minReadLength", "Minimum read length",
                            "Reads shorter than this after trimming are rejected.", 50, 1, 100_000),
                    ToolParameter.bool("trimAdapters", "Trim primers/vectors",
                            "Trim common sequencing primers and optional custom vector sequences from read ends.", true),
                    ToolParameter.optionalFile("adapterFile", "Primer/vector FASTA",
                            "Optional FASTA of primer or vector sequences to trim from either read end."),
                    ToolParameter.integer("minAdapterMatch", "Minimum primer match",
                            "Minimum matching bases required for primer/vector trimming.", 12, 6, 100),
                    ToolParameter.bool("trimVectorEnds", "Trim vector ends with reference",
                            "Use the reference alignment to remove unaligned contig ends and write insert.fa.", true),
                    ToolParameter.integer("minVectorOverhang", "Minimum aligned span",
                            "Minimum aligned span retained in the vector-trimmed insert.", 20, 5, 10_000),
                    ToolParameter.decimal("consensusThreshold", "Consensus threshold",
                            "Minimum quality-weighted base fraction for a confident call.", 0.60, 0.5, 1.0),
                    ToolParameter.decimal("heterozygousThreshold", "Heterozygous threshold",
                            "Minimum second-base fraction for an IUPAC heterozygous call.", 0.25, 0.05, 0.5),
                    ToolParameter.integer("minHeterozygousCoverage", "Heterozygous coverage",
                            "Minimum coverage before a heterozygous call is considered.", 4, 2, 10_000),
                    ToolParameter.integer("minIndelSupport", "Minimum indel support",
                            "Minimum reads supporting an insertion or deletion in the expanded consensus.", 1, 1, 10_000),
                    ToolParameter.optionalDirectory("outputDirectory", "Output directory",
                            "Leave blank to save in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite outputs",
                            "Allow replacing existing output files.", false)
            )
    );

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request);
        Path reads = request.path("readsDirectory");
        if (reads != null && !Files.isDirectory(reads)) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "readsDirectory", "Reads directory does not exist: " + reads,
                    "Choose a directory containing AB1, FASTQ, or FASTA reads.")));
        }
        Path reference = request.path("reference");
        if (reference != null && !Files.isRegularFile(reference)) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "reference", "Reference FASTA does not exist: " + reference,
                    "Choose a reference FASTA or leave the parameter blank.")));
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path readsDirectory = request.path("readsDirectory");
        Path referencePath = request.path("reference");
        Path outputDirectory = request.path("outputDirectory");
        if (outputDirectory == null) outputDirectory = context.outputDirectory();
        Files.createDirectories(outputDirectory);
        Map<String, Path> outputs = outputNames(outputDirectory);
        if (!request.bool("overwrite")) {
            for (Path path : outputs.values()) {
                if (Files.exists(path)) {
                    throw new OpenToolException(
                            "SANGER_OUTPUT_EXISTS",
                            "Output already exists: " + path,
                            "Enable overwrite or choose another output directory."
                    );
                }
            }
        }

        AdapterTrimmer adapterTrimmer = AdapterTrimmer.load(
                request.bool("trimAdapters"), request.path("adapterFile"));
        int minimumAdapterMatch = request.integer("minAdapterMatch");
        if (minimumAdapterMatch <= 0) minimumAdapterMatch = 12;
        List<Path> files = readFiles(readsDirectory, referencePath);
        if (files.isEmpty()) {
            throw new OpenToolException(
                    "SANGER_NO_READS",
                    "No AB1, FASTQ, or FASTA reads were found in " + readsDirectory,
                    "Add AB1/FASTQ/FASTA reads to the directory."
            );
        }
        int trimQuality = request.integer("trimQuality");
        int trimWindow = request.integer("trimWindow");
        int minReadLength = request.integer("minReadLength");
        List<ReadRecord> reads = new ArrayList<>();
        for (Path file : files) {
            context.cancellationToken().throwIfCancelled();
            reads.addAll(loadReads(file, trimQuality, trimWindow, minReadLength,
                    adapterTrimmer, minimumAdapterMatch));
        }
        if (reads.isEmpty()) {
            throw new OpenToolException(
                    "SANGER_READS_EMPTY",
                    "All reads were empty or shorter than the minimum length after trimming.",
                    "Lower minReadLength or trimQuality."
            );
        }
        int minOverlap = request.integer("minOverlap");
        double minIdentity = decimalValue(request, "minIdentity");
        double consensusThreshold = decimalValue(request, "consensusThreshold");
        if (consensusThreshold <= 0) consensusThreshold = 0.60;
        double heterozygousThreshold = decimalValue(request, "heterozygousThreshold");
        if (heterozygousThreshold <= 0) heterozygousThreshold = 0.25;
        int minHeterozygousCoverage = request.integer("minHeterozygousCoverage");
        if (minHeterozygousCoverage <= 0) minHeterozygousCoverage = 4;
        Assembly assembly = assemble(reads, minOverlap, minIdentity, consensusThreshold);
        if (assembly.contigs().isEmpty()) {
            throw new OpenToolException(
                    "SANGER_ASSEMBLY_EMPTY",
                    "The reads did not form any contig.",
                    "Lower minOverlap/minIdentity or inspect the read quality."
            );
        }

        ReferenceAnchor anchor = null;
        FastaRecord reference = null;
        if (referencePath != null) {
            reference = readReference(referencePath);
            anchor = ReferenceAnchor.place(reference.sequence(), reads, minOverlap);
        }

        writeFasta(outputs.get("assembly"), assembly.contigs());
        List<Contig> insertContigs = trimVectorEnds(
                assembly.contigs(), reference, request.bool("trimVectorEnds"),
                request.integer("minVectorOverhang"));
        writeFasta(outputs.get("insert"), insertContigs);
        writeReadTable(outputs.get("reads"), reads, anchor);
        writeTrimming(outputs.get("trimming"), reads);
        if (reference != null && anchor != null) {
            Consensus consensus = anchor.consensus(
                    consensusThreshold, heterozygousThreshold, minHeterozygousCoverage);
            writeConsensus(outputs.get("corrected"), reference, consensus);
            writeVariants(outputs.get("variants"), reference, consensus);
            writeProvenance(outputs.get("provenance"), reference, consensus);
            writeIndels(outputs.get("indels"), consensus);
            writeExpandedConsensus(outputs.get("expanded"), reference, consensus,
                    Math.max(1, request.integer("minIndelSupport")));
        }

        List<OutputArtifact> artifacts = new ArrayList<>();
        artifacts.add(new OutputArtifact(outputs.get("assembly"), "Assembled contigs", "text/x-fasta"));
        artifacts.add(new OutputArtifact(outputs.get("insert"), "Vector-trimmed insert", "text/x-fasta"));
        artifacts.add(new OutputArtifact(outputs.get("reads"), "Read and placement summary",
                "text/tab-separated-values"));
        artifacts.add(new OutputArtifact(outputs.get("trimming"), "Primer and vector trimming",
                "text/tab-separated-values"));
        if (reference != null && anchor != null) {
            artifacts.add(new OutputArtifact(outputs.get("corrected"), "Reference-anchored consensus",
                    "text/x-fasta"));
            artifacts.add(new OutputArtifact(outputs.get("variants"), "Consensus variants",
                    "text/tab-separated-values"));
            artifacts.add(new OutputArtifact(outputs.get("provenance"), "Consensus provenance",
                    "text/plain"));
            artifacts.add(new OutputArtifact(outputs.get("indels"), "Consensus indels",
                    "text/tab-separated-values"));
            artifacts.add(new OutputArtifact(outputs.get("expanded"),
                    "Consensus with indels", "text/x-fasta"));
        }
        long totalLength = assembly.contigs().stream().mapToLong(contig -> contig.sequence().length()).sum();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("reads", reads.size());
        metrics.put("contigs", assembly.contigs().size());
        metrics.put("assembledBases", totalLength);
        metrics.put("referenceUsed", reference != null);
        metrics.put("placements", anchor == null ? 0 : anchor.placements().size());
        if (reference != null && anchor != null) {
            metrics.put("referenceLength", reference.sequence().length());
            metrics.put("coveredReferenceBases", anchor.coveredBases());
            metrics.put("heterozygousBases", anchor.consensus(
                    consensusThreshold, heterozygousThreshold, minHeterozygousCoverage)
                    .heterozygousCount());
        }
        context.progress(1.0, "Sanger assembly completed");
        return new ToolResult(
                "Assembled " + reads.size() + " reads into " + assembly.contigs().size()
                        + " contig(s)" + (reference == null ? "" : " with reference anchoring"),
                List.of(reads.size(), assembly.contigs().size()),
                totalLength + " assembled bases" + (reference == null ? "" : "; reference consensus written"),
                List.of(totalLength),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Reads", Integer.toString(reads.size())),
                        List.of("Contigs", Integer.toString(assembly.contigs().size())),
                        List.of("Assembled bases", Long.toString(totalLength)),
                        List.of("Reference", reference == null ? "not used" : reference.id())
                )),
                artifacts,
                metrics
        );
    }

    private static Map<String, Path> outputNames(Path directory) {
        Map<String, Path> names = new LinkedHashMap<>();
        names.put("assembly", directory.resolve("assembly.fa"));
        names.put("insert", directory.resolve("insert.fa"));
        names.put("reads", directory.resolve("reads.tsv"));
        names.put("trimming", directory.resolve("trimming.tsv"));
        names.put("corrected", directory.resolve("corrected.fa"));
        names.put("variants", directory.resolve("variants.tsv"));
        names.put("provenance", directory.resolve("provenance.bed"));
        names.put("indels", directory.resolve("indels.tsv"));
        names.put("expanded", directory.resolve("consensus_with_indels.fa"));
        return names;
    }

    private static List<Path> readFiles(Path directory, Path reference) throws Exception {
        Set<Path> files = new LinkedHashSet<>();
        try (var stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> reference == null
                            || !path.toAbsolutePath().normalize().equals(reference.toAbsolutePath().normalize()))
                    .forEach(files::add);
        }
        List<Path> ab1 = filter(files, ".ab1");
        if (!ab1.isEmpty()) return ab1;
        List<Path> fastq = filter(files, ".fastq", ".fq");
        if (!fastq.isEmpty()) return fastq;
        return filter(files, ".fa", ".fasta", ".seq");
    }

    private static List<Path> filter(Set<Path> files, String... extensions) {
        List<Path> result = new ArrayList<>();
        for (Path path : files) {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            for (String extension : extensions) {
                if (name.endsWith(extension)) {
                    result.add(path);
                    break;
                }
            }
        }
        result.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)));
        return result;
    }

    private static List<ReadRecord> loadReads(
            Path file,
            int trimQuality,
            int trimWindow,
            int minReadLength,
            AdapterTrimmer adapterTrimmer,
            int minimumAdapterMatch
    ) throws Exception {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        List<ReadRecord> result = new ArrayList<>();
        if (name.endsWith(".ab1")) {
            Ab1Reader.Trace trace = Ab1Reader.read(file);
            ReadRecord read = trim(trace.name(), trace.sequence(), trace.qualities(),
                    trimQuality, trimWindow, minReadLength);
            read = applyAdapter(read, adapterTrimmer, minimumAdapterMatch, minReadLength);
            if (read != null) result.add(read);
        } else if (name.endsWith(".fastq") || name.endsWith(".fq")) {
            try (FastqReader reader = new FastqReader(file)) {
                FastqRecord record;
                while ((record = reader.next()) != null) {
                    ReadRecord read = trim(record.id(), record.sequence(), phred(record.quality()),
                            trimQuality, trimWindow, minReadLength);
                    read = applyAdapter(read, adapterTrimmer, minimumAdapterMatch, minReadLength);
                    if (read != null) result.add(read);
                }
            }
        } else if (name.endsWith(".seq")) {
            String sequence = Files.readString(file, StandardCharsets.UTF_8)
                    .replaceAll("\\s+", "");
            ReadRecord read = trim(name.substring(0, name.length() - 4), sequence, null,
                    trimQuality, trimWindow, minReadLength);
            read = applyAdapter(read, adapterTrimmer, minimumAdapterMatch, minReadLength);
            if (read != null) result.add(read);
        } else {
            try (FastaReader reader = new FastaReader(file)) {
                FastaRecord record;
                while ((record = reader.next()) != null) {
                    ReadRecord read = trim(record.id(), record.sequence(), null,
                            trimQuality, trimWindow, minReadLength);
                    read = applyAdapter(read, adapterTrimmer, minimumAdapterMatch, minReadLength);
                    if (read != null) result.add(read);
                }
            }
        }
        return result;
    }

    private static ReadRecord trim(
            String name,
            String sequence,
            int[] qualities,
            int trimQuality,
            int window,
            int minReadLength
    ) {
        if (sequence == null) return null;
        String upper = sequence.toUpperCase(Locale.ROOT).replaceAll("[^ACGTN]", "N");
        int[] scores = qualities == null ? null : qualities;
        if (scores != null && scores.length != upper.length()) {
            scores = null;
        }
        int start = 0;
        int end = upper.length();
        if (scores != null) {
            while (start + window <= end && mean(scores, start, start + window) < trimQuality) start++;
            while (end - window >= start && mean(scores, end - window, end) < trimQuality) end--;
        }
        if (end - start < minReadLength) return null;
        String trimmed = upper.substring(start, end);
        int[] trimmedQualities = scores == null ? null : java.util.Arrays.copyOfRange(scores, start, end);
        return new ReadRecord(name, trimmed, trimmedQualities, start, end, upper.length(),
                null, null, 0, 0);
    }

    private static ReadRecord applyAdapter(
            ReadRecord read,
            AdapterTrimmer trimmer,
            int minimumMatch,
            int minimumReadLength
    ) {
        if (read == null) return null;
        AdapterTrimmer.TrimResult result = trimmer.trim(
                read.sequence(), read.qualities(), minimumMatch);
        if (result.sequence().length() < minimumReadLength) return null;
        return new ReadRecord(
                read.name(), result.sequence(), result.qualities(),
                read.trimStart() + result.leftBases(),
                read.trimEnd() - result.rightBases(),
                read.originalLength(),
                result.leftAdapter(), result.rightAdapter(),
                result.leftBases(), result.rightBases()
        );
    }

    private static int[] phred(String quality) {
        if (quality == null) return null;
        int[] values = new int[quality.length()];
        for (int index = 0; index < quality.length(); index++) {
            values[index] = Math.max(0, quality.charAt(index) - 33);
        }
        return values;
    }

    private static double decimalValue(ToolRequest request, String id) {
        Object value = request.value(id);
        if (value == null) return 0;
        return value instanceof Number number
                ? number.doubleValue() : Double.parseDouble(String.valueOf(value).trim());
    }

    private static double[] toDouble(int[] values) {
        if (values == null) return null;
        double[] result = new double[values.length];
        for (int index = 0; index < values.length; index++) result[index] = values[index];
        return result;
    }

    private static double mean(int[] values, int start, int end) {
        double sum = 0;
        for (int index = start; index < end; index++) sum += values[index];
        return (end - start) == 0 ? 0 : sum / (end - start);
    }

    private static FastaRecord readReference(Path path) throws Exception {
        try (FastaReader reader = new FastaReader(path)) {
            FastaRecord record = reader.next();
            if (record == null || record.sequence().isBlank()) {
                throw new OpenToolException("SANGER_REFERENCE_EMPTY", "Reference FASTA is empty.",
                        "Provide a non-empty reference FASTA.");
            }
            return new FastaRecord(record.id(), record.header(),
                    record.sequence().toUpperCase(Locale.ROOT));
        }
    }

    private static Assembly assemble(
            List<ReadRecord> reads,
            int minOverlap,
            double minIdentity,
            double threshold
    ) {
        List<ReadRecord> ordered = new ArrayList<>(reads);
        ordered.sort(Comparator.comparingInt((ReadRecord read) -> read.sequence().length()).reversed());
        List<Contig> contigs = new ArrayList<>();
        for (ReadRecord read : ordered) {
            Contig bestContig = null;
            Overlap best = null;
            for (Contig contig : contigs) {
                Overlap overlap = bestOverlap(contig.sequence(), read.sequence(), minOverlap, minIdentity);
                if (overlap != null && (best == null || overlap.betterThan(best))) {
                    best = overlap;
                    bestContig = contig;
                }
            }
            if (bestContig == null) {
                contigs.add(Contig.from(read, threshold));
            } else {
                merge(bestContig, read, best, threshold);
            }
        }
        return new Assembly(contigs);
    }

    private static Overlap bestOverlap(
            String a,
            String b,
            int minOverlap,
            double minIdentity
    ) {
        Overlap best = null;
        for (int orientation = 0; orientation < 2; orientation++) {
            String query = orientation == 0 ? b : reverseComplement(b);
            int startOffset = -query.length() + minOverlap;
            int endOffset = a.length() - minOverlap;
            for (int offset = startOffset; offset <= endOffset; offset++) {
                int start = Math.max(0, offset);
                int end = Math.min(a.length(), offset + query.length());
                int length = end - start;
                if (length < minOverlap) continue;
                int matches = 0;
                for (int index = start; index < end; index++) {
                    if (a.charAt(index) == query.charAt(index - offset)) matches++;
                }
                double identity = matches / (double) length;
                if (identity < minIdentity) continue;
                Overlap candidate = new Overlap(offset, length, identity, orientation == 1);
                if (best == null || candidate.betterThan(best)) best = candidate;
            }
        }
        return best;
    }

    private static void merge(Contig contig, ReadRecord read, Overlap overlap, double threshold) {
        String query = overlap.reverse() ? reverseComplement(read.sequence()) : read.sequence();
        int[] queryQualities = overlap.reverse()
                ? reversed(read.qualities()) : read.qualities();
        double[] queryQuality = toDouble(queryQualities);
        int origin = Math.min(0, overlap.offset());
        int length = Math.max(contig.sequence().length(), overlap.offset() + query.length()) - origin;
        double[][] counts = new double[length][5];
        int[] coverage = new int[length];
        double[] quality = new double[length];
        addCounts(counts, coverage, quality, contig.sequence(), contig.qualities(), -origin);
        addCounts(counts, coverage, quality, query, queryQuality, overlap.offset() - origin);
        StringBuilder sequence = new StringBuilder(length);
        boolean[] low = new boolean[length];
        for (int index = 0; index < length; index++) {
            int bestBase = bestBase(counts[index]);
            double total = 0;
            for (double value : counts[index]) total += value;
            sequence.append("ACGTN".charAt(bestBase));
            low[index] = coverage[index] > 1 && total > 0
                    && counts[index][bestBase] / total < threshold;
        }
        contig.replace(sequence.toString(), coverage, quality, low, -origin);
    }

    private static void addCounts(
            double[][] counts,
            int[] coverage,
            double[] quality,
            String sequence,
            double[] qualities,
            int offset
    ) {
        for (int index = 0; index < sequence.length(); index++) {
            int target = index + offset;
            if (target < 0 || target >= counts.length) continue;
            int base = "ACGTN".indexOf(sequence.charAt(index));
            if (base < 0) base = 4;
            double q = qualities == null ? 20 : qualities[index];
            double weight = Math.max(1, q);
            counts[target][base] += weight;
            coverage[target]++;
            quality[target] += q;
        }
    }

    private static int bestBase(double[] counts) {
        int best = 0;
        for (int index = 1; index < counts.length; index++) {
            if (counts[index] > counts[best]) best = index;
        }
        return best;
    }

    private static int[] reversed(int[] values) {
        if (values == null) return null;
        int[] result = new int[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = values[values.length - 1 - index];
        }
        return result;
    }

    static String reverseComplement(String sequence) {
        StringBuilder result = new StringBuilder(sequence.length());
        for (int index = sequence.length() - 1; index >= 0; index--) {
            result.append(switch (sequence.charAt(index)) {
                case 'A' -> 'T';
                case 'C' -> 'G';
                case 'G' -> 'C';
                case 'T' -> 'A';
                default -> 'N';
            });
        }
        return result.toString();
    }

    private static List<Contig> trimVectorEnds(
            List<Contig> contigs,
            FastaRecord reference,
            boolean enabled,
            int minimumOverhang
    ) {
        if (!enabled || reference == null) return contigs;
        List<Contig> result = new ArrayList<>();
        for (Contig contig : contigs) {
            SangerAligner.Alignment alignment = SangerAligner.best(
                    reference.sequence(), contig.sequence());
            if (alignment == null || alignment.readEnd() - alignment.readStart() < minimumOverhang) {
                result.add(contig);
                continue;
            }
            String oriented = alignment.orientedRead();
            String insert = oriented.substring(alignment.readStart(), alignment.readEnd());
            if (alignment.reverse()) insert = reverseComplement(insert);
            result.add(contig.withSequence(insert));
        }
        return result;
    }

    private static void writeFasta(Path path, List<Contig> contigs) throws Exception {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < contigs.size(); index++) {
            Contig contig = contigs.get(index);
            text.append(">contig_").append(index + 1)
                    .append(" length=").append(contig.sequence().length())
                    .append(" reads=").append(contig.reads()).append('\n');
            for (int offset = 0; offset < contig.sequence().length(); offset += 80) {
                text.append(contig.sequence(), offset,
                        Math.min(contig.sequence().length(), offset + 80)).append('\n');
            }
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static void writeTrimming(Path path, List<ReadRecord> reads) throws Exception {
        StringBuilder text = new StringBuilder();
        text.append("read\tquality_trim_start\tquality_trim_end\tadapter_left"
                + "\tleft_bases\tadapter_right\tright_bases\tfinal_length\n");
        for (ReadRecord read : reads) {
            text.append(read.name()).append('\t')
                    .append(read.trimStart()).append('\t')
                    .append(read.trimEnd()).append('\t')
                    .append(read.leftAdapter() == null ? "" : read.leftAdapter()).append('\t')
                    .append(read.leftAdapterBases()).append('\t')
                    .append(read.rightAdapter() == null ? "" : read.rightAdapter()).append('\t')
                    .append(read.rightAdapterBases()).append('\t')
                    .append(read.sequence().length()).append('\n');
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static void writeReadTable(Path path, List<ReadRecord> reads, ReferenceAnchor anchor) throws Exception {
        StringBuilder text = new StringBuilder();
        text.append("read\toriginal_length\ttrim_start\ttrim_end\ttrimmed_length"
                + "\tmean_quality\treference_start\torientation\tidentity\n");
        for (ReadRecord read : reads) {
            Placement placement = anchor == null ? null : anchor.byName().get(read.name());
            text.append(read.name()).append('\t')
                    .append(read.originalLength()).append('\t')
                    .append(read.trimStart()).append('\t')
                    .append(read.trimEnd()).append('\t')
                    .append(read.sequence().length()).append('\t')
                    .append(format(meanQuality(read.qualities()))).append('\t')
                    .append(placement == null ? "" : Integer.toString(placement.start())).append('\t')
                    .append(placement == null ? "" : (placement.reverse() ? "reverse" : "forward")).append('\t')
                    .append(placement == null ? "" : format(placement.identity())).append('\n');
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static void writeConsensus(Path path, FastaRecord reference, Consensus consensus) throws Exception {
        StringBuilder text = new StringBuilder();
        text.append('>').append(reference.id()).append("_consensus\n");
        for (int offset = 0; offset < consensus.sequence().length(); offset += 80) {
            text.append(consensus.sequence(), offset,
                    Math.min(consensus.sequence().length(), offset + 80)).append('\n');
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static void writeVariants(Path path, FastaRecord reference, Consensus consensus) throws Exception {
        StringBuilder text = new StringBuilder();
        text.append("position\treference\tconsensus\tcoverage\tmean_quality\tstate\n");
        for (int index = 0; index < consensus.sequence().length(); index++) {
            if (!consensus.covered()[index]) continue;
            char base = consensus.sequence().charAt(index);
            char referenceBase = reference.sequence().toUpperCase(Locale.ROOT).charAt(index);
            String state = consensus.state(index);
            if (base == referenceBase && "READ_CONSENSUS".equals(state)) continue;
            text.append(index + 1).append('\t').append(referenceBase).append('\t').append(base)
                    .append('\t').append(consensus.coverage()[index]).append('\t')
                    .append(format(consensus.quality()[index])).append('\t')
                    .append(state)
                    .append('\n');
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static void writeProvenance(Path path, FastaRecord reference, Consensus consensus) throws Exception {
        StringBuilder text = new StringBuilder();
        String name = reference.id();
        int start = 0;
        String state = consensus.state(0);
        for (int index = 1; index <= consensus.sequence().length(); index++) {
            String next = index == consensus.sequence().length() ? null : consensus.state(index);
            if (next == null || !next.equals(state)) {
                text.append(name).append('\t').append(start).append('\t').append(index)
                        .append('\t').append(state).append('\n');
                start = index;
                state = next;
            }
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static void writeIndels(Path path, Consensus consensus) throws Exception {
        StringBuilder text = new StringBuilder();
        text.append("position\ttype\tsequence\tlength\tsupport\treads\n");
        for (IndelCall indel : consensus.indels()) {
            text.append(indel.position() + 1).append('\t')
                    .append(indel.type()).append('\t')
                    .append(indel.sequence()).append('\t')
                    .append(indel.length()).append('\t')
                    .append(indel.support()).append('\t')
                    .append(String.join(",", indel.reads())).append('\n');
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static void writeExpandedConsensus(
            Path path,
            FastaRecord reference,
            Consensus consensus,
            int minimumSupport
    ) throws Exception {
        Map<Integer, String> insertions = new LinkedHashMap<>();
        boolean[] deleted = new boolean[consensus.sequence().length()];
        for (IndelCall indel : consensus.indels()) {
            if (indel.support() < minimumSupport) continue;
            if ("INSERTION".equals(indel.type())) {
                insertions.merge(indel.position(), indel.sequence(), String::concat);
            } else if ("DELETION".equals(indel.type())) {
                int end = Math.min(deleted.length, indel.position() + indel.length());
                for (int index = Math.max(0, indel.position()); index < end; index++) {
                    deleted[index] = true;
                }
            }
        }
        StringBuilder sequence = new StringBuilder();
        for (int index = 0; index <= consensus.sequence().length(); index++) {
            String insertion = insertions.get(index);
            if (insertion != null) sequence.append(insertion);
            if (index == consensus.sequence().length()) break;
            if (!deleted[index]) sequence.append(consensus.sequence().charAt(index));
        }
        StringBuilder text = new StringBuilder();
        text.append('>').append(reference.id()).append("_consensus_with_indels\n");
        for (int offset = 0; offset < sequence.length(); offset += 80) {
            text.append(sequence, offset, Math.min(sequence.length(), offset + 80)).append('\n');
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static String format(double value) {
        return Double.isNaN(value) ? "" : "%.2f".formatted(value);
    }

    private static double meanQuality(int[] quality) {
        if (quality == null || quality.length == 0) return Double.NaN;
        double sum = 0;
        for (int value : quality) sum += value;
        return sum / quality.length;
    }

    private record Assembly(List<Contig> contigs) {
    }

    private record ReadRecord(
            String name,
            String sequence,
            int[] qualities,
            int trimStart,
            int trimEnd,
            int originalLength,
            String leftAdapter,
            String rightAdapter,
            int leftAdapterBases,
            int rightAdapterBases
    ) {
    }

    private record Overlap(int offset, int length, double identity, boolean reverse) {
        boolean betterThan(Overlap other) {
            if (Double.compare(identity, other.identity) != 0) return identity > other.identity;
            return length > other.length;
        }
    }

    private static final class Contig {
        private String sequence;
        private int[] coverage;
        private double[] qualities;
        private boolean[] low;
        private int reads;
        private final Map<String, Integer> origins = new LinkedHashMap<>();

        static Contig from(ReadRecord read, double threshold) {
            Contig contig = new Contig();
            contig.sequence = read.sequence();
            contig.coverage = new int[read.sequence().length()];
            java.util.Arrays.fill(contig.coverage, 1);
            contig.qualities = read.qualities() == null
                    ? new double[read.sequence().length()] : toDouble(read.qualities());
            contig.low = new boolean[read.sequence().length()];
            contig.reads = 1;
            contig.origins.put(read.name(), 0);
            return contig;
        }

        private static double[] toDouble(double[] values) {
        return values == null ? null : values.clone();
    }

    private static double[] toDouble(int[] values) {
            double[] result = new double[values.length];
            for (int index = 0; index < values.length; index++) result[index] = values[index];
            return result;
        }

        String sequence() { return sequence; }
        int[] coverage() { return coverage; }
        double[] qualities() { return qualities; }
        int reads() { return reads; }

        Contig withSequence(String value) {
            Contig trimmed = new Contig();
            trimmed.sequence = value;
            trimmed.coverage = new int[value.length()];
            trimmed.qualities = new double[value.length()];
            trimmed.low = new boolean[value.length()];
            trimmed.reads = reads;
            return trimmed;
        }

        void replace(String sequence, int[] coverage, double[] qualities, boolean[] low, int origin) {
            this.sequence = sequence;
            this.coverage = coverage;
            this.qualities = qualities;
            this.low = low;
            this.reads++;
            if (origin != 0) {
                origins.replaceAll((name, value) -> value + origin);
            }
        }
    }

    private record Placement(String name, SangerAligner.Alignment alignment) {
        int start() { return alignment.referenceStart(); }
        boolean reverse() { return alignment.reverse(); }
        double identity() { return alignment.identity(); }
    }

    private static final class ReferenceAnchor {
        private final String reference;
        private final List<Placement> placements;
        private final Map<String, Placement> byName;
        private final Map<String, ReadRecord> reads;

        private ReferenceAnchor(
                String reference,
                List<Placement> placements,
                Map<String, ReadRecord> reads
        ) {
            this.reference = reference;
            this.placements = placements;
            this.byName = new LinkedHashMap<>();
            for (Placement placement : placements) byName.put(placement.name(), placement);
            this.reads = reads;
        }

        static ReferenceAnchor place(String reference, List<ReadRecord> reads, int minOverlap) {
            List<Placement> placements = new ArrayList<>();
            Map<String, ReadRecord> byName = new LinkedHashMap<>();
            for (ReadRecord read : reads) {
                SangerAligner.Alignment alignment = SangerAligner.best(reference, read.sequence());
                if (alignment == null) continue;
                int aligned = 0;
                for (char operation : alignment.operations()) {
                    if (operation == 'M') aligned++;
                }
                if (aligned < minOverlap || alignment.identity() < 0.70) continue;
                placements.add(new Placement(read.name(), alignment));
                byName.put(read.name(), read);
            }
            return new ReferenceAnchor(reference, placements, byName);
        }

        List<Placement> placements() { return placements; }
        Map<String, Placement> byName() { return byName; }

        int coveredBases() {
            return consensus(0.6, 0.25, 4).coveredCount();
        }

        Consensus consensus(double threshold, double heteroThreshold, int minimumHeteroCoverage) {
            int length = reference.length();
            double[][] counts = new double[length][5];
            int[] coverage = new int[length];
            int[] deletions = new int[length];
            double[] quality = new double[length];
            Map<String, IndelCall> indels = new LinkedHashMap<>();
            for (Placement placement : placements) {
                ReadRecord read = reads.get(placement.name());
                if (read == null) continue;
                SangerAligner.Alignment alignment = placement.alignment();
                String sequence = alignment.orientedRead();
                int[] baseQuality = alignment.reverse()
                        ? reversed(read.qualities()) : read.qualities();
                int readIndex = alignment.readStart();
                int referenceIndex = alignment.referenceStart();
                List<Character> operations = alignment.operations();
                for (int operationIndex = 0; operationIndex < operations.size(); ) {
                    char operation = operations.get(operationIndex);
                    if (operation == 'M') {
                        int base = "ACGTN".indexOf(sequence.charAt(readIndex));
                        if (base < 0) base = 4;
                        int q = baseQuality == null ? 20 : baseQuality[readIndex];
                        counts[referenceIndex][base] += Math.max(1, q);
                        coverage[referenceIndex]++;
                        quality[referenceIndex] += q;
                        readIndex++;
                        referenceIndex++;
                        operationIndex++;
                    } else if (operation == 'I') {
                        int start = readIndex;
                        while (operationIndex < operations.size()
                                && operations.get(operationIndex) == 'I') {
                            readIndex++;
                            operationIndex++;
                        }
                        String inserted = sequence.substring(start, readIndex);
                        addIndel(indels, new IndelCall("INSERTION", referenceIndex,
                                inserted, inserted.length(), 0, new LinkedHashSet<>()), read.name());
                    } else {
                        int start = referenceIndex;
                        while (operationIndex < operations.size()
                                && operations.get(operationIndex) == 'D') {
                            referenceIndex++;
                            operationIndex++;
                        }
                        for (int deleted = start; deleted < referenceIndex; deleted++) {
                            deletions[deleted]++;
                        }
                        int deletedLength = referenceIndex - start;
                        addIndel(indels, new IndelCall("DELETION", start,
                                "-", deletedLength, 0, new LinkedHashSet<>()), read.name());
                    }
                }
            }
            StringBuilder sequence = new StringBuilder(length);
            boolean[] covered = new boolean[length];
            boolean[] low = new boolean[length];
            boolean[] heterozygous = new boolean[length];
            for (int index = 0; index < length; index++) {
                int best = 0;
                int second = -1;
                double total = 0;
                for (int base = 0; base < 5; base++) {
                    total += counts[index][base];
                    if (counts[index][base] > counts[index][best]) {
                        second = best;
                        best = base;
                    } else if (base != best && (second < 0
                            || counts[index][base] > counts[index][second])) {
                        second = base;
                    }
                }
                covered[index] = coverage[index] > 0 || deletions[index] > 0;
                if (coverage[index] == 0) {
                    sequence.append(reference.charAt(index));
                    continue;
                }
                double bestFraction = counts[index][best] / Math.max(1, total);
                double secondFraction = second < 0 ? 0
                        : counts[index][second] / Math.max(1, total);
                boolean hetero = coverage[index] >= minimumHeteroCoverage
                        && second >= 0 && secondFraction >= heteroThreshold;
                heterozygous[index] = hetero;
                char base = "ACGTN".charAt(best);
                if (hetero) base = iupac(best, second);
                sequence.append(base);
                low[index] = !hetero && (bestFraction < threshold
                        || quality[index] / Math.max(1, coverage[index]) < 20);
                if (coverage[index] > 1) {
                    quality[index] /= coverage[index];
                }
            }
            return new Consensus(sequence.toString(), covered, coverage, quality, low,
                    heterozygous, deletions, new ArrayList<>(indels.values()));
        }

        private static void addIndel(
                Map<String, IndelCall> indels,
                IndelCall event,
                String read
        ) {
            String key = event.type() + ":" + event.position() + ":" + event.length()
                    + ":" + event.sequence();
            IndelCall existing = indels.get(key);
            if (existing == null) {
                event.reads().add(read);
                event.support = 1;
                indels.put(key, event);
            } else {
                existing.support++;
                existing.reads().add(read);
            }
        }
    }

    private static char iupac(int first, int second) {
        int a = Math.min(first, second);
        int b = Math.max(first, second);
        if (a == 0 && b == 2) return 'R';
        if (a == 1 && b == 3) return 'Y';
        if (a == 1 && b == 2) return 'S';
        if (a == 0 && b == 3) return 'W';
        if (a == 2 && b == 3) return 'K';
        if (a == 0 && b == 1) return 'M';
        return 'N';
    }

    private static final class IndelCall {
        private final String type;
        private final int position;
        private final String sequence;
        private final int length;
        private int support;
        private final Set<String> reads;

        private IndelCall(
                String type,
                int position,
                String sequence,
                int length,
                int support,
                Set<String> reads
        ) {
            this.type = type;
            this.position = position;
            this.sequence = sequence;
            this.length = length;
            this.support = support;
            this.reads = reads;
        }

        String type() { return type; }
        int position() { return position; }
        String sequence() { return sequence; }
        int length() { return length; }
        int support() { return support; }
        Set<String> reads() { return reads; }
    }

    private record Consensus(
            String sequence,
            boolean[] covered,
            int[] coverage,
            double[] quality,
            boolean[] low,
            boolean[] heterozygous,
            int[] deletions,
            List<IndelCall> indels
    ) {
        int coveredCount() {
            int count = 0;
            for (boolean value : covered) if (value) count++;
            return count;
        }

        int heterozygousCount() {
            int count = 0;
            for (boolean value : heterozygous) if (value) count++;
            return count;
        }

        String state(int index) {
            if (!covered[index]) return "REFERENCE_FILL";
            if (deletions[index] > 0 && deletions[index] >= Math.max(1, coverage[index])) {
                return "DELETION";
            }
            if (low[index]) return "LOW_CONFIDENCE";
            if (heterozygous[index]) return "HETEROZYGOUS";
            return "READ_CONSENSUS";
        }
    }
}
