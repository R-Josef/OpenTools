package io.opentools.analysis.sequence;

import io.opentools.analysis.BuiltInAnalysis;
import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OpenTool;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolParameter;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.api.tool.ToolRequest;
import io.opentools.formats.Compression;
import io.opentools.formats.fasta.FastaReader;
import io.opentools.formats.fasta.FastaRecord;
import io.opentools.formats.fastq.FastqReader;
import io.opentools.formats.fastq.FastqRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class F1RemainingToolsTest {
    @TempDir
    Path tempDir;

    @Test
    void renamesWithMappingAndReportsAdjustedDuplicateIds() throws Exception {
        Path input = tempDir.resolve("rename.fa");
        Files.writeString(input, ">a description\nACGT\n>b\nGG\n");
        Path output = tempDir.resolve("renamed.fa");
        ToolResult result = run("fasta.rename", Map.of(
                "input", input,
                "mode", "MAP",
                "mapping", "a\tb\nb\tb",
                "duplicatePolicy", "APPEND",
                "output", output
        ));
        assertEquals(1L, result.metrics().get("duplicateAdjusted"));
        Map<String, String> records = readFasta(output);
        assertEquals("ACGT", records.get("b"));
        assertEquals("GG", records.get("b_dup2"));
        assertTrue(Files.readString(tempDir.resolve("out/rename-report.tsv")).contains("DUPLICATE_ADJUSTED"));
    }

    @Test
    void convertsFastaTsvAndIdListRoundTrip() throws Exception {
        Path fasta = tempDir.resolve("convert.fa");
        Files.writeString(fasta, ">a desc\nACGT\n>b\nGG\n");
        Path tsv = tempDir.resolve("convert.tsv");
        run("fasta.convert", Map.of("input", fasta, "mode", "FASTA_TO_TSV", "output", tsv));
        assertTrue(Files.readString(tsv).contains("a\ta desc\tACGT\t4"));

        Path back = tempDir.resolve("roundtrip.fa");
        run("fasta.convert", Map.of("input", tsv, "mode", "TSV_TO_FASTA", "output", back));
        assertEquals(readFasta(fasta), readFasta(back));

        Path ids = tempDir.resolve("ids.txt");
        run("fasta.convert", Map.of("input", fasta, "mode", "FASTA_TO_IDS", "output", ids));
        assertEquals(java.util.List.of("a", "b"), Files.readAllLines(ids));
    }

    @Test
    void importsQuotedMultilineSequenceFromTsv() throws Exception {
        Path tsv = tempDir.resolve("multiline.tsv");
        Files.writeString(tsv, """
                id	header	sequence	length
                q1	"quoted header	with tab"	"AC
                GT"	4
                """);
        Path fasta = tempDir.resolve("multiline.fa");
        run("fasta.convert", Map.of(
                "input", tsv,
                "mode", "TSV_TO_FASTA",
                "output", fasta
        ));
        assertEquals("ACGT", readFasta(fasta).get("q1"));
        assertTrue(Files.readString(fasta).contains("quoted header\twith tab"));
    }

    @Test
    void predictsForwardAndReverseOrfsWithCoordinates() throws Exception {
        Path input = tempDir.resolve("orfs.fa");
        Files.writeString(input, ">seq\nCCCATGGCTTAATTT\n");
        Path output = tempDir.resolve("orfs-out.fa");
        ToolResult result = run("sequence.orf", Map.of(
                "input", input,
                "minAminoAcids", 2,
                "startCodons", "ATG",
                "bothStrands", true,
                "output", output
        ));
        assertTrue((Long) result.metrics().get("orfs") >= 1);
        Map<String, String> records = readFasta(output);
        assertTrue(records.containsKey("seq|frame=+1|start=4|end=9"), records.toString());
        assertEquals("ATGGCT", records.get("seq|frame=+1|start=4|end=9"));
    }

    @Test
    void usesConfiguredGeneticCodeForStopCodons() throws Exception {
        Path input = tempDir.resolve("mitochondrial-orf.fa");
        Files.writeString(input, ">mito\nATGAGATGA\n");
        Path output = tempDir.resolve("mitochondrial-orf-out.fa");
        ToolResult result = run("sequence.orf", Map.of(
                "input", input,
                "minAminoAcids", 1,
                "geneticCode", "2",
                "startCodons", "ATG",
                "bothStrands", false,
                "output", output
        ));
        Map<String, String> records = readFasta(output);
        assertTrue(records.containsKey("mito|frame=+1|start=1|end=3"), records.toString());
        assertEquals("2", result.metrics().get("geneticCode"));
    }

    @Test
    void statsAndFiltersGzipFastqWithReasons() throws Exception {
        Path input = tempDir.resolve("reads.fastq.gz");
        try (var writer = Compression.createWriter(input)) {
            writer.write("@r1\nACGTA\n+\nIIIII\n");
            writer.write("@r2\nNNNNN\n+\n!!!!!\n");
            writer.write("@r3\nACGTN\n+\nIII!I\n");
        }
        Path stats = tempDir.resolve("fastq-stats.tsv");
        ToolResult statsResult = run("fastq.stats", Map.of("input", input, "output", stats));
        assertEquals(3L, statsResult.metrics().get("records"));
        assertTrue(Files.readString(stats).contains("q30_percent"));

        Path output = tempDir.resolve("filtered.fastq.gz");
        ToolResult filter = run("fastq.filter", Map.of(
                "input", input,
                "trimRight", 1,
                "minLength", 4,
                "minMeanQuality", 20.0,
                "maxNPercent", 20.0,
                "output", output
        ));
        assertEquals(2L, filter.metrics().get("retained"));
        java.util.Set<String> kept = new java.util.LinkedHashSet<>();
        try (FastqReader reader = new FastqReader(output)) {
            FastqRecord record;
            while ((record = reader.next()) != null) {
                kept.add(record.id());
                if (record.id().equals("r1")) {
                    assertEquals("ACGT", record.sequence());
                }
            }
        }
        assertTrue(kept.containsAll(java.util.Set.of("r1", "r3")));
        String report = Files.readString(tempDir.resolve("out/fastq-filter-report.tsv"));
        assertTrue(report.contains("MEAN_QUALITY_BELOW_MINIMUM"));
        assertTrue(report.contains("N_ABOVE_MAXIMUM") || report.contains("MEAN_QUALITY_BELOW_MINIMUM"));
    }

    @Test
    void interpretsPhred64QualityEncoding() throws Exception {
        Path input = tempDir.resolve("phred64.fastq");
        Files.writeString(input, "@r64\nACGTA\n+\nhhhhh\n");
        Path stats = tempDir.resolve("phred64-stats.tsv");
        ToolResult statsResult = run("fastq.stats", Map.of(
                "input", input,
                "qualityEncoding", "PHRED_64",
                "output", stats
        ));
        assertEquals(40.0, (double) statsResult.metrics().get("meanQuality"), 0.0001);
        assertEquals("PHRED_64", statsResult.metrics().get("qualityEncoding"));

        Path filtered = tempDir.resolve("phred64-filtered.fastq");
        ToolResult filterResult = run("fastq.filter", Map.of(
                "input", input,
                "qualityEncoding", "AUTO",
                "minMeanQuality", 30.0,
                "output", filtered
        ));
        assertEquals(1L, filterResult.metrics().get("retained"));
        assertEquals("PHRED_64", filterResult.metrics().get("qualityEncoding"));
    }

    @Test
    void readsSubsequenceRequestsFromGzipRegionFile() throws Exception {
        Path input = tempDir.resolve("subsequence.fa");
        Files.writeString(input, ">chr1\nAACCGGTT\n");
        Path regions = tempDir.resolve("regions.tsv.gz");
        try (var writer = Compression.createWriter(regions)) {
            writer.write("chr1\t2\t5\t-\tnegative\n");
        }
        Path output = tempDir.resolve("subsequence-out.fa");
        ToolResult result = run("fasta.subsequence", Map.of(
                "input", input,
                "regionsFile", regions,
                "output", output
        ));
        assertEquals(1L, result.metrics().get("extracted"));
        assertEquals("CGGT", readFasta(output).get("negative"));
    }

    @Test
    void parallelSequenceToolsMatchSequentialOutput() throws Exception {
        Path fasta = tempDir.resolve("parallel-sequence.fa");
        StringBuilder fastaText = new StringBuilder();
        for (int index = 0; index < 60; index++) {
            fastaText.append(">seq_").append(index).append('\n')
                    .append(index % 2 == 0 ? "ATGGCCTAA" : "ATGAAATGA")
                    .append('\n');
        }
        Files.writeString(fasta, fastaText);

        Path renameSequential = tempDir.resolve("rename-sequential.fa");
        Path renameParallel = tempDir.resolve("rename-parallel.fa");
        run("fasta.rename", Map.of(
                "input", fasta, "idPrefix", "p_", "output", renameSequential,
                "threads", 1));
        run("fasta.rename", Map.of(
                "input", fasta, "idPrefix", "p_", "output", renameParallel,
                "threads", 3));
        assertEquals(Files.readString(renameSequential), Files.readString(renameParallel));

        Path orfSequential = tempDir.resolve("orf-sequential.fa");
        Path orfParallel = tempDir.resolve("orf-parallel.fa");
        run("sequence.orf", Map.of(
                "input", fasta, "minAminoAcids", 1, "bothStrands", false,
                "output", orfSequential, "threads", 1));
        run("sequence.orf", Map.of(
                "input", fasta, "minAminoAcids", 1, "bothStrands", false,
                "output", orfParallel, "threads", 3));
        assertEquals(Files.readString(orfSequential), Files.readString(orfParallel));

        Path fastq = tempDir.resolve("parallel-reads.fastq");
        StringBuilder fastqText = new StringBuilder();
        for (int index = 0; index < 80; index++) {
            String sequence = index % 3 == 0 ? "NNNNN" : "ACGTN";
            String quality = index % 2 == 0 ? "IIIII" : "!!!!!";
            fastqText.append("@read_").append(index).append('\n')
                    .append(sequence).append("\n+\n").append(quality).append('\n');
        }
        Files.writeString(fastq, fastqText);

        Path statsSequential = tempDir.resolve("fastq-stats-sequential.tsv");
        Path statsParallel = tempDir.resolve("fastq-stats-parallel.tsv");
        run("fastq.stats", Map.of(
                "input", fastq, "output", statsSequential, "threads", 1));
        run("fastq.stats", Map.of(
                "input", fastq, "output", statsParallel, "threads", 3));
        assertEquals(Files.readString(statsSequential), Files.readString(statsParallel));

        Path filterSequential = tempDir.resolve("fastq-filter-sequential.fastq");
        Path filterParallel = tempDir.resolve("fastq-filter-parallel.fastq");
        run("fastq.filter", Map.of(
                "input", fastq, "maxNPercent", 30.0, "output", filterSequential,
                "threads", 1));
        run("fastq.filter", Map.of(
                "input", fastq, "maxNPercent", 30.0, "output", filterParallel,
                "threads", 3));
        assertEquals(Files.readString(filterSequential), Files.readString(filterParallel));
    }

    private ToolResult run(String id, Map<String, Object> supplied) throws Exception {
        ToolRegistry registry = new ToolRegistry();
        BuiltInAnalysis.register(registry);
        OpenTool tool = registry.find(id).orElseThrow();
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (ToolParameter parameter : tool.descriptor().parameters()) {
            if (parameter.defaultValue() != null) parameters.put(parameter.id(), parameter.defaultValue());
        }
        parameters.putAll(supplied);
        ToolRequest request = new ToolRequest(id, parameters);
        var validation = tool.validate(request);
        assertTrue(validation.valid(), validation.issues().toString());
        Path output = Files.createDirectories(tempDir.resolve("out"));
        return tool.execute(request, new ToolExecutionContext("f1-remaining", tempDir, output, output,
                ignored -> { }, ignored -> { }, new CancellationToken()));
    }

    private Map<String, String> readFasta(Path path) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        try (FastaReader reader = new FastaReader(path)) {
            FastaRecord record;
            while ((record = reader.next()) != null) result.put(record.id(), record.sequence());
        }
        return result;
    }
}
