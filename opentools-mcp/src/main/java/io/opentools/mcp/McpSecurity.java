package io.opentools.mcp;

import java.security.SecureRandom;
import java.util.Base64;

public final class McpSecurity {
    private static final SecureRandom RANDOM = new SecureRandom();

    private McpSecurity() {
    }

    public static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
