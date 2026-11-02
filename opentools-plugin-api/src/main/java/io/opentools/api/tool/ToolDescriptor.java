package io.opentools.api.tool;

import java.util.List;
import java.util.Objects;

/**
 * Metadata describing one tool for discovery, forms, and documentation.
 *
 * @param id stable tool identifier
 * @param name display name
 * @param description tool summary
 * @param category tool category
 * @param aliases alternate search names
 * @param inputFormats accepted input labels
 * @param outputFormats produced output labels
 * @param version tool implementation version
 * @param parameters input parameter definitions
 */
public record ToolDescriptor(
        String id,
        String name,
        String description,
        String category,
        List<String> aliases,
        List<String> inputFormats,
        List<String> outputFormats,
        String version,
        List<ToolParameter> parameters
) {
    public ToolDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(category, "category");
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        inputFormats = inputFormats == null ? List.of() : List.copyOf(inputFormats);
        outputFormats = outputFormats == null ? List.of() : List.copyOf(outputFormats);
        version = version == null ? "1.0.0" : version;
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }

    /**
     * Returns searchable text assembled from descriptor metadata.
     *
     * @return lowercase search text
     */
    public String searchText() {
        return String.join(" ", id, name, description, category,
                String.join(" ", aliases),
                String.join(" ", inputFormats),
                String.join(" ", outputFormats)).toLowerCase();
    }
}
