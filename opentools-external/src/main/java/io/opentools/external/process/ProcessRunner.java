package io.opentools.external.process;

import io.opentools.api.tool.CancellationToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class ProcessRunner {
    private static final int MAX_CAPTURE_CHARS = 1_000_000;

    public ProcessResult run(
            List<String> command,
            Path workingDirectory,
            Map<String, String> environment,
            Duration timeout,
            CancellationToken cancellationToken,
            Consumer<String> logger
    ) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("External command is empty");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("External process timeout must be positive");
        }
        Consumer<String> safeLogger = logger == null ? ignored -> { } : logger;
        ProcessBuilder builder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }
        if (environment != null) {
            builder.environment().putAll(environment);
        }
        prependExecutableDirectoryToPath(builder, command.getFirst());
        builder.redirectErrorStream(true);

        safeLogger.accept("$ " + String.join(" ", command));
        Instant started = Instant.now();
        Process process;
        try {
            process = builder.start();
        } catch (IOException ex) {
            throw new ExternalProcessException(
                    "EXTERNAL_PROCESS_START_FAILED",
                    "Could not start external command: " + command.getFirst(),
                    "Check the executable path and permissions.",
                    ex
            );
        }

        StringBuilder capture = new StringBuilder();
        boolean[] truncated = {false};
        Thread readerThread = Thread.ofVirtual().name("opentools-process-output").start(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(),
                    StandardCharsets.UTF_8
            ))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    safeLogger.accept(line);
                    if (capture.length() < MAX_CAPTURE_CHARS) {
                        int remaining = MAX_CAPTURE_CHARS - capture.length();
                        capture.append(line, 0, Math.min(remaining, line.length()));
                        if (remaining > line.length()) {
                            capture.append('\n');
                        }
                    } else {
                        truncated[0] = true;
                    }
                }
            } catch (IOException ex) {
                safeLogger.accept("Output stream error: " + ex.getMessage());
            }
        });

        boolean cancelled = false;
        boolean timedOut = false;
        while (process.isAlive()) {
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                cancelled = true;
                terminate(process);
                break;
            }
            if (Duration.between(started, Instant.now()).compareTo(timeout) > 0) {
                timedOut = true;
                terminate(process);
                break;
            }
            try {
                process.waitFor(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                terminate(process);
                throw new ExternalProcessException(
                        "EXTERNAL_PROCESS_INTERRUPTED",
                        "External process execution was interrupted.",
                        "Retry the task.",
                        ex
                );
            }
        }
        try {
            process.waitFor(5, TimeUnit.SECONDS);
            readerThread.join(5_000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        Duration elapsed = Duration.between(started, Instant.now());
        if (cancelled) {
            throw new CancellationException("External process cancelled");
        }
        if (timedOut) {
            throw new ExternalProcessException(
                    "EXTERNAL_PROCESS_TIMEOUT",
                    "External process exceeded timeout of " + timeout.toMinutes() + " minutes.",
                    "Increase the timeout or reduce the input size."
            );
        }
        return new ProcessResult(process.exitValue(), capture.toString(), elapsed, truncated[0]);
    }

    private static void terminate(Process process) {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static void prependExecutableDirectoryToPath(ProcessBuilder builder, String executable) {
        try {
            Path path = Path.of(executable).toAbsolutePath().normalize();
            Path parent = path.getParent();
            if (parent == null || !Files.isDirectory(parent)) {
                return;
            }
            String existing = builder.environment().getOrDefault("PATH", "");
            Path runtimeHome = Path.of(System.getProperty("java.home"))
                    .toAbsolutePath().normalize();
            Path applicationClasspath = applicationClasspathEntry();
            List<String> retained = new ArrayList<>();
            for (String entry : existing.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
                if (entry.isBlank()) {
                    continue;
                }
                try {
                    Path candidate = Path.of(entry).toAbsolutePath().normalize();
                    if (candidate.startsWith(runtimeHome)
                            || (applicationClasspath != null && candidate.equals(applicationClasspath))) {
                        continue;
                    }
                } catch (RuntimeException ignored) {
                    // Keep non-path environment fragments unchanged.
                }
                retained.add(entry);
            }
            retained.removeIf(entry -> entry.equalsIgnoreCase(parent.toString()));
            retained.addFirst(parent.toString());
            builder.environment().put("PATH", String.join(java.io.File.pathSeparator, retained));
        } catch (RuntimeException ignored) {
            // PATH augmentation is best-effort; executable discovery already succeeded.
        }
    }

    private static Path applicationClasspathEntry() {
        String classPath = System.getProperty("java.class.path");
        if (classPath == null || classPath.isBlank()) {
            return null;
        }
        String first = classPath.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))[0];
        try {
            Path entry = Path.of(first).toAbsolutePath().normalize();
            return Files.isRegularFile(entry) ? entry.getParent() : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
