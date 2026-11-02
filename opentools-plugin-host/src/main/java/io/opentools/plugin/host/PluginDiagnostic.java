package io.opentools.plugin.host;

import java.util.Objects;

/**
 * Structured diagnostic emitted while discovering or loading a plugin.
 *
 * @param code stable diagnostic code
 * @param severity severity level
 * @param message human-readable detail
 * @param resource related resource path, or an empty string
 */
public record PluginDiagnostic(
        String code,
        Severity severity,
        String message,
        String resource
) {
    public PluginDiagnostic {
        code = Objects.requireNonNull(code, "code");
        severity = Objects.requireNonNull(severity, "severity");
        message = message == null ? "" : message;
        resource = resource == null ? "" : resource;
    }

    @Override
    public String toString() {
        return resource.isBlank()
                ? "[" + code + "] " + message
                : "[" + code + "] " + resource + ": " + message;
    }

    /**
     * Diagnostic severity.
     */
    public enum Severity {
        WARNING,
        ERROR
    }
}
