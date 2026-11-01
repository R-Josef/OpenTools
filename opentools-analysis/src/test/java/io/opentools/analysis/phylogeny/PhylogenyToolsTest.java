package io.opentools.analysis.phylogeny;

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

final class PhylogenyToolsTest {
    @TempDir
    Path tempDir;

    @Test
    void summarizesAndSubsetsNewickTree() throws Exception {
        Path tree = tempDir.resolve("tree.nwk");
        Files.writeString(tree, "((A:0.1,B:0.2)X:0.3,C:0.4)Root;\n");
        Path summary = tempDir.resolve("summary.tsv");
        TaskResult summaryResult = run("tree.newick.summary", Map.of(
                "input", tree, "output", summary));
        assertEquals(3, summaryResult.metrics().get("leaves"));
        assertTrue(Files.readString(summary).contains("max_depth"));

        Path subset = tempDir.resolve("subset.nwk");
        TaskResult subsetResult = run("tree.newick.subset", Map.of(
                "input", tree, "taxa", "A\nC", "output", subset));
        assertEquals(2L, subsetResult.metrics().get("leaves"));
        String newick = Files.readString(subset);
        assertTrue(newick.contains("A"));
        assertTrue(newick.contains("C"));
        assertTrue(!newick.contains("B"));
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
        return tool.execute(request, new TaskContext("tree", tempDir, output, output,
                ignored -> { }, ignored -> { }, new CancellationToken()));
    }
}
