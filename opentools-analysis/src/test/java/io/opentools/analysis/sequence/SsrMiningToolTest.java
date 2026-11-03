package io.opentools.analysis.sequence;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SsrMiningToolTest {
    @TempDir
    Path tempDir;

    @Test
    void findsPerfectDiAndTetranucleotideRepeats() throws Exception {
        Path fasta = tempDir.resolve("ssr.fa");
        Files.writeString(fasta, ">s1\nACACACACGTTATATATATC\n");
        Path output = tempDir.resolve("ssr.tsv");

        ToolResult result = new SsrMiningTool().execute(
                new ToolRequest("sequence.ssr", Map.of(
                        "input", fasta,
                        "maxMotifLength", 6,
                        "minRepeats", 3,
                        "maxLoci", 100000,
                        "output", output
                )), context());

        assertEquals(2L, result.metrics().get("loci"));
        assertEquals(16L, result.metrics().get("repeatedBases"));
        String table = Files.readString(output);
        assertTrue(table.contains("s1\t1\t8\tAC\t4\t8\t2"));
        assertTrue(table.contains("s1\t11\t18\tTA\t4\t8\t2"));
    }

    @Test
    void capsLociAndMarksTruncation() throws Exception {
        Path fasta = tempDir.resolve("ssr-cap.fa");
        Files.writeString(fasta, ">s1\nACACACACGTTATATATATC\n");
        Path output = tempDir.resolve("ssr-cap.tsv");

        ToolResult result = new SsrMiningTool().execute(
                new ToolRequest("sequence.ssr", Map.of(
                        "input", fasta,
                        "maxMotifLength", 6,
                        "minRepeats", 3,
                        "maxLoci", 1,
                        "output", output
                )), context());

        assertEquals(1L, result.metrics().get("loci"));
        assertEquals(true, result.metrics().get("truncated"));
        assertTrue(Files.readString(output).contains("# truncated at 1 loci"));
    }

    @Test
    void returnsHeaderOnlyWhenNoRepeatPassesThreshold() throws Exception {
        Path fasta = tempDir.resolve("ssr-none.fa");
        Files.writeString(fasta, ">s1\nACACACACG\n");
        Path output = tempDir.resolve("ssr-none.tsv");

        ToolResult result = new SsrMiningTool().execute(
                new ToolRequest("sequence.ssr", Map.of(
                        "input", fasta,
                        "maxMotifLength", 6,
                        "minRepeats", 10,
                        "maxLoci", 100000,
                        "output", output
                )), context());

        assertEquals(0L, result.metrics().get("loci"));
        assertEquals(false, result.metrics().get("truncated"));
        assertTrue(Files.readString(output).startsWith("sequence\tstart\tend"));
    }

    private ToolExecutionContext context() {
        Path run = tempDir.resolve("run");
        return new ToolExecutionContext("ssr", tempDir, run, run,
                ignored -> { }, ignored -> { }, new CancellationToken());
    }
}
