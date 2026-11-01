package io.opentools.api.tool;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Convenience base class implementing common parameter validation.
 */
public abstract class AbstractOpenTool implements OpenTool {
    /**
     * Validates parameter presence, file paths, ranges, and numeric formats.
     *
     * @param request task parameters
     * @return accumulated validation issues
     */
    protected ValidationResult validateParameters(ToolRequest request) {
        List<ValidationIssue> issues = new ArrayList<>();
        for (ToolParameter parameter : descriptor().parameters()) {
            Object value = request.value(parameter.id());
            if (value == null || String.valueOf(value).isBlank()) {
                if (parameter.required()) {
                    issues.add(ValidationIssue.error(parameter.id(), parameter.title() + " is required.", "Choose or enter a value."));
                }
                continue;
            }

            if (parameter.type() == ParameterType.FILE) {
                try {
                    Path path = Path.of(String.valueOf(value));
                    if (!Files.isRegularFile(path)) {
                        issues.add(ValidationIssue.error(parameter.id(), "File does not exist: " + path, "Choose an existing readable file."));
                    } else if (!Files.isReadable(path)) {
                        issues.add(ValidationIssue.error(parameter.id(), "File is not readable: " + path, "Check file permissions."));
                    }
                } catch (InvalidPathException ex) {
                    issues.add(ValidationIssue.error(parameter.id(), "File path is invalid: " + value, "Choose a valid local file path."));
                }
            } else if (parameter.type() == ParameterType.DIRECTORY) {
                try {
                    Path path = Path.of(String.valueOf(value));
                    if (!Files.isDirectory(path)) {
                        issues.add(ValidationIssue.error(parameter.id(), "Directory does not exist: " + path, "Choose an existing directory."));
                    }
                } catch (InvalidPathException ex) {
                    issues.add(ValidationIssue.error(parameter.id(), "Directory path is invalid: " + value, "Choose a valid local directory."));
                }
            } else if (parameter.type() == ParameterType.INTEGER) {
                try {
                    int number = Integer.parseInt(String.valueOf(value).trim());
                    if (parameter.min() != null && number < parameter.min()) {
                        issues.add(ValidationIssue.error(parameter.id(), parameter.title() + " is below the minimum.", "Minimum: " + parameter.min().intValue()));
                    }
                    if (parameter.max() != null && number > parameter.max()) {
                        issues.add(ValidationIssue.error(parameter.id(), parameter.title() + " is above the maximum.", "Maximum: " + parameter.max().intValue()));
                    }
                } catch (NumberFormatException ex) {
                    issues.add(ValidationIssue.error(parameter.id(), parameter.title() + " must be an integer.", "Enter a whole number."));
                }
            } else if (parameter.type() == ParameterType.DECIMAL) {
                try {
                    double number = Double.parseDouble(String.valueOf(value).trim());
                    if (parameter.min() != null && number < parameter.min()) {
                        issues.add(ValidationIssue.error(parameter.id(), parameter.title() + " is below the minimum.", "Minimum: " + parameter.min()));
                    }
                    if (parameter.max() != null && number > parameter.max()) {
                        issues.add(ValidationIssue.error(parameter.id(), parameter.title() + " is above the maximum.", "Maximum: " + parameter.max()));
                    }
                } catch (NumberFormatException ex) {
                    issues.add(ValidationIssue.error(parameter.id(), parameter.title() + " must be a number.", "Enter a decimal number."));
                }
            }
        }
        return new ValidationResult(issues);
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        return validateParameters(request);
    }
}
