package io.opentools.ui;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class DocResources {
    private DocResources() {
    }

    static String docsLanguage(String activeLanguage) {
        return "zh_CN".equals(activeLanguage) ? "zh_CN" : "en_US";
    }

    static String read(String languageCode, String sourcePath) throws IOException {
        String language = normalizeLanguage(languageCode);
        String normalized = normalize(sourcePath);
        String resource = "/docs/" + language + "/" + normalized;
        try (InputStream stream = DocResources.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Bundled document not found: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static URL url(String languageCode, String sourcePath) {
        return DocResources.class.getResource(
                "/docs/" + normalizeLanguage(languageCode) + "/" + normalize(sourcePath)
        );
    }

    private static String normalizeLanguage(String languageCode) {
        String language = languageCode == null ? "" : languageCode.trim();
        if (!language.matches("^[A-Za-z0-9_-]+$")) {
            throw new IllegalArgumentException("Unsafe documentation language code: " + languageCode);
        }
        return language;
    }

    private static String normalize(String sourcePath) {
        String path = sourcePath == null ? "" : sourcePath.trim().replace('\\', '/');
        if (path.isBlank() || path.startsWith("/") || path.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Unsafe document resource path: " + sourcePath);
        }
        for (String segment : path.split("/")) {
            if (segment.equals("..")) {
                throw new IllegalArgumentException("Document path traversal is not allowed: " + sourcePath);
            }
        }
        return path;
    }
}
