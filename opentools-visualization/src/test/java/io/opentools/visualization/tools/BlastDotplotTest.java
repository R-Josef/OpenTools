package io.opentools.visualization.tools;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OutputArtifact;
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

final class BlastDotplotTest {
    @TempDir
    Path tempDir;

    @Test
    void rendersBlastHspSegmentsWithIdentityBins() throws Exception {
        Path table = tempDir.resolve("hits.tsv");
        Files.writeString(table, """
                qseqid	sseqid	pident	length	mismatch	gapopen	qstart	qend	sstart	send	evalue	bitscore	qlen	slen
                q1	s1	99.0	100	1	0	1	100	5	104	1e-50	180	1000	2000
                q1	s2	85.0	80	2	0	1	80	10	89	1e-30	120	1000	2000
                q1	s3	40.0	60	8	1	20	79	200	141	1e-05	60	1000	2000
                """);
        ToolResult result = new PlotDotplotTool().execute(
                new ToolRequest("plot.dotplot", Map.of(
                        "input", table.toString(),
                        "inputFormat", "BLAST_M6",
                        "width", 900,
                        "height", 700,
                        "exportFormats", "PNG",
                        "title", "BLAST dot plot"
                )),
                context()
        );

        assertEquals(3, result.metrics().get("hits"));
        assertEquals(3, result.metrics().get("identityBins"));
        assertEquals("SEGMENTS", result.metrics().get("chart"));
        OutputArtifact image = result.artifacts().stream()
                .filter(artifact -> artifact.path().getFileName().toString().endsWith(".png"))
                .findFirst()
                .orElseThrow();
        assertTrue(Files.isRegularFile(image.path()));
    }

    @Test
    void keepsNumericTableMode() throws Exception {
        Path table = tempDir.resolve("xy.tsv");
        Files.writeString(table, "x\ty\tgroup\n1\t2\ta\n3\t4\tb\n");
        ToolResult result = new PlotDotplotTool().execute(
                new ToolRequest("plot.dotplot", Map.of(
                        "input", table.toString(),
                        "inputFormat", "X_Y_TABLE",
                        "xColumn", "x",
                        "yColumn", "y",
                        "groupColumn", "group",
                        "width", 600,
                        "height", 400,
                        "exportFormats", "PNG"
                )),
                context()
        );
        assertEquals("SCATTER", result.metrics().get("chart"));
        assertEquals(2, result.metrics().get("series"));
    }

    private ToolExecutionContext context() {
        Path run = tempDir.resolve("run-" + System.nanoTime());
        return new ToolExecutionContext("run", tempDir, run, run,
                ignored -> { }, ignored -> { }, new CancellationToken());
    }
}
