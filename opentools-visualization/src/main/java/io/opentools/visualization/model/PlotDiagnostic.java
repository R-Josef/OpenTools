package io.opentools.visualization.model;

public record PlotDiagnostic(
        String severity,
        String messageKey,
        String message,
        long line,
        int row,
        int column
) {
    public PlotDiagnostic {
        severity = severity == null || severity.isBlank() ? "WARNING" : severity;
        messageKey = messageKey == null ? "" : messageKey;
        message = message == null ? "" : message;
    }
}
