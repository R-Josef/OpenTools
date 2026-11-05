package io.opentools.formats.gxf;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolRequest;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.formats.BuiltInTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GxfEditToolTest {
    @TempDir
    Path tempDir;

    @Test
    void editsGff3AndPreservesHeaders() throws Exception {
        Path input = tempDir.resolve("input.gff3");
        Files.writeString(input, """
                ##gff-version 3
                # keep this header
                chr1\tsrc\tgene\t1\t100\t.\t+\t.\tID=g1;Name=old
                chr1\tsrc\texon\t1\t50\t.\t+\t.\tID=e1;Parent=g1
                """);
        Path output = tempDir.resolve("edited.gff3");
        run("gxf.edit", Map.of(
                "input", input,
                "setType", "exon",
                "setSource", "edited",
                "setStrand", "MINUS",
                "addAttribute", "note=new",
                "removeAttributes", "Name",
                "output", output
        ));

        String text = Files.readString(output).replace("\r\n", "\n");
        assertTrue(text.startsWith("##gff-version 3\n# keep this header\n"));
        assertTrue(text.contains("chr1\tedited\texon\t1\t100\t.\t-\t.\tID=g1;note=new"));
        assertTrue(text.contains("chr1\tedited\texon\t1\t50\t.\t-\t.\tID=e1;Parent=g1;note=new"));
    }

    @Test
    void editsGtfAttributes() throws Exception {
        Path input = tempDir.resolve("input.gtf");
        Files.writeString(input, "chr1\tsrc\texon\t1\t100\t.\t+\t.\tgene_id \"g1\"; transcript_id \"t1\";\n");
        Path output = tempDir.resolve("edited.gtf");
        run("gxf.edit", Map.of(
                "input", input,
                "addAttribute", "custom value",
                "removeAttributes", "gene_id",
                "output", output
        ));

        assertEquals("chr1\tsrc\texon\t1\t100\t.\t+\t.\ttranscript_id \"t1\"; custom \"value\";\n",
                Files.readString(output).replace("\r\n", "\n"));
    }

    private void run(String id, Map<String, Object> supplied) throws Exception {
        ToolRegistry registry = new ToolRegistry();
        BuiltInTools.register(registry);
        Path output = Files.createDirectories(tempDir.resolve("out"));
        ToolExecutionContext context = new ToolExecutionContext("gxf-edit", tempDir, output, output,
                ignored -> { }, ignored -> { }, new CancellationToken());
        ToolRequest request = new ToolRequest(id, supplied);
        var tool = registry.find(id).orElseThrow();
        var validation = tool.validate(request);
        assertTrue(validation.valid(), validation.issues().toString());
        tool.execute(request, context);
    }
}
