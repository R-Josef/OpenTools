package io.opentools.core.automation;

import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class ToolContractsTest {
    @Test
    void generatesDraftSchemaForAllParameterKinds() {
        ToolDescriptor descriptor = new ToolDescriptor(
                "test.schema",
                "Schema test",
                "Tests schema generation.",
                "Test",
                List.of(),
                List.of("FASTA"),
                List.of("TSV"),
                "2.0.0",
                List.of(
                        ToolParameter.requiredFile("input", "Input", "Input file"),
                        ToolParameter.optionalOutputFile("output", "Output", "Output file"),
                        new ToolParameter("mode", "Mode", "Mode choice", ParameterType.ENUM, false,
                                "A", List.of("A", "B"), null, null, false, null),
                        ToolParameter.integer("threads", "Threads", "Worker count", 4, 1, 32),
                        ToolParameter.decimal("threshold", "Threshold", "Cutoff", 0.5, 0, 1),
                        ToolParameter.bool("enabled", "Enabled", "Toggle", true),
                        ToolParameter.requiredText("text", "Text", "Multiline text")
                )
        );

        ToolContracts.TaskContract contract = ToolContracts.contract(descriptor);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) contract.parameters().get("properties");

        assertEquals("test.schema", contract.toolId());
        assertEquals("2.0.0", contract.toolVersion());
        assertEquals(List.of("input", "text"), contract.parameters().get("required"));
        assertEquals("file", ((Map<?, ?>) properties.get("input")).get("x-opentools-path-kind"));
        assertEquals("output_file", ((Map<?, ?>) properties.get("output")).get("x-opentools-path-kind"));
        assertEquals("integer", ((Map<?, ?>) properties.get("threads")).get("type"));
        assertEquals(1.0, ((Map<?, ?>) properties.get("threads")).get("minimum"));
        assertEquals(List.of("A", "B"), ((Map<?, ?>) properties.get("mode")).get("enum"));
        assertEquals("boolean", ((Map<?, ?>) properties.get("enabled")).get("type"));
        assertFalse((Boolean) contract.parameters().get("additionalProperties"));
    }
}
