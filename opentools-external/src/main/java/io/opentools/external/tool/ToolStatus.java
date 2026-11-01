package io.opentools.external.tool;

import java.nio.file.Path;

public record ToolStatus(
        String id,
        String displayName,
        Path executable,
        String version,
        boolean available,
        String diagnostic
) {
}
