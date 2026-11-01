package io.opentools.ui;

import io.opentools.api.tool.ToolDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ToolCatalogPanelOrderingTest {
    @Test
    void groupsToolsInLearningOrderAndSortsWithinCategory() {
        List<ToolDescriptor> tools = List.of(
                tool("zeta", "Zeta Tool", "Future"),
                tool("table-z", "Zulu Table", "Table"),
                tool("plot", "Venn / UpSet", "Visualization"),
                tool("sequence", "FASTA Statistics", "Sequence"),
                tool("table-f", "Filter Table", "Table"),
                tool("alignment", "MUSCLE Alignment", "Alignment")
        );

        List<ToolDescriptor> ordered = ToolCatalogPanel.orderedTools(
                tools,
                ToolDescriptor::name
        );

        assertEquals(List.of(
                "sequence",
                "table-f",
                "table-z",
                "alignment",
                "plot",
                "zeta"
        ), ordered.stream().map(ToolDescriptor::id).toList());
    }

    @Test
    void usesWorkflowCategoryOrder() {
        List<ToolDescriptor> tools = List.of(
                tool("visual", "Visual", "Visualization"),
                tool("hts", "HTS", "HTS"),
                tool("compare", "Compare", "Comparative genomics"),
                tool("phylogeny", "Phylogeny", "Phylogeny"),
                tool("align", "Align", "Alignment"),
                tool("table", "Table", "Table"),
                tool("genome", "Genome", "Genome annotation"),
                tool("sequence", "Sequence", "Sequence")
        );

        List<ToolDescriptor> ordered = ToolCatalogPanel.orderedTools(
                tools,
                ToolDescriptor::name
        );

        assertEquals(List.of(
                "Sequence",
                "Genome annotation",
                "Table",
                "Alignment",
                "Phylogeny",
                "Comparative genomics",
                "HTS",
                "Visualization"
        ), ordered.stream().map(ToolDescriptor::category).toList());
    }

    private static ToolDescriptor tool(String id, String name, String category) {
        return new ToolDescriptor(
                id,
                name,
                name,
                category,
                List.of(),
                List.of(),
                List.of(),
                "1.0.0",
                List.of()
        );
    }
}
