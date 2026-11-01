package io.opentools.api.tool;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A file produced by a task.
 *
 * @param path artifact path
 * @param title display title
 * @param mediaType MIME type, using {@code application/octet-stream} when omitted
 */
public record OutputArtifact(Path path, String title, String mediaType) {
    public OutputArtifact {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(title, "title");
        mediaType = mediaType == null ? "application/octet-stream" : mediaType;
    }
}
