package io.opentools.visualization.render;

import io.opentools.visualization.scene.PlotScene;
import io.opentools.visualization.scene.SceneCircle;
import io.opentools.visualization.scene.SceneLine;
import io.opentools.visualization.scene.ScenePoint;
import io.opentools.visualization.scene.ScenePolygon;
import io.opentools.visualization.scene.ScenePrimitive;
import io.opentools.visualization.scene.SceneRect;
import io.opentools.visualization.scene.SceneText;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

public final class Java2DSceneRenderer {
    private Java2DSceneRenderer() {
    }

    public static void paint(Graphics2D graphics, PlotScene scene) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(PlotColors.parse(scene.background()));
            g.fillRect(0, 0, scene.width(), scene.height());
            for (ScenePrimitive primitive : scene.primitives()) {
                paintPrimitive(g, primitive);
            }
        } finally {
            g.dispose();
        }
    }

    private static void paintPrimitive(Graphics2D g, ScenePrimitive primitive) {
        if (primitive instanceof SceneRect rect) {
            g.setComposite(java.awt.AlphaComposite.SrcOver.derive((float) rect.opacity()));
            g.setColor(PlotColors.parse(rect.fill()));
            g.fill(new Rectangle2D.Double(rect.x(), rect.y(), rect.width(), rect.height()));
            if (rect.strokeWidth() > 0 && !rect.stroke().isBlank()) {
                g.setColor(PlotColors.parse(rect.stroke()));
                g.setStroke(new BasicStroke((float) rect.strokeWidth()));
                g.draw(new Rectangle2D.Double(rect.x(), rect.y(), rect.width(), rect.height()));
            }
        } else if (primitive instanceof SceneLine line) {
            g.setComposite(java.awt.AlphaComposite.SrcOver);
            g.setColor(PlotColors.parse(line.stroke()));
            g.setStroke(new BasicStroke((float) line.strokeWidth()));
            g.draw(new Line2D.Double(line.x1(), line.y1(), line.x2(), line.y2()));
        } else if (primitive instanceof SceneCircle circle) {
            g.setComposite(java.awt.AlphaComposite.SrcOver.derive((float) circle.opacity()));
            g.setColor(PlotColors.parse(circle.fill()));
            double radius = circle.radius();
            g.fill(new Ellipse2D.Double(circle.x() - radius, circle.y() - radius, radius * 2, radius * 2));
            if (circle.strokeWidth() > 0 && !circle.stroke().isBlank()) {
                g.setColor(PlotColors.parse(circle.stroke()));
                g.setStroke(new BasicStroke((float) circle.strokeWidth()));
                g.draw(new Ellipse2D.Double(circle.x() - radius, circle.y() - radius, radius * 2, radius * 2));
            }
        } else if (primitive instanceof ScenePolygon polygon) {
            g.setComposite(java.awt.AlphaComposite.SrcOver.derive((float) polygon.opacity()));
            Path2D path = new Path2D.Double();
            ScenePoint first = polygon.points().getFirst();
            path.moveTo(first.x(), first.y());
            for (int index = 1; index < polygon.points().size(); index++) {
                ScenePoint point = polygon.points().get(index);
                path.lineTo(point.x(), point.y());
            }
            path.closePath();
            g.setColor(PlotColors.parse(polygon.fill()));
            g.fill(path);
            if (polygon.strokeWidth() > 0 && !polygon.stroke().isBlank()) {
                g.setColor(PlotColors.parse(polygon.stroke()));
                g.setStroke(new BasicStroke((float) polygon.strokeWidth()));
                g.draw(path);
            }
        } else if (primitive instanceof SceneText text) {
            g.setComposite(java.awt.AlphaComposite.SrcOver);
            Font font = new Font(
                    "SansSerif",
                    text.bold() ? Font.BOLD : Font.PLAIN,
                    (int) Math.ceil(text.fontSize())
            );
            g.setFont(font);
            g.setColor(PlotColors.parse(text.color()));
            AffineTransform original = g.getTransform();
            g.translate(text.x(), text.y());
            if (text.rotation() != 0) {
                g.rotate(Math.toRadians(text.rotation()));
            }
            int textWidth = g.getFontMetrics().stringWidth(text.text());
            double xOffset = switch (text.anchor()) {
                case LEFT -> 0;
                case CENTER -> -textWidth / 2.0;
                case RIGHT -> -textWidth;
            };
            g.drawString(text.text(), (float) xOffset, 0);
            g.setTransform(original);
        }
    }
}
