package io.opentools.visualization.export;

import io.opentools.visualization.model.PlotSpec;
import io.opentools.visualization.render.Java2DSceneRenderer;
import io.opentools.visualization.render.PlotSceneFactory;
import io.opentools.visualization.scene.PlotScene;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlotExporter {
    private PlotExporter() {
    }

    public static List<Path> export(PlotSpec spec, Path basePath) throws Exception {
        PlotScene scene = PlotSceneFactory.create(spec);
        Path absoluteBase = basePath.toAbsolutePath().normalize();
        Path parent = absoluteBase.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        List<Path> outputs = new ArrayList<>();
        for (String format : spec.export().formats()) {
            PlotFormat selected = PlotFormat.parse(format);
            Path output = Path.of(absoluteBase + "." + selected.extension());
            Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
            switch (selected) {
                case PNG -> writePng(scene, temporary, spec.export().transparent());
                case SVG -> SvgSceneWriter.write(scene, temporary);
                case PDF -> PdfSceneWriter.write(scene, temporary, spec.export().dpi());
            }
            publish(temporary, output);
            outputs.add(output);
        }
        return List.copyOf(outputs);
    }

    public static void writePng(PlotScene scene, Path output, boolean transparent) throws IOException {
        BufferedImage image = new BufferedImage(scene.width(), scene.height(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            if (transparent) {
                graphics.setComposite(java.awt.AlphaComposite.Clear);
                graphics.fillRect(0, 0, scene.width(), scene.height());
                graphics.setComposite(java.awt.AlphaComposite.SrcOver);
            }
            Java2DSceneRenderer.paint(graphics, scene, transparent);
        } finally {
            graphics.dispose();
        }
        if (!ImageIO.write(image, "png", output.toFile())) {
            throw new IOException("No PNG writer is available");
        }
    }

    private static void publish(Path temporary, Path output) throws IOException {
        try {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
