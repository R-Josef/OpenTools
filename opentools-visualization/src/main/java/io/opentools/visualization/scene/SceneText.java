package io.opentools.visualization.scene;

public record SceneText(
        double x,
        double y,
        String text,
        String color,
        double fontSize,
        boolean bold,
        TextAnchor anchor,
        double rotation,
        String tooltip
) implements ScenePrimitive {
    public enum TextAnchor {
        LEFT,
        CENTER,
        RIGHT
    }

    public SceneText {
        text = text == null ? "" : text;
        color = color == null ? "#1F2933" : color;
        if (fontSize <= 0) {
            throw new IllegalArgumentException("Scene text font size must be positive");
        }
        anchor = anchor == null ? TextAnchor.LEFT : anchor;
        tooltip = tooltip == null ? "" : tooltip;
    }

    public SceneText(
            double x,
            double y,
            String text,
            String color,
            double fontSize
    ) {
        this(x, y, text, color, fontSize, false, TextAnchor.LEFT, 0, "");
    }
}
