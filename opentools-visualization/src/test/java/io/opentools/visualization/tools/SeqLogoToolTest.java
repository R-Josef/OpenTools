package io.opentools.visualization.tools;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SeqLogoToolTest {
    @TempDir
    Path tempDir;

    @Test
    void computesPwmFromAlignedFastaWithPseudocount() throws Exception {
        Path input = tempDir.resolve("aligned.fa");
        Files.writeString(input, ">s1\nACGT\n>s2\nAGGT\n");
        Path output = tempDir.resolve("logo.png");

        var result = new PlotSeqLogoTool().execute(
                request(input, output, Map.of(
                        "inputMode", "FASTA",
                        "pseudocount", 1.0,
                        "exportFormats", "PNG"
                )),
                context()
        );

        assertEquals("FASTA", result.metrics().get("inputMode"));
        assertEquals(2, result.metrics().get("sourceSequences"));
        assertEquals(4, result.metrics().get("positions"));
        assertEquals(1.0, (double) result.metrics().get("pseudocount"), 0.000001);
        assertTrue(Files.isRegularFile(output));
        String pwm = Files.readString(tempDir.resolve("logo.processed.tsv"));
        assertTrue(pwm.contains("1\t0.5\t0.16666666666666666"
                + "\t0.16666666666666666\t0.16666666666666666"));
        assertTrue(pwm.contains("2\t0.16666666666666666"
                + "\t0.3333333333333333\t0.3333333333333333"
                + "\t0.16666666666666666"));
        String plotModel = Files.readString(tempDir.resolve("logo.plot.json"));
        assertTrue(plotModel.contains("pseudocount"), plotModel);
    }

    @Test
    void preservesPositionTableMode() throws Exception {
        Path input = tempDir.resolve("matrix.tsv");
        Files.writeString(input,
                "position\tA\tC\tG\tT\n"
                        + "1\t0.1\t0.2\t0.3\t0.4\n");
        Path output = tempDir.resolve("table-logo.png");

        var result = new PlotSeqLogoTool().execute(
                request(input, output, Map.of("exportFormats", "PNG")),
                context()
        );

        assertEquals("POSITION_TABLE", result.metrics().get("inputMode"));
        assertEquals(0, result.metrics().get("sourceSequences"));
        assertTrue(Files.readString(tempDir.resolve("table-logo.processed.tsv"))
                .contains("1\t0.1\t0.2\t0.3\t0.4"));
    }

    @Test
    void distributesIupacBasesOnBothStrands() throws Exception {
        Path input = tempDir.resolve("ambiguous.fa");
        Files.writeString(input, ">s1\nANGT\n");
        Path output = tempDir.resolve("ambiguous.png");

        var result = new PlotSeqLogoTool().execute(
                request(input, output, Map.of(
                        "inputMode", "FASTA",
                        "bothStrands", true,
                        "exportFormats", "PNG"
                )),
                context()
        );

        assertEquals(2L, result.metrics().get("ambiguousBases"));
        assertEquals(true, result.metrics().get("bothStrands"));
    }

    @Test
    void rejectsUnequalAlignedSequenceLengths() throws Exception {
        Path input = tempDir.resolve("unaligned.fa");
        Files.writeString(input, ">s1\nACGT\n>s2\nACG\n");

        OpenToolException exception = assertThrows(
                OpenToolException.class,
                () -> new PlotSeqLogoTool().execute(
                        request(input, tempDir.resolve("invalid.png"), Map.of(
                                "inputMode", "FASTA",
                                "exportFormats", "PNG"
                        )),
                        context()
                )
        );

        assertEquals("SEQ_LOGO_ALIGNMENT_LENGTH", exception.code());
    }

    private ToolRequest request(Path input, Path output, Map<String, Object> additions) {
        Map<String, Object> parameters = new java.util.LinkedHashMap<>();
        parameters.put("input", input.toString());
        parameters.put("inputMode", "AUTO");
        parameters.put("positionColumn", "position");
        parameters.put("pseudocount", 0.0);
        parameters.put("bothStrands", false);
        parameters.put("title", "Test logo");
        parameters.put("subtitle", "");
        parameters.put("valueLabel", "");
        parameters.put("width", 320);
        parameters.put("height", 240);
        parameters.put("dpi", 72);
        parameters.put("exportFormats", "PNG_SVG_PDF");
        parameters.put("output", output.toString());
        parameters.put("overwrite", false);
        parameters.putAll(additions);
        return new ToolRequest("plot.seqlogo", parameters);
    }

    private ToolExecutionContext context() throws Exception {
        Path run = Files.createDirectories(tempDir.resolve("run-" + System.nanoTime()));
        return new ToolExecutionContext("seqlogo", tempDir, run, run,
                ignored -> { }, ignored -> { }, new CancellationToken());
    }
}
