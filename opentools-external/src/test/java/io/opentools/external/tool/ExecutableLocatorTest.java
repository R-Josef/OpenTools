package io.opentools.external.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExecutableLocatorTest {
    @TempDir
    Path tempDir;

    @Test
    void locatesExplicitExecutableAndReadsVersion() {
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
        ExecutableSpec spec = new ExecutableSpec(
                "java",
                "Java",
                "OPENTOOLS_JAVA",
                List.of("java"),
                List.of("-version"),
                List.of()
        );

        ToolStatus status = new ExecutableLocator(tempDir, Map.of(), new io.opentools.external.process.ProcessRunner())
                .resolve(spec, null, java);

        assertTrue(status.available());
        assertEquals(java.toAbsolutePath().normalize(), status.executable());
        assertTrue(status.version().contains("version"));
    }

    @Test
    void readsWindowsPathEnvironmentCaseInsensitively() {
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
        String key = System.getProperty("os.name").toLowerCase().contains("win") ? "Path" : "PATH";
        ExecutableSpec spec = new ExecutableSpec(
                "java",
                "Java",
                "OPENTOOLS_JAVA",
                List.of("java"),
                List.of("-version"),
                List.of()
        );

        ToolStatus status = new ExecutableLocator(
                tempDir,
                Map.of(key, java.getParent().toString()),
                new io.opentools.external.process.ProcessRunner()
        ).resolve(spec, null, null);

        assertTrue(status.available());
    }

    @Test
    void packagedToolsWinOverExplicitEnvironmentPathAndFallback() throws Exception {
        Path packaged = createProbe(tempDir.resolve("tools/probe"));
        Path explicit = createProbe(tempDir.resolve("explicit"));
        Path environment = createProbe(tempDir.resolve("environment"));
        Path path = createProbe(tempDir.resolve("path"));
        Path fallback = createProbe(tempDir.resolve("fallback"));
        ExecutableSpec spec = testSpec(List.of("tools/probe"));

        ToolStatus status = new ExecutableLocator(
                tempDir,
                Map.of(
                        "OPENTOOLS_LOCATOR_TEST", environment.toString(),
                        "PATH", path.getParent().toString()
                ),
                new io.opentools.external.process.ProcessRunner(),
                List.of(fallback.getParent())
        ).resolve(spec, explicit.getParent(), explicit);

        assertEquals(packaged.toAbsolutePath().normalize(), status.executable());
    }

    @Test
    void explicitPathWinsOverEnvironmentPathAndFallback() throws Exception {
        Path explicit = createProbe(tempDir.resolve("explicit"));
        Path environment = createProbe(tempDir.resolve("environment"));
        Path path = createProbe(tempDir.resolve("path"));
        Path fallback = createProbe(tempDir.resolve("fallback"));

        ToolStatus status = new ExecutableLocator(
                tempDir,
                Map.of(
                        "OPENTOOLS_LOCATOR_TEST", environment.toString(),
                        "PATH", path.getParent().toString()
                ),
                new io.opentools.external.process.ProcessRunner(),
                List.of(fallback.getParent())
        ).resolve(testSpec(List.of()), explicit.getParent(), explicit);

        assertEquals(explicit.toAbsolutePath().normalize(), status.executable());
    }

    @Test
    void environmentDirectoryWinsOverPathAndFallback() throws Exception {
        Path environment = createProbe(tempDir.resolve("environment"));
        Path path = createProbe(tempDir.resolve("path"));
        Path fallback = createProbe(tempDir.resolve("fallback"));

        ToolStatus status = new ExecutableLocator(
                tempDir,
                Map.of(
                        "OPENTOOLS_LOCATOR_TEST", environment.getParent().toString(),
                        "PATH", path.getParent().toString()
                ),
                new io.opentools.external.process.ProcessRunner(),
                List.of(fallback.getParent())
        ).resolve(testSpec(List.of()), null, null);

        assertEquals(environment.toAbsolutePath().normalize(), status.executable());
    }

    @Test
    void pathWinsOverWindowsDevelopmentFallback() throws Exception {
        Path path = createProbe(tempDir.resolve("path"));
        Path fallback = createProbe(tempDir.resolve("fallback"));

        ToolStatus status = new ExecutableLocator(
                tempDir,
                Map.of("PATH", path.getParent().toString()),
                new io.opentools.external.process.ProcessRunner(),
                List.of(fallback.getParent())
        ).resolve(testSpec(List.of()), null, null);

        assertEquals(path.toAbsolutePath().normalize(), status.executable());
    }

    @Test
    void stalePathFallsBackToWindowsDevelopmentDirectoryWithWarning() throws Exception {
        Path fallback = createProbe(tempDir.resolve("fallback"));

        ToolStatus status = new ExecutableLocator(
                tempDir,
                Map.of("PATH", ""),
                new io.opentools.external.process.ProcessRunner(),
                List.of(fallback.getParent())
        ).resolve(testSpec(List.of()), null, null);

        assertEquals(fallback.toAbsolutePath().normalize(), status.executable());
        assertTrue(status.available());
        assertTrue(status.diagnostic().contains("Windows development fallback"));
        assertTrue(status.diagnostic().contains("restart Explorer"));
    }

    private Path createProbe(Path directory) throws Exception {
        Files.createDirectories(directory);
        String name = System.getProperty("os.name").toLowerCase().contains("win")
                ? "locator-probe.exe"
                : "locator-probe";
        return Files.createFile(directory.resolve(name)).toAbsolutePath().normalize();
    }

    private static ExecutableSpec testSpec(List<String> relativeDirectories) {
        String name = System.getProperty("os.name").toLowerCase().contains("win")
                ? "locator-probe.exe"
                : "locator-probe";
        return new ExecutableSpec(
                "locator.test",
                "Locator test",
                "OPENTOOLS_LOCATOR_TEST",
                List.of(name),
                List.of("--version"),
                relativeDirectories
        );
    }
}
