package io.opentools.api.tool;

import java.util.List;

/**
 * Immutable collection of parameter validation issues.
 *
 * @param issues validation issues
 */
public record ValidationResult(List<ValidationIssue> issues) {
    public ValidationResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    /**
     * Returns a successful result without issues.
     *
     * @return valid result
     */
    public static ValidationResult ok() {
        return new ValidationResult(List.of());
    }

    /**
     * Creates a result from issues.
     *
     * @param issues validation issues
     * @return validation result
     */
    public static ValidationResult of(ValidationIssue... issues) {
        return new ValidationResult(List.of(issues));
    }

    /**
     * Returns whether no issue has error severity.
     *
     * @return {@code true} when the request is valid
     */
    public boolean valid() {
        return issues.stream().noneMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR);
    }

    /**
     * Returns whether at least one warning exists.
     *
     * @return whether warnings are present
     */
    public boolean hasWarnings() {
        return issues.stream().anyMatch(issue -> issue.severity() == ValidationIssue.Severity.WARNING);
    }

    /**
     * Combines this result with another result.
     *
     * @param other additional issues
     * @return merged result
     */
    public ValidationResult merge(ValidationResult other) {
        return new ValidationResult(java.util.stream.Stream.concat(issues.stream(), other.issues.stream()).toList());
    }
}
