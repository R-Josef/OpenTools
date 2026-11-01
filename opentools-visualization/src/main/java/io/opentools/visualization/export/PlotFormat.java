package io.opentools.visualization.export;

import java.util.Locale;

public enum PlotFormat {
    SVG("image/svg+xml"),
    PNG("image/png"),
    PDF("application/pdf");

    private final String mediaType;

    PlotFormat(String mediaType) {
        this.mediaType = mediaType;
    }

    public String mediaType() {
        return mediaType;
    }

    public String extension() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PlotFormat parse(String value) {
        try {
            return valueOf((value == null ? "" : value.trim()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported plot format: " + value, ex);
        }
    }
}
