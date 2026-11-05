package io.opentools.api.tool;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable parameter map passed to a tool.
 */
public final class ToolRequest {
    private final String toolId;
    private final Map<String, Object> parameters;

    /**
     * Creates a tool request.
     *
     * @param toolId requested tool identifier
     * @param parameters parameter values
     */
    public ToolRequest(String toolId, Map<String, Object> parameters) {
        this.toolId = Objects.requireNonNull(toolId, "toolId");
        this.parameters = Map.copyOf(new LinkedHashMap<>(parameters == null ? Map.of() : parameters));
    }

    /**
     * Returns the requested tool identifier.
     *
     * @return tool identifier
     */
    public String toolId() {
        return toolId;
    }

    /**
     * Returns all parameter values.
     *
     * @return immutable parameter map
     */
    public Map<String, Object> parameters() {
        return parameters;
    }

    /**
     * Returns a raw parameter value.
     *
     * @param id parameter identifier
     * @return value or {@code null}
     */
    public Object value(String id) {
        return parameters.get(id);
    }

    /**
     * Reads a nonblank string parameter.
     *
     * @param id parameter identifier
     * @return optional trimmed value
     */
    public Optional<String> optionalString(String id) {
        Object value = parameters.get(id);
        if (value == null) {
            return Optional.empty();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    /**
     * Reads a string parameter, returning an empty string when absent.
     *
     * @param id parameter identifier
     * @return trimmed value or empty string
     */
    public String string(String id) {
        return optionalString(id).orElse("");
    }

    /**
     * Reads a path parameter.
     *
     * @param id parameter identifier
     * @return path or {@code null}
     */
    public Path path(String id) {
        return optionalString(id).map(Path::of).orElse(null);
    }

    /**
     * Reads a multi-file parameter. Values may be a list, an array, or a
     * comma-separated string supplied through the CLI.
     *
     * @param id parameter identifier
     * @return immutable list of paths, never null
     */
    public List<Path> paths(String id) {
        Object value = parameters.get(id);
        if (value == null) return List.of();
        List<String> parts = new java.util.ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null) parts.add(String.valueOf(item).trim());
            }
        } else if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                Object item = java.lang.reflect.Array.get(value, index);
                if (item != null) parts.add(String.valueOf(item).trim());
            }
        } else {
            for (String item : String.valueOf(value).split(",")) {
                parts.add(item.trim());
            }
        }
        List<Path> paths = new java.util.ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) paths.add(Path.of(part));
        }
        return List.copyOf(paths);
    }

    /**
     * Reads an integer parameter, returning zero when absent.
     *
     * @param id parameter identifier
     * @return integer value
     */
    public int integer(String id) {
        Object value = parameters.get(id);
        if (value == null || String.valueOf(value).isBlank()) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value).trim());
    }

    /**
     * Reads a boolean parameter.
     *
     * @param id parameter identifier
     * @return boolean value
     */
    public boolean bool(String id) {
        Object value = parameters.get(id);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * Returns a copy with one parameter replaced.
     *
     * @param id parameter identifier
     * @param value new value
     * @return modified request
     */
    public ToolRequest with(String id, Object value) {
        Map<String, Object> next = new LinkedHashMap<>(parameters);
        next.put(id, value);
        return new ToolRequest(toolId, next);
    }
}
