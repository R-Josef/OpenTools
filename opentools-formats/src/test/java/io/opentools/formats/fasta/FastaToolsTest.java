package io.opentools.formats.fasta;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FastaToolsTest {
    @TempDir
    Path tempDir;

    @Test
    void calculatesStatsAndWritesTsv() throws Exception {
        Path input = tempDir.resolve("input.fa");
        Files.writeString(input, ">a\nGGCC\n>b\nAT\n");
        Path output = tempDir.resolve("stats.tsv");
        TaskContext context = context();

        var result = new FastaStatsTool().execute(
                new ToolRequest("fasta.stats", Map.of("input", input.toString(), "output", output.toString())),
                context
        );

        assertEquals(2L, result.metrics().get("sequences"));
        assertEquals(6L, result.metrics().get("totalBases"));
        assertTrue(Files.readString(output).contains("a\t4\t100.00"));
    }

    @Test
    void parallelFastaStatsPreservesOrderedOutput() throws Exception {
        Path input = tempDir.resolve("parallel.fa");
        StringBuilder content = new StringBuilder();
        for (int index = 0; index < 75; index++) {
            content.append(">seq_").append(index).append('\n')
                    .append(index % 2 == 0 ? "ACGT".repeat(index % 7 + 1)
                            : "GGN".repeat(index % 5 + 1))
                    .append('\n');
        }
        Files.writeString(input, content);
        Path sequential = tempDir.resolve("sequential.tsv");
        Path parallel = tempDir.resolve("parallel.tsv");

        new FastaStatsTool().execute(new ToolRequest("fasta.stats", Map.of(
                "input", input.toString(),
                "output", sequential.toString(),
                "threads", 1
        )), context());
        var result = new FastaStatsTool().execute(new ToolRequest("fasta.stats", Map.of(
                "input", input.toString(),
                "output", parallel.toString(),
                "threads", 3
        )), context());

        assertEquals(Files.readString(sequential), Files.readString(parallel));
        assertEquals(3, result.metrics().get("threads"));
        assertTrue((Boolean) result.metrics().get("parallelRecords"));
    }

    @Test
    void extractsRequestedRecordsAndTracksMissingIds() throws Exception {
        Path input = tempDir.resolve("input.fa");
        Files.writeString(input, ">a description\nACGT\n>b\nTT\n");
        Path output = tempDir.resolve("out.fa");
        TaskContext context = context();
        ToolRequest request = new ToolRequest("fasta.extract", Map.of(
                "input", input.toString(),
                "ids", "a\nmissing\n",
                "output", output.toString(),
                "matchMode", "ID_TOKEN",
                "ignoreCase", true
        ));

        ValidationResult validation = new FastaExtractTool().validate(request);
        var result = new FastaExtractTool().execute(request, context);

        assertTrue(validation.valid());
        assertEquals(1, result.metrics().get("found"));
        assertEquals(1, result.metrics().get("missing"));
        assertTrue(Files.readString(output).contains(">a description"));
        assertTrue(Files.exists(tempDir.resolve("run/missing-ids.txt")));
    }

    @Test
    void rejectsMissingInputFile() {
        ValidationResult validation = new FastaStatsTool().validate(
                new ToolRequest("fasta.stats", Map.of("input", tempDir.resolve("absent.fa").toString()))
        );

        assertFalse(validation.valid());
    }

    @Test
    void mergesAndSplitsFastaFiles() throws Exception {
        Path input = tempDir.resolve("merge-input.fa");
        Files.writeString(input, ">a\nAA\n>b\nCCC\n>c\nG\n");
        Path merged = tempDir.resolve("merged.fa");
        TaskContext context = context();
        var mergedResult = new FastaMergeTool().execute(
                new ToolRequest("fasta.merge", Map.of(
                        "inputs", input.toString(),
                        "output", merged.toString()
                )),
                context
        );
        assertEquals(3L, mergedResult.metrics().get("sequences"));

        Path parts = tempDir.resolve("parts");
        Files.createDirectories(parts);
        var splitResult = new FastaSplitTool().execute(
                new ToolRequest("fasta.split", Map.of(
                        "input", merged.toString(),
                        "recordsPerFile", 2,
                        "outputDirectory", parts.toString()
                )),
                context
        );
        assertEquals(3L, splitResult.metrics().get("records"));
        assertEquals(2, splitResult.metrics().get("parts"));
        assertTrue(Files.exists(parts.resolve("part-0001.fa")));
        assertTrue(Files.exists(parts.resolve("part-0002.fa")));
    }

    private TaskContext context() {
        Path runDir = tempDir.resolve("run");
        return new TaskContext(
                "run",
                tempDir,
                runDir,
                runDir,
                ignored -> { },
                ignored -> { },
                new CancellationToken()
        );
    }
}
