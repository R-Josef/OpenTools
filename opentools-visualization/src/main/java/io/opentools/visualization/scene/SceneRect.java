package io.opentools.visualization.scene;

public record SceneRect(
        double x,
        double y,
        double width,
        double height,
        String fill,
        String stroke,
        double strokeWidth,
        double opacity,
        String tooltip
) implements ScenePrimitive {
    public SceneRect {
        opacity = opacity < 0 ? 0 : Math.min(opacity, 1);
        strokeWidth = Math.max(0, strokeWidth);
        tooltip = tooltip == null ? "" : tooltip;
    }
}
