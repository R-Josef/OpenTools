package io.opentools.visualization.export;

import io.opentools.visualization.render.PlotColors;
import io.opentools.visualization.scene.PlotScene;
import io.opentools.visualization.scene.SceneCircle;
import io.opentools.visualization.scene.SceneLine;
import io.opentools.visualization.scene.ScenePoint;
import io.opentools.visualization.scene.ScenePolygon;
import io.opentools.visualization.scene.ScenePrimitive;
import io.opentools.visualization.scene.SceneRect;
import io.opentools.visualization.scene.SceneText;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PdfSceneWriter {
    private static final List<Path> FONT_CANDIDATES = List.of(
            Path.of("C:/Windows/Fonts/simhei.ttf"),
            Path.of("C:/Windows/Fonts/arial.ttf"),
            Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
            Path.of("/System/Library/Fonts/Supplemental/Arial.ttf")
    );

    private PdfSceneWriter() {
    }

    public static void write(PlotScene scene, Path output, int dpi) throws Exception {
        float scale = (float) (72.0 / Math.max(72, dpi));
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(scene.width() * scale, scene.height() * scale));
            document.addPage(page);
            FontSet fonts = loadFonts(document);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.saveGraphicsState();
                content.transform(Matrix.getScaleInstance(scale, scale));
                fill(content, scene.background(), 1);
                content.addRect(0, 0, scene.width(), scene.height());
                content.fill();
                for (ScenePrimitive primitive : scene.primitives()) {
                    drawPrimitive(content, scene.height(), primitive, fonts);
                }
                content.restoreGraphicsState();
            }
            document.save(output.toFile());
        }
    }

    private static void drawPrimitive(
            PDPageContentStream content,
            int sceneHeight,
            ScenePrimitive primitive,
            FontSet fonts
    ) throws Exception {
        if (primitive instanceof SceneRect rect) {
            fill(content, rect.fill(), rect.opacity());
            content.addRect(
                    (float) rect.x(),
                    (float) (sceneHeight - rect.y() - rect.height()),
                    (float) rect.width(),
                    (float) rect.height()
            );
            content.fill();
            if (rect.strokeWidth() > 0 && !rect.stroke().isBlank()) {
                stroke(content, rect.stroke(), rect.strokeWidth());
                content.addRect(
                        (float) rect.x(),
                        (float) (sceneHeight - rect.y() - rect.height()),
                        (float) rect.width(),
                        (float) rect.height()
                );
                content.stroke();
            }
        } else if (primitive instanceof SceneLine line) {
            stroke(content, line.stroke(), line.strokeWidth());
            content.moveTo((float) line.x1(), (float) (sceneHeight - line.y1()));
            content.lineTo((float) line.x2(), (float) (sceneHeight - line.y2()));
            content.stroke();
        } else if (primitive instanceof SceneCircle circle) {
            fill(content, circle.fill(), circle.opacity());
            drawCircle(content, circle.x(), sceneHeight - circle.y(), circle.radius());
            content.fill();
            if (circle.strokeWidth() > 0 && !circle.stroke().isBlank()) {
                stroke(content, circle.stroke(), circle.strokeWidth());
                drawCircle(content, circle.x(), sceneHeight - circle.y(), circle.radius());
                content.stroke();
            }
        } else if (primitive instanceof ScenePolygon polygon) {
            fill(content, polygon.fill(), polygon.opacity());
            ScenePoint first = polygon.points().getFirst();
            content.moveTo((float) first.x(), (float) (sceneHeight - first.y()));
            for (int index = 1; index < polygon.points().size(); index++) {
                ScenePoint point = polygon.points().get(index);
                content.lineTo((float) point.x(), (float) (sceneHeight - point.y()));
            }
            content.closePath();
            content.fill();
            if (polygon.strokeWidth() > 0 && !polygon.stroke().isBlank()) {
                stroke(content, polygon.stroke(), polygon.strokeWidth());
                content.stroke();
            }
        } else if (primitive instanceof SceneText text) {
            PDFont font = text.bold() ? fonts.bold() : fonts.regular();
            String value = fonts.unicode() ? text.text() : asciiFallback(text.text());
            float fontSize = (float) text.fontSize();
            float width = font.getStringWidth(value) / 1000f * fontSize;
            float xOffset = switch (text.anchor()) {
                case LEFT -> 0;
                case CENTER -> -width / 2;
                case RIGHT -> -width;
            };
            content.beginText();
            content.setFont(font, fontSize);
            fill(content, text.color(), 1);
            Matrix matrix = Matrix.getRotateInstance(
                    Math.toRadians(-text.rotation()),
                    (float) text.x(),
                    (float) (sceneHeight - text.y())
            );
            matrix.translate(xOffset, 0);
            content.setTextMatrix(matrix);
            content.showText(value);
            content.endText();
        }
    }

    private static void drawCircle(PDPageContentStream content, double x, double y, double radius) throws Exception {
        float k = 0.5522848f;
        float cx = (float) x;
        float cy = (float) y;
        float r = (float) radius;
        content.moveTo(cx + r, cy);
        content.curveTo(cx + r, cy + k * r, cx + k * r, cy + r, cx, cy + r);
        content.curveTo(cx - k * r, cy + r, cx - r, cy + k * r, cx - r, cy);
        content.curveTo(cx - r, cy - k * r, cx - k * r, cy - r, cx, cy - r);
        content.curveTo(cx + k * r, cy - r, cx + r, cy - k * r, cx + r, cy);
        content.closePath();
    }

    private static void fill(PDPageContentStream content, String color, double opacity) throws Exception {
        Color parsed = PlotColors.parse(color);
        content.setNonStrokingColor(
                parsed.getRed() / 255f,
                parsed.getGreen() / 255f,
                parsed.getBlue() / 255f
        );
        org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState state =
                new org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState();
        state.setNonStrokingAlphaConstant((float) opacity);
        content.setGraphicsStateParameters(state);
    }

    private static void stroke(PDPageContentStream content, String color, double width) throws Exception {
        Color parsed = PlotColors.parse(color);
        content.setStrokingColor(
                parsed.getRed() / 255f,
                parsed.getGreen() / 255f,
                parsed.getBlue() / 255f
        );
        content.setLineWidth((float) width);
    }

    private static FontSet loadFonts(PDDocument document) {
        String configured = System.getenv("OPENTOOLS_PDF_FONT");
        if (configured != null && !configured.isBlank()) {
            try {
                PDFont regular = PDType0Font.load(document, Path.of(configured).toFile());
                return new FontSet(regular, regular, true);
            } catch (Exception ignored) {
                // Fall through to system font candidates and the standard PDF font.
            }
        }
        for (Path candidate : FONT_CANDIDATES) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                PDFont regular = PDType0Font.load(document, candidate.toFile());
                return new FontSet(regular, regular, true);
            } catch (Exception ignored) {
                // Some system collections cannot be embedded as a single OpenType font.
            }
        }
        PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        return new FontSet(regular, bold, false);
    }

    private static String asciiFallback(String value) {
        StringBuilder result = new StringBuilder();
        value.codePoints().forEach(codePoint -> result.append(codePoint < 256 ? (char) codePoint : '?'));
        return result.toString();
    }

    private record FontSet(PDFont regular, PDFont bold, boolean unicode) {
    }
}
