package io.opentools.visualization.scene;

import java.util.ArrayList;
import java.util.List;

public final class PlotSceneBuilder {
    private final int width;
    private final int height;
    private final String background;
    private final List<ScenePrimitive> primitives = new ArrayList<>();

    public PlotSceneBuilder(int width, int height, String background) {
        this.width = width;
        this.height = height;
        this.background = background;
    }

    public PlotSceneBuilder rect(
            double x,
            double y,
            double width,
            double height,
            String fill,
            String stroke,
            double strokeWidth
    ) {
        primitives.add(new SceneRect(x, y, width, height, fill, stroke, strokeWidth, 1, ""));
        return this;
    }

    public PlotSceneBuilder rect(
            double x,
            double y,
            double width,
            double height,
            String fill,
            String stroke,
            double strokeWidth,
            double opacity,
            String tooltip
    ) {
        primitives.add(new SceneRect(x, y, width, height, fill, stroke, strokeWidth, opacity, tooltip));
        return this;
    }

    public PlotSceneBuilder line(
            double x1,
            double y1,
            double x2,
            double y2,
            String stroke,
            double strokeWidth
    ) {
        primitives.add(new SceneLine(x1, y1, x2, y2, stroke, strokeWidth, ""));
        return this;
    }

    public PlotSceneBuilder circle(
            double x,
            double y,
            double radius,
            String fill,
            String stroke,
            double strokeWidth,
            double opacity,
            String tooltip
    ) {
        primitives.add(new SceneCircle(x, y, radius, fill, stroke, strokeWidth, opacity, tooltip));
        return this;
    }

    public PlotSceneBuilder polygon(
            List<ScenePoint> points,
            String fill,
            String stroke,
            double strokeWidth,
            String tooltip
    ) {
        primitives.add(new ScenePolygon(points, fill, stroke, strokeWidth, 1, tooltip));
        return this;
    }

    public PlotSceneBuilder text(
            double x,
            double y,
            String text,
            String color,
            double fontSize
    ) {
        primitives.add(new SceneText(x, y, text, color, fontSize));
        return this;
    }

    public PlotSceneBuilder text(
            double x,
            double y,
            String text,
            String color,
            double fontSize,
            boolean bold
    ) {
        primitives.add(new SceneText(
                x,
                y,
                text,
                color,
                fontSize,
                bold,
                SceneText.TextAnchor.LEFT,
                0,
                ""
        ));
        return this;
    }

    public PlotSceneBuilder text(
            double x,
            double y,
            String text,
            String color,
            double fontSize,
            boolean bold,
            SceneText.TextAnchor anchor,
            double rotation,
            String tooltip
    ) {
        primitives.add(new SceneText(x, y, text, color, fontSize, bold, anchor, rotation, tooltip));
        return this;
    }

    public PlotScene build() {
        return new PlotScene(width, height, background, primitives);
    }
}
