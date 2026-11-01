package io.opentools.mcp;

import java.util.List;

public final class AutomationException extends Exception {
    private final String code;
    private final String suggestion;
    private final List<AutomationIssue> issues;

    public AutomationException(String code, String message, String suggestion) {
        this(code, message, suggestion, List.of(), null);
    }

    public AutomationException(
            String code,
            String message,
            String suggestion,
            List<AutomationIssue> issues
    ) {
        this(code, message, suggestion, issues, null);
    }

    public AutomationException(
            String code,
            String message,
            String suggestion,
            List<AutomationIssue> issues,
            Throwable cause
    ) {
        super(message, cause);
        this.code = code;
        this.suggestion = suggestion == null ? "" : suggestion;
        this.issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public String code() {
        return code;
    }

    public String suggestion() {
        return suggestion;
    }

    public List<AutomationIssue> issues() {
        return issues;
    }
}
