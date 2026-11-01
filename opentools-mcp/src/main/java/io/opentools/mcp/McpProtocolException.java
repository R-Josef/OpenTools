package io.opentools.mcp;

public final class McpProtocolException extends Exception {
    private final int code;

    public McpProtocolException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
