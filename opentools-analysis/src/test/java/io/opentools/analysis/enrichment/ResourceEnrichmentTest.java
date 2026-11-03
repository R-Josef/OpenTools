package io.opentools.analysis.enrichment;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ResourceEnrichmentTest {
    @TempDir
    Path tempDir;
    private String previous;

    @BeforeEach
    void useTemporaryLibrary() {
        previous = System.getProperty("opentools.resources.dir");
        System.setProperty("opentools.resources.dir", tempDir.resolve("resources").toString());
    }

    @AfterEach
    void restoreLibrary() {
        if (previous == null) {
            System.clearProperty("opentools.resources.dir");
        } else {
            System.setProperty("opentools.resources.dir", previous);
        }
    }

    @Test
    void goEnrichmentReadsActiveAnnotationFromPack() throws Exception {
        Path obo = write("go.obo", """
                format-version: 1.4
                [Term]
                id: GO:0001
                name: alpha
                [Term]
                id: GO:0002
                name: beta
                """);
        Path annotations = write("go.tsv", "gene1\tGO:0001;GO:0002\ngene2\tGO:0001\n");
        new ResourcePacks(tempDir.resolve("resources")).importGo(
                obo, annotations, "go-test", "Test GO", "1.0", true);
        Path genes = write("genes.txt", "gene1\n");
        Path output = tempDir.resolve("go-result.tsv");

        ToolResult result = new GoEnrichTool().execute(new ToolRequest("go.enrich", Map.of(
                "resourceId", "go-test",
                "genes", genes.toString(),
                "output", output.toString()
        )), context());

        assertTrue(Files.isRegularFile(output));
        assertEquals("go-test", result.metrics().get("resourceId"));
        assertEquals(2, result.metrics().get("terms"));
    }

    @Test
    void goLevelReadsOboFromPack() throws Exception {
        Path obo = write("go.obo", """
                format-version: 1.4
                [Term]
                id: GO:0001
                name: alpha
                [Term]
                id: GO:0002
                name: beta
                is_a: GO:0001
                """);
        new ResourcePacks(tempDir.resolve("resources")).importGo(
                obo, null, "go-level", "GO levels", "1.0", true);
        Path output = tempDir.resolve("levels.tsv");

        ToolResult result = new GoLevelTool().execute(new ToolRequest("go.level", Map.of(
                "resourceId", "go-level",
                "output", output.toString()
        )), context());

        assertTrue(Files.isRegularFile(output));
        assertEquals("go-level", result.metrics().get("resourceId"));
        assertEquals(2, result.metrics().get("terms"));
    }

    @Test
    void keggEnrichmentReadsMappingFromPack() throws Exception {
        Path mapping = write("kegg.tsv", "gene1\tmap00010,map00020\ngene2\tmap00010\n");
        new ResourcePacks(tempDir.resolve("resources")).importKegg(
                mapping, null, "kegg-test", "KEGG", "1.0", true);
        Path genes = write("genes.txt", "gene1\n");
        Path output = tempDir.resolve("kegg-result.tsv");

        ToolResult result = new KeggEnrichTool().execute(new ToolRequest("kegg.enrich", Map.of(
                "resourceId", "kegg-test",
                "genes", genes.toString(),
                "output", output.toString()
        )), context());

        assertTrue(Files.isRegularFile(output));
        assertEquals("kegg-test", result.metrics().get("resourceId"));
        assertEquals(2, result.metrics().get("terms"));
    }

    private Path write(String name, String content) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    private ToolExecutionContext context() {
        Path run = tempDir.resolve("run-" + System.nanoTime());
        return new ToolExecutionContext("run", tempDir, run, run,
                ignored -> { }, ignored -> { }, new CancellationToken());
    }
}
