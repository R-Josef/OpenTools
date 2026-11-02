package io.opentools.plugin.host;

import io.opentools.plugin.PluginLocalizer;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class JarPluginLocalizer implements PluginLocalizer, AutoCloseable {
    static final String RESOURCE_PREFIX = "META-INF/opentools/i18n/";
    private static final int MAX_LANGUAGE_FILES = 64;
    private static final int MAX_FILE_BYTES = 256 * 1024;
    private static final int MAX_TOTAL_BYTES = 1024 * 1024;
    private static final int MAX_KEYS_PER_FILE = 5000;
    private static final int MAX_KEY_CHARS = 256;
    private static final int MAX_VALUE_CHARS = 16 * 1024;
    private static final String DEFAULT_LANGUAGE = "en_US";

    private final Supplier<String> languageSupplier;
    private volatile Map<String, Map<String, String>> resources;

    private JarPluginLocalizer(Supplier<String> languageSupplier) {
        this.languageSupplier = languageSupplier == null ? () -> DEFAULT_LANGUAGE : languageSupplier;
        this.resources = Map.of();
    }

    static LoadResult load(Path jarPath, Supplier<String> languageSupplier) throws IOException {
        JarPluginLocalizer localizer = new JarPluginLocalizer(languageSupplier);
        List<PluginDiagnostic> diagnostics = new ArrayList<>();
        Map<String, Map<String, String>> loaded = new LinkedHashMap<>();
        int files = 0;
        int totalBytes = 0;
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || !name.startsWith(RESOURCE_PREFIX)) {
                    continue;
                }
                String language = languageFromEntry(name);
                if (language == null) {
                    diagnostics.add(new PluginDiagnostic(
                            "I18N_RESOURCE_PATH",
                            PluginDiagnostic.Severity.WARNING,
                            "Ignored an invalid plugin language resource path.",
                            name
                    ));
                    continue;
                }
                files++;
                if (files > MAX_LANGUAGE_FILES) {
                    diagnostics.add(new PluginDiagnostic(
                            "I18N_RESOURCE_LIMIT",
                            PluginDiagnostic.Severity.WARNING,
                            "Ignored language resources after the "
                                    + MAX_LANGUAGE_FILES + "-file limit.",
                            RESOURCE_PREFIX
                    ));
                    break;
                }
                long declaredSize = entry.getSize();
                if (declaredSize < 0 || declaredSize > MAX_FILE_BYTES) {
                    diagnostics.add(new PluginDiagnostic(
                            "I18N_RESOURCE_LIMIT",
                            PluginDiagnostic.Severity.WARNING,
                            "Ignored a language file larger than " + MAX_FILE_BYTES + " bytes.",
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
                            "I18N_RESOURCE_LIMIT",
                            PluginDiagnostic.Severity.WARNING,
                            "Ignored a language file larger than " + MAX_FILE_BYTES + " bytes.",
                            name
                    ));
                    continue;
                }
                totalBytes += bytes.length;
                if (totalBytes > MAX_TOTAL_BYTES) {
                    diagnostics.add(new PluginDiagnostic(
                            "I18N_RESOURCE_LIMIT",
                            PluginDiagnostic.Severity.WARNING,
                            "Ignored language resources after the "
                                    + MAX_TOTAL_BYTES + "-byte total limit.",
                            RESOURCE_PREFIX
                    ));
                    break;
                }
                String content;
                try {
                    content = StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
                } catch (CharacterCodingException ex) {
                    diagnostics.add(new PluginDiagnostic(
                            "I18N_INVALID_UTF8",
                            PluginDiagnostic.Severity.WARNING,
                            "Language resource is not valid UTF-8.",
                            name
                    ));
                    continue;
                }
                ParsedProperties parsed = parseProperties(content);
                if (!parsed.duplicateKeys().isEmpty()) {
                    diagnostics.add(new PluginDiagnostic(
                            "I18N_DUPLICATE_KEY",
                            PluginDiagnostic.Severity.WARNING,
                            "Duplicate keys: " + String.join(", ", parsed.duplicateKeys()),
                            name
                    ));
                    continue;
                }
                if (parsed.error() != null) {
                    diagnostics.add(new PluginDiagnostic(
                            "I18N_MALFORMED_PROPERTIES",
                            PluginDiagnostic.Severity.WARNING,
                            parsed.error(),
                            name
                    ));
                    continue;
                }
                try {
                    Properties properties = new Properties();
                    properties.load(new StringReader(content));
                    if (properties.size() > MAX_KEYS_PER_FILE) {
                        diagnostics.add(new PluginDiagnostic(
                                "I18N_RESOURCE_LIMIT",
                                PluginDiagnostic.Severity.WARNING,
                                "Language file exceeds the " + MAX_KEYS_PER_FILE + "-key limit.",
                                name
                        ));
                        continue;
                    }
                    Map<String, String> values = new LinkedHashMap<>();
                    boolean oversized = false;
                    for (String key : properties.stringPropertyNames()) {
                        String value = properties.getProperty(key);
                        if (key.length() > MAX_KEY_CHARS || value.length() > MAX_VALUE_CHARS) {
                            oversized = true;
                            break;
                        }
                        values.put(key, value);
                    }
                    if (oversized) {
                        diagnostics.add(new PluginDiagnostic(
                                "I18N_RESOURCE_LIMIT",
                                PluginDiagnostic.Severity.WARNING,
                                "Language file contains an oversized key or value.",
                                name
                        ));
                        continue;
                    }
                    Map<String, String> previous = loaded.putIfAbsent(
                            language,
                            Collections.unmodifiableMap(values)
                    );
                    if (previous != null) {
                        diagnostics.add(new PluginDiagnostic(
                                "I18N_DUPLICATE_LANGUAGE",
                                PluginDiagnostic.Severity.WARNING,
                                "Ignored a second JAR entry for language " + language + ".",
                                name
                        ));
                    }
                } catch (IllegalArgumentException | IOException ex) {
                    diagnostics.add(new PluginDiagnostic(
                            "I18N_MALFORMED_PROPERTIES",
                            PluginDiagnostic.Severity.WARNING,
                            ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                            name
                    ));
                }
            }
        }
        localizer.resources = Collections.unmodifiableMap(loaded);
        return new LoadResult(localizer, List.copyOf(diagnostics));
    }

    @Override
    public String languageCode() {
        return normalizeLanguage(languageSupplier.get());
    }

    @Override
    public String text(String key, String fallback, Object... arguments) {
        String fallbackText = fallback == null ? "" : fallback;
        if (key == null || key.isBlank()) {
            return format(fallbackText, arguments);
        }
        Map<String, Map<String, String>> current = resources;
        if (current.isEmpty()) {
            return format(fallbackText, arguments);
        }
        String value = null;
        for (String language : lookupLanguages(languageCode())) {
            Map<String, String> messages = current.get(language);
            if (messages != null) {
                String candidate = messages.get(key);
                if (candidate != null && !candidate.isBlank()) {
                    value = candidate;
                    break;
                }
            }
        }
        return format(value == null || value.isBlank() ? fallbackText : value, arguments);
    }

    @Override
    public void close() {
        resources = Map.of();
    }

    private static String format(String pattern, Object[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return pattern;
        }
        return MessageFormat.format(pattern, arguments);
    }

    private static List<String> lookupLanguages(String languageCode) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String normalized = normalizeLanguage(languageCode);
        result.add(normalized);
        int separator = normalized.indexOf('_');
        if (separator > 0) {
            result.add(normalized.substring(0, separator));
        }
        result.add(normalizeLanguage(DEFAULT_LANGUAGE));
        return List.copyOf(result);
    }

    private static String languageFromEntry(String name) {
        String fileName = name.substring(RESOURCE_PREFIX.length());
        if (fileName.contains("/") || !fileName.endsWith(".properties")) {
            return null;
        }
        String code = fileName.substring(0, fileName.length() - ".properties".length());
        if (!code.matches("[A-Za-z]{2,8}(?:_[A-Za-z0-9]{2,8})?")) {
            return null;
        }
        return normalizeLanguage(code);
    }

    private static String normalizeLanguage(String code) {
        if (code == null || code.isBlank()) {
            return DEFAULT_LANGUAGE.toLowerCase(Locale.ROOT);
        }
        return code.trim().replace('-', '_').toLowerCase(Locale.ROOT);
    }

    private static ParsedProperties parseProperties(String content) {
        List<String> logicalLines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : content.split("\\R", -1)) {
            if (current.isEmpty()) {
                current.append(line);
            } else {
                current.append(line.stripLeading());
            }
            if (isContinued(current)) {
                current.setLength(current.length() - 1);
            } else {
                logicalLines.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            logicalLines.add(current.toString());
        }

        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (String line : logicalLines) {
            String rawKey = rawKey(line);
            if (rawKey == null) {
                continue;
            }
            String key;
            try {
                Properties keyProperties = new Properties();
                keyProperties.load(new StringReader(rawKey + "=value"));
                key = keyProperties.stringPropertyNames().stream().findFirst().orElse("");
            } catch (IllegalArgumentException | IOException ex) {
                return new ParsedProperties(List.of(), ex.getMessage());
            }
            if (!seen.add(key)) {
                duplicates.add(key);
            }
        }
        return new ParsedProperties(List.copyOf(duplicates), null);
    }

    private static String rawKey(String line) {
        int index = 0;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
            index++;
        }
        if (index >= line.length() || line.charAt(index) == '#' || line.charAt(index) == '!') {
            return null;
        }
        boolean escaped = false;
        for (int cursor = index; cursor < line.length(); cursor++) {
            char value = line.charAt(cursor);
            if (!escaped && (value == '=' || value == ':' || Character.isWhitespace(value))) {
                return line.substring(index, cursor);
            }
            if (value == '\\') {
                escaped = !escaped;
            } else {
                escaped = false;
            }
        }
        return line.substring(index);
    }

    private static boolean isContinued(CharSequence line) {
        int backslashes = 0;
        for (int index = line.length() - 1; index >= 0 && line.charAt(index) == '\\'; index--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    record LoadResult(JarPluginLocalizer localizer, List<PluginDiagnostic> diagnostics) {
    }

    private record ParsedProperties(List<String> duplicateKeys, String error) {
    }
}
