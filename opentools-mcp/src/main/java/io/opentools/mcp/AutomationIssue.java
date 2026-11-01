package io.opentools.mcp;

public record AutomationIssue(
        String code,
        String field,
        String severity,
        String message,
        String suggestion
) {
    public AutomationIssue {
        code = code == null ? "INVALID_INPUT" : code;
        field = field == null ? "" : field;
        severity = severity == null ? "ERROR" : severity;
        message = message == null ? "" : message;
        suggestion = suggestion == null ? "" : suggestion;
    }

    public boolean error() {
        return severity.equalsIgnoreCase("ERROR");
    }
}
