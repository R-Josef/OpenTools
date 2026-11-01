package io.opentools.cli;

import io.opentools.analysis.BuiltInAnalysis;
import io.opentools.core.automation.ToolContracts;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.external.ExternalTools;
import io.opentools.formats.BuiltInTools;
import io.opentools.visualization.BuiltInVisualizations;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class ToolContractCoverageTest {
    @Test
    void everyRegisteredHeadlessToolHasDraftSchema() {
        ToolRegistry registry = new ToolRegistry();
        BuiltInTools.register(registry);
        BuiltInAnalysis.register(registry);
        ExternalTools.register(registry);
        BuiltInVisualizations.register(registry);

        assertFalse(registry.list().isEmpty());
        for (var descriptor : registry.list()) {
            ToolContracts.TaskContract contract = ToolContracts.contract(descriptor);
            assertEquals(descriptor.id(), contract.toolId());
            assertEquals(
                    "https://json-schema.org/draft/2020-12/schema",
                    contract.parameters().get("$schema")
            );
            assertEquals("object", contract.parameters().get("type"));
        }
    }
}
