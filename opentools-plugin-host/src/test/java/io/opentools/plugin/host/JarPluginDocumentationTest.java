package io.opentools.plugin.host;

import io.opentools.api.tool.ToolDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JarPluginDocumentationTest {
    @TempDir
    Path tempDir;

    @Test
    void discoversByCategoryAndToolIdWithEnglishFallback() throws Exception {
        Path jar = tempDir.resolve("documented.jar");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
            write(
                    output,
                    "META-INF/opentools/docs/en_US/tool/example.tool.md",
                    "# English fallback\n"
            );
            write(
                    output,
                    "META-INF/opentools/docs/zh_CN/tool/nested/doc.md",
                    "# Nested path\n"
            );
        }
        JarPluginDocumentation.LoadResult loaded = JarPluginDocumentation.load(
                jar,
                () -> "zh_CN"
        );
        ToolDescriptor tool = new ToolDescriptor(
                "example.tool",
                "Example",
                "Example tool",
                "Plugin",
                List.of(),
                List.of(),
                List.of(),
                "1.0.0",
                List.of()
        );

        assertTrue(loaded.provider().find(tool).orElseThrow().contains("English fallback"));
        assertTrue(loaded.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("DOC_RESOURCE_PATH")));
        assertEquals(
                "tool/example.tool.md",
                ToolDocumentationPaths.hostRelativePath("example.tool")
                        .orElseThrow()
        );
    }

    private static void write(ZipOutputStream output, String name, String text) throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(text.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
