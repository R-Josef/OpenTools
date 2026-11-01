package io.opentools.visualization.export;

import java.nio.file.Path;

public record PlotExportResult(PlotFormat format, Path path, String mediaType, long bytes) {
}
