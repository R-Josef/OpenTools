package io.opentools.core.i18n;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;

public final class LanguageManager {
    public static final String DEFAULT_CODE = "en_US";

    private final Path languageDirectory;
    private final Path settingsFile;
    private volatile String activeCode;
    private volatile Properties active = new Properties();
    private volatile Properties fallback = new Properties();

    public LanguageManager(Path appHome) {
        this(appHome, Path.of(System.getProperty("user.home"), ".opentools"), null);
    }

    public LanguageManager(Path appHome, String forcedLanguage) {
        this(appHome, Path.of(System.getProperty("user.home"), ".opentools"), forcedLanguage);
    }

    public LanguageManager(Path appHome, Path userHome, String forcedLanguage) {
        this.languageDirectory = appHome.toAbsolutePath().normalize().resolve("lang");
        this.settingsFile = userHome.toAbsolutePath().normalize().resolve("settings.properties");
        String resolved = forcedLanguage;
        if (resolved == null || resolved.isBlank()) {
            resolved = readSavedLanguage();
        }
        if (resolved == null || resolved.isBlank()) {
            resolved = languageFor(Locale.getDefault());
        }
        reload(resolved);
    }

    public synchronized void setLanguage(String languageCode) throws IOException {
        String normalized = normalize(languageCode);
        Files.createDirectories(settingsFile.getParent());
        Properties settings = new Properties();
        if (Files.isRegularFile(settingsFile)) {
            try (Reader reader = Files.newBufferedReader(settingsFile, StandardCharsets.UTF_8)) {
                settings.load(reader);
            }
        }
        settings.setProperty("ui.language", normalized);
        try (var writer = Files.newBufferedWriter(settingsFile, StandardCharsets.UTF_8)) {
            settings.store(writer, "OpenTools user settings");
        }
        reload(normalized);
    }

    public synchronized void reload() {
        reload(activeCode);
    }

    public String text(String key) {
        return text(key, key);
    }

    public String text(String key, String defaultValue, Object... arguments) {
        String value = active.getProperty(key);
        if (value == null || value.isBlank()) {
            value = fallback.getProperty(key);
        }
        if (value == null || value.isBlank()) {
            value = defaultValue == null || defaultValue.isBlank() ? key : defaultValue;
        }
        return arguments == null || arguments.length == 0
                ? value
                : MessageFormat.format(value, arguments);
    }

    public String activeCode() {
        return activeCode;
    }

    public Path languageDirectory() {
        return languageDirectory;
    }

    public List<LanguageOption> availableLanguages() {
        List<LanguageOption> options = new ArrayList<>();
        options.add(option(DEFAULT_CODE, "English"));
        if (!Files.isDirectory(languageDirectory)) {
            return options;
        }
        try (Stream<Path> files = Files.list(languageDirectory)) {
            files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".properties"))
                    .map(name -> name.substring(0, name.length() - ".properties".length()))
                    .filter(code -> !code.equals(DEFAULT_CODE))
                    .sorted(Comparator.naturalOrder())
                    .forEach(code -> options.add(option(code, code)));
        } catch (IOException ignored) {
        }
        return List.copyOf(options);
    }

    public static String languageFor(Locale locale) {
        String language = locale == null ? "" : locale.getLanguage().toLowerCase(Locale.ROOT);
        if (language.startsWith("zh")) {
            return "zh_CN";
        }
        return DEFAULT_CODE;
    }

    private LanguageOption option(String code, String fallbackName) {
        Properties properties = load(code);
        String name = properties.getProperty("language.name", fallbackName);
        return new LanguageOption(code, name);
    }

    private synchronized void reload(String requestedCode) {
        String candidate = normalize(requestedCode);
        Properties english = load(DEFAULT_CODE);
        Properties requested = candidate.equals(DEFAULT_CODE) ? english : load(candidate);
        if (requested.isEmpty() && !candidate.equals(DEFAULT_CODE)) {
            candidate = DEFAULT_CODE;
            requested = english;
        }
        fallback = english;
        active = requested;
        activeCode = candidate;
    }

    private Properties load(String code) {
        Properties properties = new Properties();
        Path file = languageDirectory.resolve(normalize(code) + ".properties");
        if (!Files.isRegularFile(file)) {
            return properties;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException ignored) {
        }
        return properties;
    }

    private String readSavedLanguage() {
        if (!Files.isRegularFile(settingsFile)) {
            return null;
        }
        Properties settings = new Properties();
        try (Reader reader = Files.newBufferedReader(settingsFile, StandardCharsets.UTF_8)) {
            settings.load(reader);
            return settings.getProperty("ui.language");
        } catch (IOException ex) {
            return null;
        }
    }

    private static String normalize(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return DEFAULT_CODE;
        }
        String normalized = languageCode.trim().replace('-', '_');
        if (normalized.toLowerCase(Locale.ROOT).startsWith("zh")) {
            return "zh_CN";
        }
        if (normalized.toLowerCase(Locale.ROOT).startsWith("en")) {
            return DEFAULT_CODE;
        }
        return normalized;
    }

    public record LanguageOption(String code, String displayName) {
        @Override
        public String toString() {
            return displayName;
        }
    }
}
