package io.opentools.formats;

public record FormatDetection(FormatType type, String reason, long fileSize) {
}
