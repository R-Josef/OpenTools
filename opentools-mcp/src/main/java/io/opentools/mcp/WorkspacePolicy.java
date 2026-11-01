package io.opentools.mcp;

import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorkspacePolicy {
    private final Path projectRoot;
    private final List<Path> allowedRoots;

    public WorkspacePolicy(Path projectRoot, List<Path> configuredRoots) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        roots.add(canonicalBoundary(this.projectRoot));
        if (configuredRoots != null) {
            for (Path root : configuredRoots) {
                if (root != null) {
                    roots.add(canonicalBoundary(root.toAbsolutePath().normalize()));
                }
            }
        }
        this.allowedRoots = List.copyOf(roots);
    }

    public List<Path> allowedRoots() {
        return allowedRoots;
    }

    public NormalizedRequest normalize(ToolDescriptor descriptor, Map<String, Object> suppliedParameters) {
        Map<String, Object> effective = new LinkedHashMap<>();
        for (ToolParameter parameter : descriptor.parameters()) {
            if (parameter.defaultValue() != null) {
                effective.put(parameter.id(), parameter.defaultValue());
            }
        }
        if (suppliedParameters != null) {
            effective.putAll(suppliedParameters);
        }

        List<AutomationIssue> issues = new ArrayList<>();
        Set<String> known = descriptor.parameters().stream().map(ToolParameter::id).collect(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new)
        );
        for (String key : suppliedParameters == null ? Set.<String>of() : suppliedParameters.keySet()) {
            if (!known.contains(key)) {
                issues.add(new AutomationIssue(
                        "UNKNOWN_PARAMETER",
                        key,
                        "ERROR",
                        "Unknown parameter: " + key,
                        "Call describe_tool and use only declared parameters."
                ));
            }
        }
        Object overwrite = effective.get("overwrite");
        if (overwrite != null && truthy(overwrite)) {
            issues.add(new AutomationIssue(
                    "OVERWRITE_NOT_ALLOWED",
                    "overwrite",
                    "ERROR",
                    "Automation calls cannot enable overwrite.",
                    "Choose a new output path; overwrite remains disabled for MCP/CLI automation."
            ));
        }

        for (ToolParameter parameter : descriptor.parameters()) {
            Object value = effective.get(parameter.id());
            if (value == null || String.valueOf(value).isBlank()) {
                continue;
            }
            if (parameter.type() == ParameterType.FILE
                    || parameter.type() == ParameterType.OUTPUT_FILE
                    || parameter.type() == ParameterType.DIRECTORY) {
                try {
                    Path path = resolvePath(String.valueOf(value));
                    if (!isAllowed(path)) {
                        issues.add(outsideWorkspace(parameter.id(), path));
                    }
                    effective.put(parameter.id(), path.toString());
                } catch (InvalidPathException ex) {
                    issues.add(new AutomationIssue(
                            "INVALID_PATH",
                            parameter.id(),
                            "ERROR",
                            "Invalid path: " + value,
                            "Use an absolute path or a path relative to the project."
                    ));
                }
            } else if (parameter.type() == ParameterType.TEXT && parameter.id().equals("inputs")) {
                effective.put(parameter.id(), normalizePathList(parameter.id(), String.valueOf(value), issues));
            }
        }
        return new NormalizedRequest(new ToolRequest(descriptor.id(), effective), List.copyOf(issues));
    }

    public boolean isAllowed(Path path) {
        Path candidate = canonicalCandidate(path.toAbsolutePath().normalize());
        return allowedRoots.stream().anyMatch(candidate::startsWith);
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public Path resolvePath(String value) {
        Path path = Path.of(value);
        if (!path.isAbsolute()) {
            path = projectRoot.resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    private String normalizePathList(String field, String text, List<AutomationIssue> issues) {
        List<String> normalized = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String value = line.trim();
            if (value.isEmpty() || value.startsWith("#")) {
                normalized.add(line);
                continue;
            }
            try {
                Path path = resolvePath(value);
                if (!isAllowed(path)) {
                    issues.add(outsideWorkspace(field, path));
                }
                normalized.add(path.toString());
            } catch (InvalidPathException ex) {
                issues.add(new AutomationIssue(
                        "INVALID_PATH",
                        field,
                        "ERROR",
                        "Invalid path: " + value,
                        "Use paths inside the allowed workspace."
                ));
                normalized.add(line);
            }
        }
        return String.join(System.lineSeparator(), normalized);
    }

    private AutomationIssue outsideWorkspace(String field, Path path) {
        return new AutomationIssue(
                "PATH_OUTSIDE_WORKSPACE",
                field,
                "ERROR",
                "Path is outside the allowed workspace: " + path,
                "Use a path under " + allowedRoots + " or start OpenTools with an additional --workspace."
        );
    }

    private static Path canonicalBoundary(Path path) {
        try {
            return Files.exists(path) ? path.toRealPath() : path;
        } catch (IOException ex) {
            return path;
        }
    }

    private static Path canonicalCandidate(Path path) {
        if (Files.exists(path)) {
            try {
                return path.toRealPath();
            } catch (IOException ignored) {
                return path;
            }
        }
        Path ancestor = path;
        Deque<String> segments = new java.util.ArrayDeque<>();
        while (ancestor != null && !Files.exists(ancestor)) {
            Path name = ancestor.getFileName();
            if (name != null) {
                segments.push(name.toString());
            }
            ancestor = ancestor.getParent();
        }
        if (ancestor == null) {
            return path;
        }
        try {
            Path resolved = ancestor.toRealPath();
            while (!segments.isEmpty()) {
                resolved = resolved.resolve(segments.pop());
            }
            return resolved.normalize();
        } catch (IOException ex) {
            return path;
        }
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public record NormalizedRequest(ToolRequest request, List<AutomationIssue> issues) {
        public boolean valid() {
            return issues.stream().noneMatch(AutomationIssue::error);
        }
    }
}
