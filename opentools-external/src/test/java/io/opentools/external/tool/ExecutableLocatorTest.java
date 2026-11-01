package io.opentools.external.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void packagedToolsWinOverExplicitEnvironmentAndPath() throws Exception {
        Path packaged = createProbe(tempDir.resolve("tools/probe"));
        Path explicit = createProbe(tempDir.resolve("explicit"));
        Path environment = createProbe(tempDir.resolve("environment"));
        Path path = createProbe(tempDir.resolve("path"));
        ExecutableSpec spec = testSpec(List.of("tools/probe"));

        ToolStatus status = new ExecutableLocator(
                tempDir,
                Map.of(
                        "OPENTOOLS_LOCATOR_TEST", environment.toString(),
                        "PATH", path.getParent().toString()
                ),
                new io.opentools.external.process.ProcessRunner()
        ).resolve(spec, explicit.getParent(), explicit);

        assertEquals(packaged.toAbsolutePath().normalize(), status.executable());
    }

    @Test
    void explicitPathWinsOverEnvironmentAndPath() throws Exception {
        Path explicit = createProbe(tempDir.resolve("explicit"));
        Path environment = createProbe(tempDir.resolve("environment"));
        Path path = createProbe(tempDir.resolve("path"));

        ToolStatus status = new ExecutableLocator(
                tempDir,
                Map.of(
                        "OPENTOOLS_LOCATOR_TEST", environment.toString(),
                        "PATH", path.getParent().toString()
                ),
                new io.opentools.external.process.ProcessRunner()
        ).resolve(testSpec(List.of()), explicit.getParent(), explicit);

        assertEquals(explicit.toAbsolutePath().normalize(), status.executable());
    }

    @Test
    void environmentDirectoryWinsOverPath() throws Exception {
        Path environment = createProbe(tempDir.resolve("environment"));
        Path path = createProbe(tempDir.resolve("path"));

        ToolStatus status = new ExecutableLocator(
                tempDir,
                Map.of(
                        "OPENTOOLS_LOCATOR_TEST", environment.getParent().toString(),
                        "PATH", path.getParent().toString()
                ),
                new io.opentools.external.process.ProcessRunner()
        ).resolve(testSpec(List.of()), null, null);

        assertEquals(environment.toAbsolutePath().normalize(), status.executable());
    }

    @Test
    void locatesToolFromPath() throws Exception {
        Path path = createProbe(tempDir.resolve("path"));

        ToolStatus status = new ExecutableLocator(
                tempDir,
                Map.of("PATH", path.getParent().toString()),
                new io.opentools.external.process.ProcessRunner()
        ).resolve(testSpec(List.of()), null, null);

        assertEquals(path.toAbsolutePath().normalize(), status.executable());
    }

    @Test
    void doesNotSearchUndocumentedDirectoriesWhenPathIsEmpty() throws Exception {
        createProbe(tempDir.resolve("external"));
        Path appHome = Files.createDirectories(tempDir.resolve("app"));

        ToolStatus status = new ExecutableLocator(
                appHome,
                Map.of("PATH", ""),
                new io.opentools.external.process.ProcessRunner()
        ).resolve(testSpec(List.of()), null, null);

        assertFalse(status.available());
        assertTrue(status.diagnostic().contains("add it to PATH"));
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
