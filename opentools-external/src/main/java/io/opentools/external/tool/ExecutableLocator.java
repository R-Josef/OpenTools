package io.opentools.external.tool;

import io.opentools.external.process.ProcessResult;
import io.opentools.external.process.ProcessRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExecutableLocator {
    private final Path appHome;
    private final Map<String, String> environment;
    private final ProcessRunner processRunner;

    public ExecutableLocator(Path appHome) {
        this(appHome, System.getenv(), new ProcessRunner());
    }

    public ExecutableLocator(Path appHome, Map<String, String> environment, ProcessRunner processRunner) {
        this.appHome = appHome.toAbsolutePath().normalize();
        this.environment = Map.copyOf(environment);
        this.processRunner = processRunner;
    }

    public ToolStatus resolve(ExecutableSpec spec, Path explicitDirectory, Path explicitFile) {
        Path executable = findInPackagedDirectories(spec);
        if (executable == null && explicitFile != null && Files.isRegularFile(explicitFile)) {
            executable = explicitFile.toAbsolutePath().normalize();
        }
        if (executable == null && explicitDirectory != null) {
            executable = findInDirectory(explicitDirectory, spec.executableNames());
        }

        String configurationWarning = null;
        if (executable == null) {
            String configured = environmentValue(spec.environmentVariable());
            if (configured != null && !configured.isBlank()) {
                try {
                    Path path = configuredPath(configured);
                    if (Files.isRegularFile(path)) {
                        executable = path;
                    } else if (Files.isDirectory(path)) {
                        executable = findInDirectory(path, spec.executableNames());
                    } else {
                        configurationWarning = "Ignored " + spec.environmentVariable()
                                + " because its value is not an existing file or directory.";
                    }
                } catch (RuntimeException ex) {
                    configurationWarning = "Ignored invalid " + spec.environmentVariable()
                            + " value: " + ex.getMessage() + ".";
                }
            }
        }
        if (executable == null) {
            executable = findOnPath(spec.executableNames());
        }

        if (executable == null) {
            String diagnostic = "Not found. Set " + spec.environmentVariable()
                    + ", choose the tool directory, or add it to PATH.";
            if (configurationWarning != null) {
                diagnostic = configurationWarning + " " + diagnostic;
            }
            return new ToolStatus(spec.id(), spec.displayName(), null, null, false, diagnostic);
        }

        String discoveryWarning = configurationWarning;
        try {
            List<String> command = new ArrayList<>();
            command.add(executable.toString());
            command.addAll(spec.versionArguments());
            ProcessResult result = processRunner.run(
                    command,
                    appHome,
                    Map.of(),
                    Duration.ofSeconds(10),
                    new io.opentools.api.tool.CancellationToken(),
                    ignored -> { }
            );
            if (!result.succeeded()) {
                return new ToolStatus(spec.id(), spec.displayName(), executable, null, true,
                        joinDiagnostics(
                                discoveryWarning,
                                "Executable found, but version command exited with code "
                                        + result.exitCode() + "."
                        ));
            }
            String version = firstNonBlankLine(result.output());
            return new ToolStatus(spec.id(), spec.displayName(), executable, version, true,
                    version == null
                            ? joinDiagnostics(discoveryWarning, "Executable found; version output was empty.")
                            : discoveryWarning == null ? "" : discoveryWarning);
        } catch (Exception ex) {
            return new ToolStatus(spec.id(), spec.displayName(), executable, null, true,
                    joinDiagnostics(
                            discoveryWarning,
                            "Executable found, but version check failed: " + ex.getMessage()
                    ));
        }
    }

    public List<ToolStatus> diagnose(List<ExecutableSpec> specs) {
        return specs.stream().map(spec -> resolve(spec, null, null)).toList();
    }

    private Path findInPackagedDirectories(ExecutableSpec spec) {
        List<Path> candidateDirectories = new ArrayList<>();
        spec.relativeDirectories().forEach(relative -> candidateDirectories.add(appHome.resolve(relative)));
        candidateDirectories.add(appHome.resolve("bin"));
        candidateDirectories.add(appHome.resolve("tools"));
        candidateDirectories.add(appHome.resolve("tools/blast/bin"));
        candidateDirectories.add(appHome.resolve("tools/diamond"));
        for (Path directory : candidateDirectories) {
            Path executable = findInDirectory(directory, spec.executableNames());
            if (executable != null) {
                return executable;
            }
        }
        return null;
    }

    private Path findInDirectory(Path directory, List<String> names) {
        if (directory == null || !Files.isDirectory(directory)) {
            return null;
        }
        for (String name : names) {
            for (String candidateName : candidateNames(name)) {
                Path candidate = directory.resolve(candidateName);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().normalize();
                }
            }
        }
        return null;
    }

    private Path findOnPath(List<String> names) {
        String path = environmentValue("PATH");
        if (path == null) {
            path = "";
        }
        String[] directories = path.split(java.io.File.pathSeparator);
        for (String directory : directories) {
            if (directory.isBlank()) {
                continue;
            }
            Path pathEntry;
            try {
                pathEntry = unquotePath(directory);
            } catch (RuntimeException ignored) {
                continue;
            }
            Path found = findInDirectory(pathEntry, names);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<String> candidateNames(String name) {
        if (!isWindows() || name.toLowerCase(Locale.ROOT).endsWith(".exe")) {
            return List.of(name);
        }
        return List.of(name + ".exe", name);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static Path configuredPath(String configured) {
        String value = configured.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    private static Path unquotePath(String value) {
        String entry = value.trim();
        if (entry.length() >= 2 && entry.startsWith("\"") && entry.endsWith("\"")) {
            entry = entry.substring(1, entry.length() - 1);
        }
        return Path.of(entry);
    }

    private String environmentValue(String name) {
        if (environment.containsKey(name)) {
            return environment.get(name);
        }
        if (isWindows()) {
            for (Map.Entry<String, String> entry : environment.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static String joinDiagnostics(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + " " + second;
    }

    private static String firstNonBlankLine(String output) {
        if (output == null) {
            return null;
        }
        return output.lines().map(String::trim).filter(line -> !line.isBlank()).findFirst().orElse(null);
    }
}
