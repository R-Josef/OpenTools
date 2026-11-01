package io.opentools.ui;

import java.awt.Font;
import java.awt.GraphicsEnvironment;

final class UiScaling {
    private static final double MIN_SCALE = 0.75;
    private static final double MAX_SCALE = 2.5;

    private UiScaling() {
    }

    static double factor() {
        String configured = System.getProperty("opentools.ui.scale", "1.0");
        try {
            double value = Double.parseDouble(configured);
            return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
        } catch (NumberFormatException ex) {
            return 1.0;
        }
    }

    static int px(double value) {
        return Math.max(1, (int) Math.round(value * factor()));
    }

    static Font defaultFont() {
        String family = "Microsoft YaHei UI";
        if (GraphicsEnvironment.isHeadless()
                || !java.util.Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()
        ).contains(family)) {
            family = Font.SANS_SERIF;
        }
        return new Font(family, Font.PLAIN, px(13));
    }
}
