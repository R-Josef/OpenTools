package io.opentools.plugin.host;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JarPluginLocalizerTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesFullLanguageThenLanguageThenEnglishThenFallback() throws Exception {
        Path jar = resourceJar("layers.jar", Map.of(
                "en_US", """
                        label=English
                        english.only=English only
                        formatted=Read {0} rows
                        """,
                "fr", "label=French\n",
                "fr_CA", "label=French Canadian\n"
        ));
        AtomicReference<String> language = new AtomicReference<>("fr_CA");
        JarPluginLocalizer.LoadResult loaded = JarPluginLocalizer.load(jar, language::get);
        JarPluginLocalizer localizer = loaded.localizer();

        assertEquals("French Canadian", localizer.text("label", "fallback"));
        assertEquals("English only", localizer.text("english.only", "fallback"));
        assertEquals("Read 7 rows", localizer.text("formatted", "fallback", 7));
        assertEquals("fallback", localizer.text("missing", "fallback"));

        language.set("fr");
        assertEquals("French", localizer.text("label", "fallback"));
        localizer.close();
        assertEquals("fallback", localizer.text("label", "fallback"));
    }

    @Test
    void keepsIdenticalKeysIsolatedBetweenPluginJars() throws Exception {
        Path firstJar = resourceJar("first.jar", Map.of("en_US", "shared.key=first\n"));
        Path secondJar = resourceJar("second.jar", Map.of("en_US", "shared.key=second\n"));
        JarPluginLocalizer first = JarPluginLocalizer.load(firstJar, () -> "en_US").localizer();
        JarPluginLocalizer second = JarPluginLocalizer.load(secondJar, () -> "en_US").localizer();

        assertEquals("first", first.text("shared.key", "fallback"));
        assertEquals("second", second.text("shared.key", "fallback"));
    }

    @Test
    void reportsMalformedDuplicateAndOversizedResources() throws Exception {
        Path duplicate = resourceJar("duplicate.jar", Map.of(
                "en_US", "duplicate.key=one\nduplicate.key=two\n"
        ));
        Path malformed = resourceJar("malformed.jar", Map.of(
                "en_US", "broken=\\u12\n"
        ));
        Path oversized = resourceJar("oversized.jar", Map.of(
                "en_US", "value=" + "x".repeat(260 * 1024)
        ));

        assertTrue(diagnosticCodes(duplicate).contains("I18N_DUPLICATE_KEY"));
        assertTrue(diagnosticCodes(malformed).contains("I18N_MALFORMED_PROPERTIES"));
        assertTrue(diagnosticCodes(oversized).contains("I18N_RESOURCE_LIMIT"));
    }

    @Test
    void ignoresResourcesOutsideTheFixedJarDirectory() throws Exception {
        Path jar = tempDir.resolve("safe.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            write(zip, "outside.properties", "label=outside\n");
            write(zip, "META-INF/opentools/i18n/nested/en_US.properties", "label=nested\n");
        }
        JarPluginLocalizer.LoadResult loaded = JarPluginLocalizer.load(jar, () -> "en_US");

        assertEquals("fallback", loaded.localizer().text("label", "fallback"));
        assertTrue(loaded.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("I18N_RESOURCE_PATH")));
    }

    private java.util.List<String> diagnosticCodes(Path jar) throws Exception {
        return JarPluginLocalizer.load(jar, () -> "en_US").diagnostics().stream()
                .map(PluginDiagnostic::code)
                .toList();
    }

    private Path resourceJar(String name, Map<String, String> resources) throws Exception {
        Path jar = tempDir.resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, String> entry : new LinkedHashMap<>(resources).entrySet()) {
                write(
                        zip,
                        JarPluginLocalizer.RESOURCE_PREFIX + entry.getKey() + ".properties",
                        entry.getValue()
                );
            }
        }
        return jar;
    }

    private static void write(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
