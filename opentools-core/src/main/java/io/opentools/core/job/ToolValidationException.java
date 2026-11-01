package io.opentools.core.job;

import io.opentools.api.tool.ValidationResult;

public final class ToolValidationException extends RuntimeException {
    private final ValidationResult validationResult;

    public ToolValidationException(ValidationResult validationResult) {
        super("Tool input validation failed");
        this.validationResult = validationResult;
    }

    public ValidationResult validationResult() {
        return validationResult;
    }
}
