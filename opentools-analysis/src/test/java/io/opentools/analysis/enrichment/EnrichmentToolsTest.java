package io.opentools.analysis.enrichment;

import io.opentools.analysis.BuiltInAnalysis;
import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OpenTool;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolParameter;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.api.tool.ToolRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EnrichmentToolsTest {
    @TempDir
    Path tempDir;

    @Test
    void runsOfflineGoAndKeggEnrichment() throws Exception {
        Path annotations = tempDir.resolve("annotations.tsv");
        Files.writeString(annotations, """
                gene\tterms
                g1\tGO:1;GO:2
                g2\tGO:1;GO:3
                g3\tGO:1
                g4\tGO:4
                """);
        Path genes = tempDir.resolve("genes.txt");
        Files.writeString(genes, "g1\ng2\ng3\n");
        Path background = tempDir.resolve("background.txt");
        Files.writeString(background, "g1\ng2\ng3\ng4\n");
        Path go = tempDir.resolve("go.tsv");
        TaskResult goResult = run("go.enrich", Map.of(
                "annotations", annotations,
                "genes", genes,
                "background", background,
                "output", go
        ));
        assertTrue((Integer) goResult.metrics().get("terms") > 0);
        String goText = Files.readString(go);
        assertTrue(goText.contains("GO:1"));
        assertTrue(goText.contains("adjusted_p_value"));

        Path pathways = tempDir.resolve("pathways.tsv");
        Files.writeString(pathways, """
                gene\tpathway
                g1\tko1;ko2
                g2\tko1
                g3\tko3
                g4\tko1
                """);
        Path kegg = tempDir.resolve("kegg.tsv");
        TaskResult keggResult = run("kegg.enrich", Map.of(
                "annotations", pathways,
                "genes", genes,
                "background", background,
                "output", kegg
        ));
        assertTrue((Integer) keggResult.metrics().get("terms") > 0);
        assertTrue(Files.readString(kegg).contains("ko1"));
    }

    @Test
    void calculatesGoHierarchyLevels() throws Exception {
        Path hierarchy = tempDir.resolve("hierarchy.tsv");
        Files.writeString(hierarchy, """
                term	parent
                GO:1	.
                GO:2	GO:1
                GO:3	GO:2
                GO:4	GO:2
                """);
        Path info = tempDir.resolve("info.tsv");
        Files.writeString(info, """
                term	name	namespace
                GO:1	root	biological_process
                GO:2	child	biological_process
                GO:3	grandchild	biological_process
                """);
        Path output = tempDir.resolve("levels.tsv");
        TaskResult result = run("go.level", Map.of(
                "hierarchy", hierarchy,
                "termInfo", info,
                "output", output
        ));
        assertEquals(4, result.metrics().get("terms"));
        String text = Files.readString(output);
        assertTrue(text.contains("GO:3\tgrandchild\tbiological_process\t3"));
    }

    @Test
    void parsesOboAliasesDuplicateParentsAndMissingParents() throws Exception {
        Path obo = tempDir.resolve("go.obo");
        Files.writeString(obo, """
                format-version: 1.4

                [Term]
                id: GO:0008150
                name: biological_process
                namespace: biological_process

                [Term]
                id: GO:0000002
                name: mitochondrial genome maintenance
                namespace: biological_process
                alt_id: GO:alias
                is_a: GO:0008150 ! biological_process
                is_a: GO:0008150 ! duplicate line is ignored

                [Term]
                id: GO:0000003
                name: alias parent child
                namespace: biological_process
                is_a: GO:alias ! alternate identifier

                [Term]
                id: GO:0000004
                name: unresolved parent child
                namespace: biological_process
                is_a: GO:missing

                [Typedef]
                id: part_of
                """);
        Path output = tempDir.resolve("obo-levels.tsv");
        TaskResult result = run("go.level", Map.of("hierarchy", obo, "output", output));

        assertEquals(5, result.metrics().get("terms"));
        assertEquals("OBO", result.metrics().get("format"));
        assertEquals(1, result.metrics().get("altIds"));
        assertEquals(1L, result.metrics().get("unresolvedParents"));
        String text = Files.readString(output);
        assertTrue(text.contains("GO:0000002\tmitochondrial genome maintenance\tbiological_process\t2\tGO:0008150"));
        assertTrue(text.contains("GO:0000003\talias parent child\tbiological_process\t3\tGO:0000002"));
        assertTrue(text.contains("GO:missing\t\t\t1\t"));
        assertTrue(text.contains("GO:0000004\tunresolved parent child\tbiological_process\t2\tGO:missing"));
    }

    @Test
    void reportsPureCycleComponentsWithoutHanging() throws Exception {
        Path hierarchy = tempDir.resolve("cycle.tsv");
        Files.writeString(hierarchy, """
                term\tparent
                GO:A\tGO:B
                GO:B\tGO:A
                GO:ROOT\t.
                """);
        Path output = tempDir.resolve("cycle-levels.tsv");
        TaskResult result = run("go.level", Map.of("hierarchy", hierarchy, "output", output));

        assertEquals(2L, result.metrics().get("cycleOrUnreachableTerms"));
        String text = Files.readString(output);
        assertTrue(text.contains("GO:A\t\t\t1\tGO:B"));
        assertTrue(text.contains("GO:B\t\t\t1\tGO:A"));
    }

    private TaskResult run(String id, Map<String, Object> supplied) throws Exception {
        ToolRegistry registry = new ToolRegistry();
        BuiltInAnalysis.register(registry);
        OpenTool tool = registry.find(id).orElseThrow();
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (ToolParameter parameter : tool.descriptor().parameters()) {
            if (parameter.defaultValue() != null) {
                parameters.put(parameter.id(), parameter.defaultValue());
            }
        }
        parameters.putAll(supplied);
        ToolRequest request = new ToolRequest(id, parameters);
        var validation = tool.validate(request);
        assertTrue(validation.valid(), validation.issues().toString());
        Path output = Files.createDirectories(tempDir.resolve("out"));
        return tool.execute(request, new TaskContext("enrichment", tempDir, output, output,
                ignored -> { }, ignored -> { }, new CancellationToken()));
    }
}
