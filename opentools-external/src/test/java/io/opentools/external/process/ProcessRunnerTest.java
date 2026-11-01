package io.opentools.external.process;

import io.opentools.api.tool.CancellationToken;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProcessRunnerTest {
    @Test
    void preservesArgumentsWithSpacesAndCapturesStderr() {
        List<String> logs = new ArrayList<>();
        ProcessResult result = new ProcessRunner().run(
                javaCommand("echo", "arg with spaces & literal"),
                Path.of("."),
                Map.of(),
                Duration.ofSeconds(10),
                new CancellationToken(),
                logs::add
        );

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("arg with spaces & literal"));
        assertTrue(result.output().contains("stderr-line"));
        assertTrue(logs.stream().anyMatch(line -> line.contains("arg with spaces & literal")));
    }

    @Test
    void timeoutTerminatesProcess() {
        ExternalProcessException error = assertThrows(ExternalProcessException.class, () ->
                new ProcessRunner().run(
                        javaCommand("sleep"),
                        Path.of("."),
                        Map.of(),
                        Duration.ofMillis(300),
                        new CancellationToken(),
                        ignored -> { }
                ));

        assertEquals("EXTERNAL_PROCESS_TIMEOUT", error.code());
    }

    @Test
    void cancellationTerminatesProcess() {
        CancellationToken token = new CancellationToken();
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(300);
                token.cancel();
            } catch (InterruptedException ignored) {
            }
        });

        assertThrows(CancellationException.class, () ->
                new ProcessRunner().run(
                        javaCommand("sleep"),
                        Path.of("."),
                        Map.of(),
                        Duration.ofSeconds(10),
                        token,
                        ignored -> { }
                ));
    }

    private static List<String> javaCommand(String... args) {
        List<String> command = new ArrayList<>();
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
        command.add(java.toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(ProcessTestApp.class.getName());
        command.addAll(List.of(args));
        return command;
    }
}
