package io.opentools.visualization.scene;

public sealed interface ScenePrimitive permits SceneRect, SceneLine, SceneCircle, ScenePolygon, SceneText {
    String tooltip();
}
