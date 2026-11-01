package io.opentools.formats.fasta;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.ToolRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class CdsTranslateToolTest {
    @TempDir
    Path tempDir;

    @Test
    void translatesStandardCodeAndTrimsStop() throws Exception {
        Path input = tempDir.resolve("cds.fa");
        Path output = tempDir.resolve("protein.fa");
        Files.writeString(input, ">gene1\nATGGCTTAA\n");
        TaskContext context = new TaskContext(
                "run",
                tempDir,
                tempDir.resolve("run"),
                tempDir.resolve("run"),
                ignored -> { },
                ignored -> { },
                new CancellationToken()
        );

        var result = new CdsTranslateTool().execute(
                new ToolRequest("fasta.translate-cds", Map.of(
                        "input", input.toString(),
                        "output", output.toString(),
                        "frame", "1",
                        "trimTrailingStop", true
                )),
                context
        );

        assertTrue(Files.readString(output).contains("MA"));
        assertTrue(result.metrics().containsKey("ambiguousCodons"));
    }

    @Test
    void translatesWithVertebrateMitochondrialCode() throws Exception {
        Path input = tempDir.resolve("mitochondrial.fa");
        Path output = tempDir.resolve("mitochondrial-protein.fa");
        Files.writeString(input, ">mito\nATGAGATGA\n");
        TaskContext context = new TaskContext(
                "run",
                tempDir,
                tempDir.resolve("run"),
                tempDir.resolve("run"),
                ignored -> { },
                ignored -> { },
                new CancellationToken()
        );

        var result = new CdsTranslateTool().execute(
                new ToolRequest("fasta.translate-cds", Map.of(
                        "input", input.toString(),
                        "output", output.toString(),
                        "frame", "1",
                        "geneticCode", "2",
                        "trimTrailingStop", false
                )),
                context
        );

        assertTrue(Files.readString(output).contains("M*W"));
        assertEquals("2", result.metrics().get("geneticCode"));
    }

    @Test
    void parallelTranslationMatchesSequentialOutput() throws Exception {
        Path input = tempDir.resolve("parallel-cds.fa");
        StringBuilder records = new StringBuilder();
        for (int index = 0; index < 40; index++) {
            records.append(">gene_").append(index).append('\n')
                    .append(index % 2 == 0 ? "ATGGCTTAA" : "ATGNNNTGA")
                    .append('\n');
        }
        Files.writeString(input, records);
        Path sequential = tempDir.resolve("sequential-protein.fa");
        Path parallel = tempDir.resolve("parallel-protein.fa");
        TaskContext context = new TaskContext(
                "run",
                tempDir,
                tempDir.resolve("run"),
                tempDir.resolve("run"),
                ignored -> { },
                ignored -> { },
                new CancellationToken()
        );

        new CdsTranslateTool().execute(new ToolRequest("fasta.translate-cds", Map.of(
                "input", input.toString(),
                "output", sequential.toString(),
                "threads", 1
        )), context);
        var result = new CdsTranslateTool().execute(
                new ToolRequest("fasta.translate-cds", Map.of(
                        "input", input.toString(),
                        "output", parallel.toString(),
                        "threads", 3
                )),
                context
        );

        assertEquals(Files.readString(sequential), Files.readString(parallel));
        assertEquals(3, result.metrics().get("threads"));
    }
}
