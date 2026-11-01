package io.opentools.external.process;

import io.opentools.api.tool.OpenToolException;

public final class ExternalProcessException extends OpenToolException {
    public ExternalProcessException(String code, String message, String suggestion) {
        super(code, message, suggestion);
    }

    public ExternalProcessException(String code, String message, String suggestion, Throwable cause) {
        super(code, message, suggestion, cause);
    }
}
