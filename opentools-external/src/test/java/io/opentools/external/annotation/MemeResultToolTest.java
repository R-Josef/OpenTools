package io.opentools.external.annotation;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolRequest;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.external.ExternalTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MemeResultToolTest {
    @TempDir
    Path tempDir;

    @Test
    void writesMotifAndMastHitTables() throws Exception {
        Path meme = tempDir.resolve("meme.xml");
        Files.writeString(meme, """
                <?xml version="1.0"?>
                <MEME version="5.5.5">
                  <motifs>
                    <motif id="MEME-1" name="M1" width="8" sites="12" evalue="1.2e-10"/>
                  </motifs>
                </MEME>
                """);
        Path motifs = run(meme).resolve("meme-motifs.tsv");
        assertTrue(Files.readString(motifs).contains("MEME-1\tM1\t8\t12"));

        Path mast = tempDir.resolve("mast.xml");
        Files.writeString(mast, """
                <?xml version="1.0"?>
                <MAST><sequences><sequence id="seq1"><seg start="1" stop="100">
                  <hit motiff="MEME-1" strand="+" position="9" pvalue="2.5e-6" match="ACGTACGT"/>
                </seg></sequence></sequences></MAST>
                """);
        Path hits = runMast(mast).resolve("mast-hits.tsv");
        assertTrue(Files.readString(hits).contains("seq1\tMEME-1\t10\t17"));
    }

    private Path run(Path input) throws Exception {
        return execute(input, tempDir.resolve("meme-out"));
    }

    private Path runMast(Path input) throws Exception {
        return execute(input, tempDir.resolve("mast-out"));
    }

    private Path execute(Path input, Path out) throws Exception {
        Files.createDirectories(out);
        ToolRegistry registry = new ToolRegistry();
        ExternalTools.register(registry);
        var tool = registry.find("annotation.meme.results").orElseThrow();
        ToolExecutionContext context = new ToolExecutionContext("meme-result", tempDir, out, out,
                ignored -> { }, ignored -> { }, new CancellationToken());
        ToolRequest request = new ToolRequest("annotation.meme.results", Map.of(
                "input", input,
                "motifOutput", out.resolve("meme-motifs.tsv"),
                "hitOutput", out.resolve("mast-hits.tsv")
        ));
        var validation = tool.validate(request);
        assertTrue(validation.valid(), validation.issues().toString());
        tool.execute(request, context);
        return out;
    }
}
