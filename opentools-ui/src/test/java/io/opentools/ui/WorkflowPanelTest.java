package io.opentools.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class WorkflowPanelTest {
    @Test
    void parsesAndOrdersAWorkflow() throws Exception {
        String json = """
                {
                  "name": "demo",
                  "steps": [
                    {"id": "b", "toolId": "table.preview",
                     "parameters": {"input": "${a.output}"}, "dependsOn": ["a"]},
                    {"id": "a", "toolId": "fasta.stats", "parameters": {"input": "data.fa"}}
                  ]
                }
                """;
        WorkflowPanel.WorkflowSpec spec = WorkflowPanel.parse(json);
        assertEquals("demo", spec.name());
        assertEquals(List.of("a", "b"), WorkflowPanel.topologicalOrder(spec));
    }

    @Test
    void rejectsCyclesAndMissingDependencies() {
        assertThrows(IllegalArgumentException.class, () -> WorkflowPanel.topologicalOrder(
                new WorkflowPanel.WorkflowSpec("cycle", false, List.of(
                        new WorkflowPanel.Step("a", "fasta.stats", java.util.Map.of(), List.of("b")),
                        new WorkflowPanel.Step("b", "fasta.stats", java.util.Map.of(), List.of("a"))
                ))));
        assertThrows(IllegalArgumentException.class, () -> WorkflowPanel.topologicalOrder(
                new WorkflowPanel.WorkflowSpec("missing", false, List.of(
                        new WorkflowPanel.Step("a", "fasta.stats", java.util.Map.of(), List.of("missing"))
                ))));
    }
}
