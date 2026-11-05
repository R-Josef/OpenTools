package io.opentools.api.tool;

import java.util.List;
import java.util.Objects;

/**
 * Definition of one tool input parameter.
 *
 * @param id parameter identifier used in requests
 * @param title fallback display title, usually in English
 * @param description fallback input guidance, usually in English
 * @param type input type
 * @param required whether a value is required
 * @param defaultValue default value
 * @param options allowed values for enum parameters
 * @param min minimum numeric value
 * @param max maximum numeric value
 * @param advanced whether the parameter is shown in advanced mode
 * @param placeholder optional input placeholder
 */
public record ToolParameter(
        String id,
        String title,
        String description,
        ParameterType type,
        boolean required,
        Object defaultValue,
        List<String> options,
        Double min,
        Double max,
        boolean advanced,
        String placeholder
) {
    public ToolParameter {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(type, "type");
        options = options == null ? List.of() : List.copyOf(options);
        if (type == ParameterType.ENUM && options.isEmpty()) {
            throw new IllegalArgumentException("Enum parameter requires options: " + id);
        }
    }

    public static ToolParameter requiredFile(String id, String title, String description) {
        return new ToolParameter(id, title, description, ParameterType.FILE, true, null, List.of(), null, null, false, null);
    }

    public static ToolParameter optionalFile(String id, String title, String description) {
        return new ToolParameter(id, title, description, ParameterType.FILE, false, null, List.of(), null, null, false, null);
    }

    public static ToolParameter requiredFiles(String id, String title, String description) {
        return new ToolParameter(id, title, description, ParameterType.FILES, true, null, List.of(), null, null, false, null);
    }

    public static ToolParameter optionalFiles(String id, String title, String description) {
        return new ToolParameter(id, title, description, ParameterType.FILES, false, null, List.of(), null, null, false, null);
    }

    public static ToolParameter optionalOutputFile(String id, String title, String description) {
        return new ToolParameter(id, title, description, ParameterType.OUTPUT_FILE, false, null, List.of(), null, null, false, null);
    }

    public static ToolParameter optionalDirectory(String id, String title, String description) {
        return new ToolParameter(id, title, description, ParameterType.DIRECTORY, false, null, List.of(), null, null, false, null);
    }

    public static ToolParameter requiredText(String id, String title, String description) {
        return new ToolParameter(id, title, description, ParameterType.TEXT, true, null, List.of(), null, null, false, null);
    }

    public static ToolParameter optionalText(String id, String title, String description) {
        return new ToolParameter(id, title, description, ParameterType.TEXT, false, "", List.of(), null, null, false, null);
    }

    public static ToolParameter string(String id, String title, String description, String defaultValue) {
        return new ToolParameter(id, title, description, ParameterType.STRING, false, defaultValue, List.of(), null, null, false, null);
    }

    public static ToolParameter bool(String id, String title, String description, boolean defaultValue) {
        return new ToolParameter(id, title, description, ParameterType.BOOLEAN, false, defaultValue, List.of(), null, null, false, null);
    }

    public static ToolParameter integer(String id, String title, String description, Integer defaultValue, int min, int max) {
        return new ToolParameter(id, title, description, ParameterType.INTEGER, false, defaultValue, List.of(),
                (double) min, (double) max, true, null);
    }

    public static ToolParameter requiredInteger(String id, String title, String description, int defaultValue, int min, int max) {
        return new ToolParameter(id, title, description, ParameterType.INTEGER, true, defaultValue, List.of(),
                (double) min, (double) max, false, null);
    }

    public static ToolParameter decimal(String id, String title, String description, double defaultValue, double min, double max) {
        return new ToolParameter(id, title, description, ParameterType.DECIMAL, false, defaultValue, List.of(),
                min, max, true, null);
    }
}
