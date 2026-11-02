package io.opentools.ui;

import io.opentools.core.i18n.LanguageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

final class PlotViewerImageTest {
    @TempDir
    Path tempDir;

    @Test
    void displaysPngArtifacts() throws Exception {
        Path image = tempDir.resolve("result.png");
        ImageIO.write(new BufferedImage(1200, 552, BufferedImage.TYPE_INT_ARGB), "png", image.toFile());
        LanguageManager language = new LanguageManager(tempDir, "en_US");

        assertDoesNotThrow(() -> SwingUtilities.invokeAndWait(() -> {
            PlotViewerPanel viewer = new PlotViewerPanel(language);
            try {
                viewer.loadImage(image);
            } catch (java.io.IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        }));
    }
}
