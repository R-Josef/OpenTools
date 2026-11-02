package io.opentools.analysis.sequence;

import io.opentools.analysis.BuiltInAnalysis;
import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolParameter;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.Compression;
import io.opentools.formats.fasta.FastaReader;
import io.opentools.formats.fasta.FastaRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FastaAnalysisToolsTest {
    @TempDir
    Path tempDir;

    @Test
    void filterStreamsIdsRegexLengthGcAndNWithPerRecordReasons() throws Exception {
        Path input = tempDir.resolve("filter.fa");
        Files.writeString(input, """
                >keep1 pass
                AACCGGTT
                >low_gc fail
                AAAAAAAT
                >keep2 high_n
                ACGTNNNN
                >excluded
                GGGGCCCC
                """);
        Path output = tempDir.resolve("filtered.fa");

        ToolResult result = run("fasta.filter", Map.of(
                "input", input,
                "includeRegex", "keep|low",
                "excludeIds", "excluded",
                "minLength", 6,
                "maxLength", 10,
                "minGcPercent", 25.0,
                "maxNPercent", 10.0,
                "output", output
        ));

        assertEquals(Map.of("keep1", "AACCGGTT"), readFasta(output));
        String report = Files.readString(tempDir.resolve("out/filter-report.tsv"));
        assertTrue(report.contains("keep1\tKEPT\tPASS"));
        assertTrue(report.contains("low_gc\tFILTERED\tGC_BELOW_MINIMUM"));
        assertTrue(report.contains("keep2\tFILTERED\tN_ABOVE_MAXIMUM"));
        assertTrue(report.contains("excluded\tFILTERED\tID_EXCLUDED"));
        assertEquals(1L, result.metrics().get("retained"));
        assertEquals(3L, result.metrics().get("filtered"));
    }

    @Test
    void transformHandlesReverseComplementCaseAndInvalidCleaning() throws Exception {
        Path input = tempDir.resolve("transform.fa");
        Files.writeString(input, """
                >nucleotide
                ACGTacgt
                >bad
                AC#GT?N
                >protein
                MEEP#
                """);

        Path reverseInput = tempDir.resolve("reverse-input.fa");
        Files.writeString(reverseInput, ">nucleotide\nACGTacgt\n");
        Path reverse = tempDir.resolve("reverse.fa");
        run("fasta.transform", Map.of(
                "input", reverseInput,
                "direction", "REVERSE_COMPLEMENT",
                "caseMode", "KEEP",
                "sequenceType", "NUCLEIC_ACID",
                "output", reverse
        ));
        assertEquals("acgtACGT", readFasta(reverse).get("nucleotide"));

        Path cleaned = tempDir.resolve("cleaned.fa");
        ToolResult result = run("fasta.transform", Map.of(
                "input", input,
                "direction", "FORWARD",
                "caseMode", "UPPERCASE",
                "sequenceType", "AUTO",
                "cleanInvalid", true,
                "output", cleaned
        ));
        Map<String, String> sequences = readFasta(cleaned);
        assertEquals("ACGTACGT", sequences.get("nucleotide"));
        assertEquals("ACGTN", sequences.get("bad"));
        assertEquals("MEEP", sequences.get("protein"));
        assertEquals(3L, result.metrics().get("removedCharacters"));
        String report = Files.readString(tempDir.resolve("out/transform-report.tsv"));
        assertTrue(report.contains("U+0023"));
        assertTrue(report.contains("U+003F"));
        assertTrue(report.contains("PROTEIN"));

        Path rejected = tempDir.resolve("rejected.fa");
        var badRecord = Files.writeString(tempDir.resolve("bad.fa"), ">bad\nAC#GT\n");
        var badRequest = request("fasta.transform", Map.of(
                "input", badRecord,
                "direction", "FORWARD",
                "output", rejected
        ));
        assertTrue(tool("fasta.transform").validate(badRequest).valid());
        OpenToolException failure = org.junit.jupiter.api.Assertions.assertThrows(
                OpenToolException.class,
                () -> tool("fasta.transform").execute(badRequest, context())
        );
        assertEquals("INVALID_SEQUENCE_CHARACTER", failure.code());
        assertFalse(Files.exists(rejected));
    }

    @Test
    void subsequenceUsesOneBasedInclusiveCoordinatesAndReverseComplement() throws Exception {
        Path input = tempDir.resolve("subsequence.fa");
        Files.writeString(input, """
                >s1 source
                ACGTACGTAA
                >dup first
                TTGGCC
                >dup second
                AAAAAA
                """);
        Path output = tempDir.resolve("regions.fa");
        String regions = String.join("\n",
                "s1\t1\t1\t+\tfirst",
                "s1\t10\t10\t-\tlast",
                "s1\t2\t5\t-\treverse",
                "dup\t2\t3\t+\tdup_region",
                "missing\t1\t2\t+\tmissing_region",
                "s1\t9\t11\t+\tout_of_range"
        );

        ToolResult result = run("fasta.subsequence", Map.of(
                "input", input,
                "regions", regions,
                "output", output
        ));
        Map<String, String> sequences = readFasta(output);
        assertEquals("A", sequences.get("first"));
        assertEquals("T", sequences.get("last"));
        assertEquals("TACG", sequences.get("reverse"));
        assertEquals("TG", sequences.get("dup_region"));
        assertFalse(sequences.containsKey("missing_region"));
        assertFalse(sequences.containsKey("out_of_range"));

        String diagnostics = Files.readString(tempDir.resolve("out/subsequence-diagnostics.tsv"));
        assertTrue(diagnostics.contains("DUPLICATE_SOURCE_ID"));
        assertTrue(diagnostics.contains("MISSING_ID"));
        assertTrue(diagnostics.contains("OUT_OF_RANGE"));
        assertEquals(4L, result.metrics().get("extracted"));
        assertEquals(1L, result.metrics().get("duplicateSourceIds"));
    }

    @Test
    void invalidCoordinatesAndEmptyExtractionAreReportedDeterministically() throws Exception {
        Path input = tempDir.resolve("boundary.fa");
        Files.writeString(input, ">seq\nACGT\n");
        String invalidRegions = "seq\t0\t1\nseq\t3\t2";
        ToolRequest invalid = request("fasta.subsequence", Map.of(
                "input", input,
                "regions", invalidRegions
        ));
        ValidationResult validation = tool("fasta.subsequence").validate(invalid);
        assertFalse(validation.valid());
        assertTrue(validation.issues().stream().anyMatch(issue -> issue.message().contains("at least 1")));
        assertTrue(validation.issues().stream().anyMatch(issue -> issue.message().contains("must not exceed")));

        Path output = tempDir.resolve("empty.fa");
        ToolResult empty = run("fasta.subsequence", Map.of(
                "input", input,
                "regions", "seq\t5\t6\t+\tmissing_range",
                "output", output
        ));
        assertTrue(Files.size(output) == 0L);
        assertEquals(0L, empty.metrics().get("extracted"));
        String diagnostics = Files.readString(tempDir.resolve("out/subsequence-diagnostics.tsv"));
        assertTrue(diagnostics.contains("OUT_OF_RANGE"));
    }

    @Test
    void gzipRoundTripFiltersOneHundredThousandRecords() throws Exception {
        Path input = tempDir.resolve("large.fa.gz");
        try (BufferedWriter writer = new BufferedWriter(
                new java.io.OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(input)), StandardCharsets.UTF_8)
        )) {
            for (int index = 0; index < 100_000; index++) {
                writer.write(">keep_" + index + " description\nACGTACGT\n");
            }
        }
        Path output = tempDir.resolve("large-filtered.fa.gz");

        ToolResult result = assertTimeoutPreemptively(Duration.ofSeconds(20), () -> run("fasta.filter", Map.of(
                "input", input,
                "includeRegex", "keep_",
                "minLength", 8,
                "maxLength", 8,
                "output", output
        )));

        assertTrue(Compression.isGzip(output));
        assertEquals(100_000L, result.metrics().get("retained"));
        try (var stream = Compression.open(output)) {
            assertTrue(stream.readNBytes(9).length > 0);
        }
        try (FastaReader reader = new FastaReader(output)) {
            long records = 0;
            while (reader.next() != null) {
                records++;
            }
            assertEquals(100_000L, records);
        }
    }

    @Test
    void parallelFilterAndTransformPreserveOrderedOutput() throws Exception {
        Path input = tempDir.resolve("parallel-filter-transform.fa");
        StringBuilder content = new StringBuilder();
        for (int index = 0; index < 90; index++) {
            content.append(">seq_").append(index).append('\n')
                    .append(index % 4 == 0 ? "acgtNN" : "ACGTACGT")
                    .append('\n');
        }
        Files.writeString(input, content);

        Path sequentialFilter = tempDir.resolve("filter-sequential.fa");
        Path parallelFilter = tempDir.resolve("filter-parallel.fa");
        run("fasta.filter", Map.of(
                "input", input, "minLength", 6, "output", sequentialFilter,
                "threads", 1));
        run("fasta.filter", Map.of(
                "input", input, "minLength", 6, "output", parallelFilter,
                "threads", 3));
        assertEquals(Files.readString(sequentialFilter), Files.readString(parallelFilter));

        Path sequentialTransform = tempDir.resolve("transform-sequential.fa");
        Path parallelTransform = tempDir.resolve("transform-parallel.fa");
        run("fasta.transform", Map.of(
                "input", input, "caseMode", "UPPERCASE",
                "direction", "REVERSE_COMPLEMENT", "sequenceType", "NUCLEIC_ACID",
                "output", sequentialTransform, "threads", 1));
        run("fasta.transform", Map.of(
                "input", input, "caseMode", "UPPERCASE",
                "direction", "REVERSE_COMPLEMENT", "sequenceType", "NUCLEIC_ACID",
                "output", parallelTransform, "threads", 3));
        assertEquals(Files.readString(sequentialTransform), Files.readString(parallelTransform));
    }

    private ToolResult run(String toolId, Map<String, Object> supplied) throws Exception {
        ToolRequest request = request(toolId, supplied);
        ValidationResult validation = tool(toolId).validate(request);
        assertTrue(validation.valid(), validation.issues().toString());
        return tool(toolId).execute(request, context());
    }

    private ToolExecutionContext context() throws Exception {
        Path outputDirectory = Files.createDirectories(tempDir.resolve("out"));
        return new ToolExecutionContext(
                "test-run",
                tempDir,
                outputDirectory,
                outputDirectory,
                ignored -> { },
                ignored -> { },
                new CancellationToken()
        );
    }

    private ToolRequest request(String toolId, Map<String, Object> supplied) {
        OpenTool openTool = tool(toolId);
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (ToolParameter parameter : openTool.descriptor().parameters()) {
            if (parameter.defaultValue() != null) {
                parameters.put(parameter.id(), parameter.defaultValue());
            }
        }
        parameters.putAll(supplied);
        return new ToolRequest(toolId, parameters);
    }

    private OpenTool tool(String toolId) {
        ToolRegistry registry = new ToolRegistry();
        BuiltInAnalysis.register(registry);
        return registry.find(toolId).orElseThrow();
    }

    private Map<String, String> readFasta(Path path) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        try (FastaReader reader = new FastaReader(path)) {
            FastaRecord record;
            while ((record = reader.next()) != null) {
                result.put(record.id(), record.sequence());
            }
        }
        return result;
    }
}
