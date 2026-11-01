package io.opentools.external.tool;

import java.util.List;

public record ExecutableSpec(
        String id,
        String displayName,
        String environmentVariable,
        List<String> executableNames,
        List<String> versionArguments,
        List<String> relativeDirectories
) {
    public ExecutableSpec {
        executableNames = List.copyOf(executableNames);
        versionArguments = List.copyOf(versionArguments);
        relativeDirectories = relativeDirectories == null ? List.of() : List.copyOf(relativeDirectories);
    }
}
