package io.opentools.visualization.scene;

public record SceneCircle(
        double x,
        double y,
        double radius,
        String fill,
        String stroke,
        double strokeWidth,
        double opacity,
        String tooltip
) implements ScenePrimitive {
    public SceneCircle {
        radius = Math.max(0, radius);
        strokeWidth = Math.max(0, strokeWidth);
        opacity = opacity < 0 ? 0 : Math.min(opacity, 1);
        tooltip = tooltip == null ? "" : tooltip;
    }
}
