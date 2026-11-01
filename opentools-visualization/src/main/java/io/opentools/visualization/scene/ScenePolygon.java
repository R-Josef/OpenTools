package io.opentools.visualization.scene;

import java.util.List;

public record ScenePolygon(
        List<ScenePoint> points,
        String fill,
        String stroke,
        double strokeWidth,
        double opacity,
        String tooltip
) implements ScenePrimitive {
    public ScenePolygon {
        points = points == null ? List.of() : List.copyOf(points);
        if (points.size() < 3) {
            throw new IllegalArgumentException("A scene polygon requires at least three points");
        }
        strokeWidth = Math.max(0, strokeWidth);
        opacity = opacity < 0 ? 0 : Math.min(opacity, 1);
        tooltip = tooltip == null ? "" : tooltip;
    }
}
