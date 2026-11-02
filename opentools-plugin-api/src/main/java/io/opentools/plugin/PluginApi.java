package io.opentools.plugin;

import java.util.List;

/**
 * Constants and compatibility rules for the OpenTools plugin contract.
 */
public final class PluginApi {
    /**
     * Version implemented by this API artifact.
     */
    public static final String VERSION = "2.0";

    /**
     * Capability names accepted by the current host.
     */
    public static final List<String> KNOWN_CAPABILITIES = List.of("tool", "page", "data");

    private PluginApi() {
    }

    /**
     * Tests whether a plugin's requested API version is compatible with this host.
     *
     * <p>The major version must match and the requested minor version must not be
     * newer than the host version.</p>
     *
     * @param requestedVersion descriptor API version
     * @return whether the host can load the plugin API version
     */
    public static boolean isCompatible(String requestedVersion) {
        if (requestedVersion == null || requestedVersion.isBlank()) {
            return false;
        }
        String[] requested = requestedVersion.trim().split("\\.");
        String[] host = VERSION.split("\\.");
        try {
            int requestedMajor = Integer.parseInt(requested[0]);
            int hostMajor = Integer.parseInt(host[0]);
            int requestedMinor = requested.length > 1 ? Integer.parseInt(requested[1]) : 0;
            int hostMinor = Integer.parseInt(host[1]);
            return requestedMajor == hostMajor && requestedMinor <= hostMinor;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
