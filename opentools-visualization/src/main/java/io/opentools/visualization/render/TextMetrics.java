package io.opentools.visualization.render;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public final class TextMetrics {
    private static final BufferedImage CANVAS = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    private static final Graphics2D GRAPHICS = CANVAS.createGraphics();

    private TextMetrics() {
    }

    public static int width(String text, boolean bold, double fontSize) {
        FontMetrics metrics = GRAPHICS.getFontMetrics(new Font(
                "SansSerif",
                bold ? Font.BOLD : Font.PLAIN,
                Math.max(1, (int) Math.ceil(fontSize))
        ));
        return metrics.stringWidth(text == null ? "" : text);
    }

    public static List<String> wrap(String text, double maxWidth, double fontSize) {
        List<String> lines = new ArrayList<>();
        String value = text == null ? "" : text;
        if (value.isEmpty()) {
            lines.add("");
            return lines;
        }
        String[] words = value.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (width(candidate, false, fontSize) <= maxWidth || current.isEmpty()) {
                if (width(candidate, false, fontSize) <= maxWidth) {
                    current.setLength(0);
                    current.append(candidate);
                    continue;
                }
                for (char character : word.toCharArray()) {
                    String charCandidate = current + String.valueOf(character);
                    if (width(charCandidate, false, fontSize) > maxWidth && !current.isEmpty()) {
                        lines.add(current.toString());
                        current.setLength(0);
                    }
                    current.append(character);
                }
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines.isEmpty() ? List.of(value) : lines;
    }
}
