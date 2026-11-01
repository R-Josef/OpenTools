package io.opentools.ui;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

final class AppIcon {
    private static final int[] WINDOW_SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};

    private AppIcon() {
    }

    static List<Image> windowImages() {
        List<Image> images = new ArrayList<>();
        for (int size : WINDOW_SIZES) {
            Image image = load(size);
            if (image != null) {
                images.add(image);
            }
        }
        return List.copyOf(images);
    }

    static ImageIcon icon(int size) {
        Image image = load(size);
        return image == null ? null : new ImageIcon(image);
    }

    private static Image load(int size) {
        String resource = "/icons/opentools-" + size + ".png";
        try (InputStream input = AppIcon.class.getResourceAsStream(resource)) {
            return input == null ? null : ImageIO.read(input);
        } catch (IOException ex) {
            return null;
        }
    }
}
