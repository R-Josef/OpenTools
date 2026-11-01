package io.opentools.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class UiScalingTest {
    @Test
    void supportsBoundedFontScalingOverride() {
        String previous = System.getProperty("opentools.ui.scale");
        try {
            System.setProperty("opentools.ui.scale", "1.5");
            assertEquals(1.5, UiScaling.factor());
            assertEquals(146, UiScaling.px(97));

            System.setProperty("opentools.ui.scale", "9");
            assertEquals(2.5, UiScaling.factor());

            System.setProperty("opentools.ui.scale", "invalid");
            assertEquals(1.0, UiScaling.factor());
        } finally {
            if (previous == null) {
                System.clearProperty("opentools.ui.scale");
            } else {
                System.setProperty("opentools.ui.scale", previous);
            }
        }
    }
}
