package io.opentools.api.tool;

/**
 * Structured task failure with a stable code and optional recovery suggestion.
 */
public class OpenToolException extends RuntimeException {
    private final String code;
    private final String suggestion;

    /**
     * Creates a structured task exception.
     *
     * @param code stable error code
     * @param message user-facing message
     * @param suggestion optional corrective action
     */
    public OpenToolException(String code, String message, String suggestion) {
        super(message);
        this.code = code;
        this.suggestion = suggestion;
    }

    /**
     * Creates a structured task exception with a cause.
     *
     * @param code stable error code
     * @param message user-facing message
     * @param suggestion optional corrective action
     * @param cause underlying failure
     */
    public OpenToolException(String code, String message, String suggestion, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.suggestion = suggestion;
    }

    /**
     * Returns the stable error code.
     *
     * @return error code
     */
    public String code() {
        return code;
    }

    /**
     * Returns the optional recovery suggestion.
     *
     * @return suggestion or {@code null}
     */
    public String suggestion() {
        return suggestion;
    }
}
