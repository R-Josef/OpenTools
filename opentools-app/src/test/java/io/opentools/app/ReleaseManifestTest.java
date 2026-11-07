package io.opentools.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
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
        JsonNode runtimeSupport = manifest.path("runtimeSupport");
        assertEquals("system-prerequisite", runtimeSupport.path("deployment").asText());
        assertTrue(runtimeSupport.path("installer").path("url").asText()
                .startsWith("https://"));
        assertTrue(runtimeSupport.path("installer").path("sha256").asText()
                .matches("[0-9A-F]{64}"));
        assertTrue(runtimeSupport.path("archive").isMissingNode());

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
            for (JsonNode copiedFile : tool.path("copy")) {
                assertFalse(copiedFile.asText().endsWith("MSVCP140.dll"),
                        "tool-local Microsoft runtime DLL: " + tool.path("id").asText());
            }
            for (JsonNode requiredFile : tool.path("requiredFiles")) {
                assertFalse(requiredFile.asText().endsWith("MSVCP140.dll"),
                        "tool-local Microsoft runtime dependency: " + tool.path("id").asText());
            }
        }
        assertEquals(Set.of(
                "blast", "diamond", "hmmer", "muscle", "trimal", "iqtree", "mcscanx"
        ), ids);
    }

    @Test
    void sourceManifestPinsSharedCorrespondingSourceArchives() throws Exception {
        JsonNode manifest = new ObjectMapper().readTree(
                Files.readString(findProjectRoot().resolve("packaging/sources.json"))
        );

        assertEquals(1, manifest.path("schemaVersion").asInt());
        assertEquals("3.6.12",
                manifest.path("windowsBuildRuntime").path("cygwinVersion").asText());
        assertEquals("3.6.12-1",
                manifest.path("windowsBuildRuntime").path("packages").path("msys2-runtime").asText());
        assertEquals("16.2.0-1",
                manifest.path("windowsBuildRuntime").path("packages").path("gcc-libs").asText());

        Set<String> ids = new HashSet<>();
        for (JsonNode component : manifest.path("components")) {
            String id = component.path("id").asText();
            assertTrue(ids.add(id), "duplicate source component id");
            assertFalse(component.path("version").asText().isBlank());
            assertFalse(component.path("license").asText().isBlank());
            assertTrue(Set.of("required", "disclosure").contains(component.path("sourceUse").asText()));
            assertTrue(component.path("archive").path("url").asText().startsWith("https://"));
            assertTrue(component.path("archive").path("sha256").asText()
                    .matches("[0-9A-F]{64}"), "invalid source SHA-256 for " + id);
        }
        assertEquals(Set.of(
                "diamond", "muscle", "trimal", "iqtree", "meme",
                "cygwin", "msys2-runtime", "msys2-gcc"
        ), ids);
        assertTrue(Files.isRegularFile(
                findProjectRoot().resolve("scripts/package-sources.sh")
        ));
        String windowsPackager = Files.readString(
                findProjectRoot().resolve("scripts/package-windows.ps1")
        );
        assertFalse(windowsPackager.contains("foreach ($packageName"),
                "MSYS2 validation must not overwrite the release archive name");
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

    @Test
    void allPlatformManifestsPinSupportedArchitecturesAndTools() throws Exception {
        Map<String, String> manifests = Map.of(
                "packaging/windows/tools.json", "windows-x64",
                "packaging/linux/tools.json", "linux-x64",
                "packaging/macos-x64/tools.json", "macos-x64",
                "packaging/macos-arm64/tools.json", "macos-arm64"
        );
        Set<String> toolIds = Set.of(
                "blast", "diamond", "hmmer", "muscle", "trimal", "iqtree", "mcscanx"
        );

        for (var entry : manifests.entrySet()) {
            JsonNode manifest = new ObjectMapper().readTree(
                    Files.readString(findProjectRoot().resolve(entry.getKey()))
            );
            assertEquals(entry.getValue(), manifest.path("release").asText(), entry.getKey());
            assertEquals(1, manifest.path("schemaVersion").asInt(), entry.getKey());
            Set<String> ids = new HashSet<>();
            for (JsonNode tool : manifest.path("tools")) {
                ids.add(tool.path("id").asText());
                JsonNode archive = tool.path("archive");
                assertTrue(archive.path("url").asText().startsWith("https://"), entry.getKey());
                String sha256 = archive.path("sha256").asText();
                String md5 = archive.path("md5").asText();
                assertTrue(
                        sha256.matches("[0-9A-F]{64}") || md5.matches("[0-9A-F]{32}"),
                        "missing pinned checksum in " + entry.getKey()
                );
            }
            assertEquals(toolIds, ids, entry.getKey());
        }
        assertTrue(Files.isRegularFile(
                findProjectRoot().resolve(".github/workflows/release.yml")
        ));
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
