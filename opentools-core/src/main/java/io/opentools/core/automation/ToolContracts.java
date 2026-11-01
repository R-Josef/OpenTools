package io.opentools.core.automation;

import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ToolContracts {
    public static final String SCHEMA_VERSION = "2020-12";

    private ToolContracts() {
    }

    public static TaskContract contract(ToolDescriptor descriptor) {
        return new TaskContract(
                descriptor.id(),
                descriptor.version(),
                parameterSchema(descriptor)
        );
    }

    public static Map<String, Object> parameterSchema(ToolDescriptor descriptor) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("title", descriptor.name());
        schema.put("description", descriptor.description());
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (ToolParameter parameter : descriptor.parameters()) {
            properties.put(parameter.id(), parameterSchema(parameter));
            if (parameter.required()) {
                required.add(parameter.id());
            }
        }
        schema.put("properties", properties);
        schema.put("required", List.copyOf(required));
        return Collections.unmodifiableMap(schema);
    }

    private static Map<String, Object> parameterSchema(ToolParameter parameter) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("title", parameter.title());
        schema.put("description", parameter.description());
        schema.put("type", jsonType(parameter.type()));
        schema.put("x-opentools-parameter-type", parameter.type().name());
        if (parameter.defaultValue() != null) {
            schema.put("default", parameter.defaultValue());
        }
        if (!parameter.options().isEmpty()) {
            schema.put("enum", parameter.options());
        }
        if (parameter.min() != null) {
            schema.put("minimum", parameter.min());
        }
        if (parameter.max() != null) {
            schema.put("maximum", parameter.max());
        }
        if (parameter.placeholder() != null && !parameter.placeholder().isBlank()) {
            schema.put("x-opentools-placeholder", parameter.placeholder());
        }
        if (parameter.advanced()) {
            schema.put("x-opentools-advanced", true);
        }
        if (parameter.type() == ParameterType.FILE
                || parameter.type() == ParameterType.OUTPUT_FILE
                || parameter.type() == ParameterType.DIRECTORY) {
            schema.put("format", "path");
            schema.put("x-opentools-path-kind", parameter.type().name().toLowerCase(java.util.Locale.ROOT));
        }
        return Collections.unmodifiableMap(schema);
    }

    private static String jsonType(ParameterType type) {
        return switch (type) {
            case INTEGER -> "integer";
            case DECIMAL -> "number";
            case BOOLEAN -> "boolean";
            case STRING, TEXT, FILE, OUTPUT_FILE, DIRECTORY, ENUM -> "string";
        };
    }

    public record TaskContract(String toolId, String toolVersion, Map<String, Object> parameters) {
        public TaskContract {
            parameters = parameters == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        }
    }
}
