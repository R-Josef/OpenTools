package io.opentools.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JavawDoctorTest {
    @TempDir
    Path tempDir;

    @Test
    void startsDoctorWithoutConsoleAndReportsRuntimePaths() throws Exception {
        Assumptions.assumeTrue(
                System.getProperty("os.name").toLowerCase().contains("win"),
                "javaw is Windows-specific"
        );
        Path javaw = Path.of(System.getProperty("java.home"), "bin", "javaw.exe");
        Assumptions.assumeTrue(Files.isRegularFile(javaw));
        String classPath = System.getProperty(
                "surefire.test.class.path",
                System.getProperty("java.class.path")
        );

        Path appHome = Files.createDirectories(tempDir.resolve("apphome"));
        Path userHome = Files.createDirectories(tempDir.resolve("home"));
        Path stdout = tempDir.resolve("doctor.stdout.json");
        Path stderr = tempDir.resolve("doctor.stderr.txt");
        Process process = new ProcessBuilder(
                javaw.toString(),
                "-Duser.home=" + userHome,
                "-Dopentools.home=" + appHome,
                "-cp",
                classPath,
                OpenToolsLauncher.class.getName(),
                "doctor",
                "--json"
        )
                .directory(tempDir.toFile())
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();

        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        assertTrue(finished, "javaw doctor did not finish");
        String errorOutput = new String(Files.readAllBytes(stderr), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), errorOutput);

        JsonNode report = new ObjectMapper()
                .readTree(new String(Files.readAllBytes(stdout), StandardCharsets.UTF_8))
                .path("data");
        assertEquals(
                appHome.toAbsolutePath().normalize().toString(),
                report.path("applicationHome").asText()
        );
        assertEquals(
                tempDir.toAbsolutePath().normalize().toString(),
                report.path("userDir").asText()
        );
        assertTrue(report.path("processPath").isTextual());
    }
}
