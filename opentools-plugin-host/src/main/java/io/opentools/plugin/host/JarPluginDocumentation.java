package io.opentools.plugin.host;

import io.opentools.api.tool.ToolDescriptor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class JarPluginDocumentation implements AutoCloseable {
    private static final int MAX_DOCUMENT_FILES = 512;
    private static final int MAX_FILE_BYTES = 256 * 1024;
    private static final int MAX_TOTAL_BYTES = 16 * 1024 * 1024;
    private static final String DEFAULT_LANGUAGE = "en_US";

    private final Supplier<String> languageSupplier;
    private volatile Map<String, String> documents;

    private JarPluginDocumentation(Supplier<String> languageSupplier) {
        this.languageSupplier = languageSupplier == null ? () -> DEFAULT_LANGUAGE : languageSupplier;
        this.documents = Map.of();
    }

    static LoadResult load(Path jarPath, Supplier<String> languageSupplier) throws IOException {
        JarPluginDocumentation provider = new JarPluginDocumentation(languageSupplier);
        List<PluginDiagnostic> diagnostics = new ArrayList<>();
        Map<String, String> loaded = new LinkedHashMap<>();
        int files = 0;
        int totalBytes = 0;
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || !name.startsWith(ToolDocumentationPaths.PLUGIN_PREFIX)) {
                    continue;
                }
                String relative = name.substring(ToolDocumentationPaths.PLUGIN_PREFIX.length());
                String normalized = normalizeEntry(relative);
                if (normalized == null) {
                    diagnostics.add(new PluginDiagnostic(
                            "DOC_RESOURCE_PATH",
                            PluginDiagnostic.Severity.WARNING,
                            "Ignored an invalid plugin documentation path.",
                            name
                    ));
                    continue;
                }
                files++;
                if (files > MAX_DOCUMENT_FILES) {
                    diagnostics.add(new PluginDiagnostic(
                            "DOC_RESOURCE_LIMIT",
                            PluginDiagnostic.Severity.WARNING,
                            "Ignored documentation after the " + MAX_DOCUMENT_FILES + "-file limit.",
                            ToolDocumentationPaths.PLUGIN_PREFIX
                    ));
                    break;
                }
                long declaredSize = entry.getSize();
                if (declaredSize < 0 || declaredSize > MAX_FILE_BYTES) {
                    diagnostics.add(new PluginDiagnostic(
                            "DOC_RESOURCE_LIMIT",
                            PluginDiagnostic.Severity.WARNING,
                            "Ignored a Markdown file larger than " + MAX_FILE_BYTES + " bytes.",
                            name
                    ));
                    continue;
                }
                byte[] bytes;
                try (var input = jar.getInputStream(entry)) {
                    bytes = input.readNBytes(MAX_FILE_BYTES + 1);
                }
                if (bytes.length > MAX_FILE_BYTES) {
                    diagnostics.add(new PluginDiagnostic(
                            "DOC_RESOURCE_LIMIT",
                            PluginDiagnostic.Severity.WARNING,
                            "Ignored a Markdown file larger than " + MAX_FILE_BYTES + " bytes.",
                            name
                    ));
                    continue;
                }
                totalBytes += bytes.length;
                if (totalBytes > MAX_TOTAL_BYTES) {
                    diagnostics.add(new PluginDiagnostic(
                            "DOC_RESOURCE_LIMIT",
                            PluginDiagnostic.Severity.WARNING,
                            "Ignored documentation after the " + MAX_TOTAL_BYTES + "-byte total limit.",
                            ToolDocumentationPaths.PLUGIN_PREFIX
                    ));
                    break;
                }
                String markdown;
                try {
                    markdown = StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
                } catch (CharacterCodingException ex) {
                    diagnostics.add(new PluginDiagnostic(
                            "DOC_INVALID_UTF8",
                            PluginDiagnostic.Severity.WARNING,
                            "Plugin documentation is not valid UTF-8.",
                            name
                    ));
                    continue;
                }
                if (loaded.putIfAbsent(normalized, markdown) != null) {
                    diagnostics.add(new PluginDiagnostic(
                            "DOC_DUPLICATE_PATH",
                            PluginDiagnostic.Severity.WARNING,
                            "Ignored a duplicate documentation resource.",
                            name
                    ));
                }
            }
        }
        provider.documents = Collections.unmodifiableMap(loaded);
        return new LoadResult(provider, List.copyOf(diagnostics));
    }

    Optional<String> find(ToolDescriptor tool) {
        Map<String, String> current = documents;
        if (current.isEmpty()) {
            return Optional.empty();
        }
        for (String language : lookupLanguages(languageSupplier.get())) {
            Optional<String> relative = ToolDocumentationPaths.pluginRelativePath(
                    tool.id()
            );
            if (relative.isEmpty()) {
                return Optional.empty();
            }
            String markdown = current.get(language + "/" + relative.get());
            if (markdown != null && !markdown.isBlank()) {
                return Optional.of(markdown);
            }
        }
        return Optional.empty();
    }

    @Override
    public void close() {
        documents = Map.of();
    }

    private static String normalizeEntry(String relative) {
        String[] parts = relative.split("/");
        if (parts.length != 3 || !parts[1].equals("tool")
                || !parts[2].endsWith(".md") || parts[2].length() <= 3) {
            return null;
        }
        String language = ToolDocumentationPaths.normalizeLanguage(parts[0]);
        if (!parts[0].matches("[A-Za-z]{2,8}(?:_[A-Za-z0-9]{2,8})?")
                || !parts[2].matches("[A-Za-z0-9._-]+\\.md")) {
            return null;
        }
        return language + "/" + parts[1] + "/" + parts[2];
    }

    private static List<String> lookupLanguages(String active) {
        LinkedHashSet<String> languages = new LinkedHashSet<>();
        String full = ToolDocumentationPaths.normalizeLanguage(active);
        languages.add(full);
        int separator = full.indexOf('_');
        if (separator > 0) {
            languages.add(full.substring(0, separator));
        }
        languages.add(ToolDocumentationPaths.normalizeLanguage(DEFAULT_LANGUAGE));
        return List.copyOf(languages);
    }

    record LoadResult(JarPluginDocumentation provider, List<PluginDiagnostic> diagnostics) {
    }
}
