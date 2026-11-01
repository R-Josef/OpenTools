package io.opentools.visualization.export;

import io.opentools.visualization.scene.PlotScene;
import io.opentools.visualization.scene.SceneCircle;
import io.opentools.visualization.scene.SceneLine;
import io.opentools.visualization.scene.ScenePoint;
import io.opentools.visualization.scene.ScenePolygon;
import io.opentools.visualization.scene.ScenePrimitive;
import io.opentools.visualization.scene.SceneRect;
import io.opentools.visualization.scene.SceneText;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class SvgSceneWriter {
    private SvgSceneWriter() {
    }

    public static void write(PlotScene scene, Path output) throws Exception {
        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append(String.format(
                Locale.ROOT,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\">\n",
                scene.width(),
                scene.height(),
                scene.width(),
                scene.height()
        ));
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"").append(escape(scene.background())).append("\"/>\n");
        svg.append("<g font-family=\"Arial, Helvetica, sans-serif\">\n");
        for (ScenePrimitive primitive : scene.primitives()) {
            if (primitive instanceof SceneRect rect) {
                svg.append(String.format(
                        Locale.ROOT,
                        "<rect x=\"%.3f\" y=\"%.3f\" width=\"%.3f\" height=\"%.3f\" fill=\"%s\" fill-opacity=\"%.3f\"",
                        rect.x(),
                        rect.y(),
                        rect.width(),
                        rect.height(),
                        escape(rect.fill()),
                        rect.opacity()
                ));
                if (rect.strokeWidth() > 0 && !rect.stroke().isBlank()) {
                    svg.append(String.format(
                            Locale.ROOT,
                            " stroke=\"%s\" stroke-width=\"%.3f\"",
                            escape(rect.stroke()),
                            rect.strokeWidth()
                    ));
                }
                svg.append("/>\n");
            } else if (primitive instanceof SceneLine line) {
                svg.append(String.format(
                        Locale.ROOT,
                        "<line x1=\"%.3f\" y1=\"%.3f\" x2=\"%.3f\" y2=\"%.3f\" stroke=\"%s\" stroke-width=\"%.3f\"/>\n",
                        line.x1(),
                        line.y1(),
                        line.x2(),
                        line.y2(),
                        escape(line.stroke()),
                        line.strokeWidth()
                ));
            } else if (primitive instanceof SceneCircle circle) {
                svg.append(String.format(
                        Locale.ROOT,
                        "<circle cx=\"%.3f\" cy=\"%.3f\" r=\"%.3f\" fill=\"%s\" fill-opacity=\"%.3f\" stroke=\"%s\" stroke-width=\"%.3f\"/>\n",
                        circle.x(),
                        circle.y(),
                        circle.radius(),
                        escape(circle.fill()),
                        circle.opacity(),
                        escape(circle.stroke()),
                        circle.strokeWidth()
                ));
            } else if (primitive instanceof ScenePolygon polygon) {
                svg.append("<polygon points=\"");
                for (ScenePoint point : polygon.points()) {
                    svg.append(String.format(Locale.ROOT, "%.3f,%.3f ", point.x(), point.y()));
                }
                svg.append(String.format(
                        Locale.ROOT,
                        "\" fill=\"%s\" fill-opacity=\"%.3f\" stroke=\"%s\" stroke-width=\"%.3f\"/>\n",
                        escape(polygon.fill()),
                        polygon.opacity(),
                        escape(polygon.stroke()),
                        polygon.strokeWidth()
                ));
            } else if (primitive instanceof SceneText text) {
                String anchor = switch (text.anchor()) {
                    case LEFT -> "start";
                    case CENTER -> "middle";
                    case RIGHT -> "end";
                };
                svg.append(String.format(
                        Locale.ROOT,
                        "<text x=\"%.3f\" y=\"%.3f\" fill=\"%s\" font-size=\"%.3f\" font-weight=\"%s\" text-anchor=\"%s\"",
                        text.x(),
                        text.y(),
                        escape(text.color()),
                        text.fontSize(),
                        text.bold() ? "700" : "400",
                        anchor
                ));
                if (text.rotation() != 0) {
                    svg.append(String.format(
                            Locale.ROOT,
                            " transform=\"rotate(%.3f %.3f %.3f)\"",
                            text.rotation(),
                            text.x(),
                            text.y()
                    ));
                }
                svg.append('>').append(escape(text.text())).append("</text>\n");
            }
        }
        svg.append("</g>\n</svg>\n");
        Files.writeString(output, svg.toString());
    }

    private static String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
