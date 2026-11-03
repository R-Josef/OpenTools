package io.opentools.core.i18n;

import io.opentools.api.tool.ValidationIssue;

import java.util.Map;

/**
 * Localizes the messages produced by {@code AbstractOpenTool.validateParameters}.
 *
 * <p>Tool-specific validation remains free text; unknown messages are returned
 * unchanged so no diagnostic is lost.</p>
 */
public final class ValidationText {
    private ValidationText() {
    }

    public static ValidationIssue localize(
            LanguageManager language,
            Map<String, String> titles,
            ValidationIssue issue
    ) {
        if (language == null || issue == null) {
            return issue;
        }
        String message = issue.message() == null ? "" : issue.message();
        String suggestion = issue.suggestion() == null ? "" : issue.suggestion();
        String title = titles == null ? "" : titles.getOrDefault(issue.field(), "");
        String localizedMessage = message;
        String localizedSuggestion = suggestion;

        if (message.endsWith(" is required.")) {
            localizedMessage = language.text("validation.required",
                    "{0} is required.", title.isBlank() ? strip(message, " is required.") : title);
            localizedSuggestion = language.text("validation.suggestion.value", "Choose or enter a value.");
        } else if (message.startsWith("File does not exist: ")) {
            localizedMessage = language.text("validation.file_missing",
                    "File does not exist: {0}", message.substring("File does not exist: ".length()));
            localizedSuggestion = language.text("validation.suggestion.file",
                    "Choose an existing readable file.");
        } else if (message.startsWith("File is not readable: ")) {
            localizedMessage = language.text("validation.file_unreadable",
                    "File is not readable: {0}", message.substring("File is not readable: ".length()));
            localizedSuggestion = language.text("validation.suggestion.permissions", "Check file permissions.");
        } else if (message.startsWith("File path is invalid: ")) {
            localizedMessage = language.text("validation.file_invalid",
                    "File path is invalid: {0}", message.substring("File path is invalid: ".length()));
            localizedSuggestion = language.text("validation.suggestion.file_path", "Choose a valid local file path.");
        } else if (message.startsWith("Directory does not exist: ")) {
            localizedMessage = language.text("validation.directory_missing",
                    "Directory does not exist: {0}", message.substring("Directory does not exist: ".length()));
            localizedSuggestion = language.text("validation.suggestion.directory", "Choose an existing directory.");
        } else if (message.startsWith("Directory path is invalid: ")) {
            localizedMessage = language.text("validation.directory_invalid",
                    "Directory path is invalid: {0}", message.substring("Directory path is invalid: ".length()));
            localizedSuggestion = language.text("validation.suggestion.directory_path", "Choose a valid local directory.");
        } else if (message.endsWith(" is below the minimum.")) {
            localizedMessage = language.text("validation.below_minimum",
                    "{0} is below the minimum.", title.isBlank() ? strip(message, " is below the minimum.") : title);
            localizedSuggestion = suggestion.startsWith("Minimum: ")
                    ? language.text("validation.suggestion.minimum", "Minimum: {0}", suggestion.substring(9))
                    : suggestion;
        } else if (message.endsWith(" is above the maximum.")) {
            localizedMessage = language.text("validation.above_maximum",
                    "{0} is above the maximum.", title.isBlank() ? strip(message, " is above the maximum.") : title);
            localizedSuggestion = suggestion.startsWith("Maximum: ")
                    ? language.text("validation.suggestion.maximum", "Maximum: {0}", suggestion.substring(9))
                    : suggestion;
        } else if (message.endsWith(" must be an integer.")) {
            localizedMessage = language.text("validation.integer",
                    "{0} must be an integer.", title.isBlank() ? strip(message, " must be an integer.") : title);
            localizedSuggestion = language.text("validation.suggestion.integer", "Enter a whole number.");
        } else if (message.endsWith(" must be a number.")) {
            localizedMessage = language.text("validation.number",
                    "{0} must be a number.", title.isBlank() ? strip(message, " must be a number.") : title);
            localizedSuggestion = language.text("validation.suggestion.number", "Enter a decimal number.");
        }
        return new ValidationIssue(issue.severity(), issue.field(), localizedMessage, localizedSuggestion);
    }

    private static String strip(String value, String suffix) {
        return value.substring(0, value.length() - suffix.length());
    }
}
