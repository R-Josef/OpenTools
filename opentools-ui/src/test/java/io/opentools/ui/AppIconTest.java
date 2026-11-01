package io.opentools.ui;

import org.junit.jupiter.api.Test;

import javax.swing.ImageIcon;
import java.awt.Image;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class AppIconTest {
    @Test
    void loadsAllWindowIconSizes() {
        List<Image> images = AppIcon.windowImages();

        assertEquals(9, images.size());
        assertEquals(
                List.of(16, 20, 24, 32, 40, 48, 64, 128, 256),
                images.stream().map(image -> image.getWidth(null)).toList()
        );
    }

    @Test
    void loadsBrandIconForHeader() {
        ImageIcon icon = AppIcon.icon(48);

        assertNotNull(icon);
        assertEquals(48, icon.getIconWidth());
        assertEquals(48, icon.getIconHeight());
    }
}
