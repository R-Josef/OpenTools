package io.opentools.analysis.sequence;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PrimerCheckToolTest {
    @TempDir
    Path tempDir;

    @Test
    void findsAnExactAmpliconAndReportsCoordinates() throws Exception {
        String genome = "G".repeat(10) + "ACGTACGT" + "A".repeat(42) + "TGCATGCA" + "C".repeat(20);
        Path fasta = write("genome.fa", ">chr1\n" + genome + "\n");
        Path primers = write("primers.tsv", "name\tforward\treverse\np1\tACGTACGT\tTGCATGCA\n");
        Path output = tempDir.resolve("amplicons.tsv");

        ToolResult result = new PrimerCheckTool().execute(request(fasta, primers, output, Map.of(
                "maxMismatches", 0,
                "minProductSize", 50,
                "maxProductSize", 3000
        )), context());

        assertEquals(1L, result.metrics().get("amplicons"));
        String table = Files.readString(output);
        assertTrue(table.contains("p1\tchr1\t11\t18\t61\t68\t58\t0\t0"), table);
    }

    @Test
    void allowsConfiguredMismatches() throws Exception {
        String genome = "G".repeat(10) + "ACGTTCGT" + "A".repeat(42) + "TGCATGCA" + "C".repeat(20);
        Path fasta = write("mismatch.fa", ">chr1\n" + genome + "\n");
        Path primers = write("mismatch-primers.tsv", "name\tforward\treverse\np1\tACGTACGT\tTGCATGCA\n");
        Path output = tempDir.resolve("mismatch.tsv");

        ToolResult result = new PrimerCheckTool().execute(request(fasta, primers, output, Map.of(
                "maxMismatches", 1,
                "minProductSize", 50,
                "maxProductSize", 3000
        )), context());

        assertEquals(1L, result.metrics().get("amplicons"));
        assertTrue(Files.readString(output).contains("\t58\t1\t0"));
    }

    @Test
    void returnsHeaderOnlyWhenProductLimitsExcludeThePair() throws Exception {
        String genome = "G".repeat(10) + "ACGTACGT" + "A".repeat(42) + "TGCATGCA" + "C".repeat(20);
        Path fasta = write("short.fa", ">chr1\n" + genome + "\n");
        Path primers = write("short-primers.tsv", "name\tforward\treverse\np1\tACGTACGT\tTGCATGCA\n");
        Path output = tempDir.resolve("short.tsv");

        ToolResult result = new PrimerCheckTool().execute(request(fasta, primers, output, Map.of(
                "maxMismatches", 0,
                "minProductSize", 1,
                "maxProductSize", 20
        )), context());

        assertEquals(0L, result.metrics().get("amplicons"));
        assertTrue(Files.readString(output).startsWith("primer\tsequence\tforward_start"));
    }

    private ToolRequest request(Path fasta, Path primers, Path output, Map<String, Object> extra) {
        java.util.Map<String, Object> parameters = new java.util.LinkedHashMap<>();
        parameters.put("genome", fasta);
        parameters.put("primers", primers);
        parameters.put("output", output);
        parameters.put("maxAmplicons", 100000);
        parameters.putAll(extra);
        return new ToolRequest("sequence.primer-check", parameters);
    }

    private Path write(String name, String content) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    private ToolExecutionContext context() {
        Path run = tempDir.resolve("run");
        return new ToolExecutionContext("primer", tempDir, run, run,
                ignored -> { }, ignored -> { }, new CancellationToken());
    }
}
