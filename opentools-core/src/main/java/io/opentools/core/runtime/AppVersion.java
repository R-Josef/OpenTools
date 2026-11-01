package io.opentools.core.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public final class AppVersion {
    private static final String RESOURCE = "/io/opentools/core/runtime/version.properties";
    private static final String DEVELOPMENT = "development";
    private static final String CURRENT = load();

    private AppVersion() {
    }

    public static String current() {
        return CURRENT;
    }

    private static String load() {
        try (InputStream stream = AppVersion.class.getResourceAsStream(RESOURCE)) {
            if (stream != null) {
                Properties properties = new Properties();
                properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                String version = properties.getProperty("version");
                if (isResolved(version)) {
                    return version.trim();
                }
            }
        } catch (IOException ignored) {
            // Fall through to JAR metadata or the development marker.
        }

        Package applicationPackage = AppVersion.class.getPackage();
        String manifestVersion = applicationPackage == null
                ? null
                : applicationPackage.getImplementationVersion();
        return isResolved(manifestVersion) ? manifestVersion.trim() : DEVELOPMENT;
    }

    private static boolean isResolved(String version) {
        return version != null && !version.isBlank() && !version.contains("${");
    }
}
