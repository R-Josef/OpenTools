package io.opentools.visualization.render;

import java.awt.Color;
import java.util.List;
import java.util.Locale;

public final class PlotColors {
    private PlotColors() {
    }

    public static Color parse(String value) {
        String text = value == null ? "#000000" : value.trim();
        if (!text.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("Expected #RRGGBB color: " + value);
        }
        return new Color(
                Integer.parseInt(text.substring(1, 3), 16),
                Integer.parseInt(text.substring(3, 5), 16),
                Integer.parseInt(text.substring(5, 7), 16)
        );
    }

    public static String toHex(Color color) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    public static String sample(List<String> palette, double fraction) {
        if (palette == null || palette.isEmpty()) {
            return "#FFFFFF";
        }
        double value = Math.max(0, Math.min(1, fraction)) * (palette.size() - 1);
        int lower = (int) Math.floor(value);
        int upper = Math.min(palette.size() - 1, lower + 1);
        return mix(palette.get(lower), palette.get(upper), value - lower);
    }

    public static String mix(String first, String second, double fraction) {
        Color a = parse(first);
        Color b = parse(second);
        double amount = Math.max(0, Math.min(1, fraction));
        return toHex(new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * amount),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * amount),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * amount)
        ));
    }

    public static String contrast(String background) {
        Color color = parse(background);
        double luminance = (0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue()) / 255.0;
        return luminance < 0.55 ? "#FFFFFF" : "#111827";
    }
}
