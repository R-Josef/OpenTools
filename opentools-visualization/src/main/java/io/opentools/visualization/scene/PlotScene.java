package io.opentools.visualization.scene;

import java.util.List;

public record PlotScene(
        int width,
        int height,
        String background,
        List<ScenePrimitive> primitives
) {
    public PlotScene {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Plot scene dimensions must be positive");
        }
        background = background == null ? "#FFFFFF" : background;
        primitives = primitives == null ? List.of() : List.copyOf(primitives);
    }

    public String tooltipAt(double x, double y) {
        for (int index = primitives.size() - 1; index >= 0; index--) {
            ScenePrimitive primitive = primitives.get(index);
            if (!primitive.tooltip().isBlank() && contains(primitive, x, y)) {
                return primitive.tooltip();
            }
        }
        return "";
    }

    private static boolean contains(ScenePrimitive primitive, double x, double y) {
        if (primitive instanceof SceneRect rect) {
            return x >= rect.x() && x <= rect.x() + rect.width()
                    && y >= rect.y() && y <= rect.y() + rect.height();
        }
        if (primitive instanceof SceneCircle circle) {
            double dx = x - circle.x();
            double dy = y - circle.y();
            return dx * dx + dy * dy <= circle.radius() * circle.radius();
        }
        return false;
    }
}
