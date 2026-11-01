package io.opentools.api.tool;

/**
 * One parameter validation issue.
 *
 * @param severity issue severity
 * @param field parameter identifier
 * @param message user-facing diagnosis
 * @param suggestion optional corrective action
 */
public record ValidationIssue(
        Severity severity,
        String field,
        String message,
        String suggestion
) {
    /**
     * Validation severity.
     */
    public enum Severity {
        ERROR,
        WARNING
    }

    /**
     * Creates an error issue.
     *
     * @param field parameter identifier
     * @param message diagnosis
     * @param suggestion corrective action
     * @return error issue
     */
    public static ValidationIssue error(String field, String message, String suggestion) {
        return new ValidationIssue(Severity.ERROR, field, message, suggestion);
    }

    /**
     * Creates a warning issue.
     *
     * @param field parameter identifier
     * @param message diagnosis
     * @param suggestion corrective action
     * @return warning issue
     */
    public static ValidationIssue warning(String field, String message, String suggestion) {
        return new ValidationIssue(Severity.WARNING, field, message, suggestion);
    }
}
