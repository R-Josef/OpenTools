package io.opentools.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReleaseManifestTest {
    @Test
    void windowsManifestPinsOfficialHttpsSourcesAndSha256Hashes() throws Exception {
        JsonNode manifest = new ObjectMapper().readTree(
                Files.readString(findProjectRoot().resolve("packaging/windows/tools.json"))
        );

        assertEquals(1, manifest.path("schemaVersion").asInt());
        assertEquals("windows-x64", manifest.path("release").asText());
        assertEquals("windows", manifest.path("platform").asText());
        assertEquals("x64", manifest.path("architecture").asText());
        assertTrue(manifest.path("tools").isArray());
        assertEquals(7, manifest.path("tools").size());
        assertTrue(manifest.path("runtimeSupport").path("archive").path("url").asText()
                .startsWith("https://"));
        assertTrue(manifest.path("runtimeSupport").path("archive").path("sha256").asText()
                .matches("[0-9A-F]{64}"));

        Set<String> ids = new HashSet<>();
        for (JsonNode tool : manifest.path("tools")) {
            assertTrue(ids.add(tool.path("id").asText()), "duplicate tool id");
            assertFalse(tool.path("version").asText().isBlank());
            assertFalse(tool.path("license").asText().isBlank());
            assertTrue(tool.path("licenseUrl").asText().startsWith("https://"));
            assertTrue(tool.path("sourceUrl").asText().startsWith("https://"));
            assertTrue(tool.path("archive").path("url").asText().startsWith("https://"));
            String hash = tool.path("archive").path("sha256").asText();
            assertTrue(hash.matches("[0-9A-F]{64}"), "invalid SHA-256 for " + tool.path("id").asText());
            assertTrue(tool.path("licenseFiles").isArray());
            assertFalse(tool.path("licenseFiles").isEmpty());
        }
        assertEquals(Set.of(
                "blast", "diamond", "hmmer", "muscle", "trimal", "iqtree", "mcscanx"
        ), ids);
    }

    @Test
    void windowsIconContainsRequiredLauncherSizes() throws Exception {
        byte[] icon = Files.readAllBytes(findProjectRoot().resolve("packaging/windows/opentools.ico"));

        assertTrue(icon.length > 6);
        assertEquals(0, Short.toUnsignedInt(ByteBufferLittleEndian.shortAt(icon, 0)));
        assertEquals(1, Short.toUnsignedInt(ByteBufferLittleEndian.shortAt(icon, 2)));
        assertEquals(9, Short.toUnsignedInt(ByteBufferLittleEndian.shortAt(icon, 4)));
        Set<Integer> sizes = new HashSet<>();
        for (int index = 0; index < 9; index++) {
            int width = Byte.toUnsignedInt(icon[6 + index * 16]);
            sizes.add(width == 0 ? 256 : width);
        }
        assertEquals(Set.of(16, 20, 24, 32, 40, 48, 64, 128, 256), sizes);
    }

    private static Path findProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("packaging/windows/tools.json"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not find project root");
    }

    private static final class ByteBufferLittleEndian {
        private ByteBufferLittleEndian() {
        }

        private static short shortAt(byte[] bytes, int offset) {
            return (short) (Byte.toUnsignedInt(bytes[offset])
                    | Byte.toUnsignedInt(bytes[offset + 1]) << 8);
        }
    }
}
