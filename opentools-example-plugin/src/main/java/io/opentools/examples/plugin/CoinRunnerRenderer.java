package io.opentools.examples.plugin;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

final class CoinRunnerRenderer {
    private static final double WORLD_WIDTH = CoinRunnerModel.WORLD_WIDTH;
    private static final Color INK = new Color(35, 49, 61);
    private static final Color ACCENT = new Color(36, 139, 135);
    private static final Color COIN = new Color(241, 184, 52);
    private static final Color DANGER = new Color(211, 83, 74);

    private CoinRunnerRenderer() {
    }

    static void draw(
            Graphics2D graphics,
            int width,
            int height,
            CoinRunnerModel model,
            String overlay
    ) {
        draw(
                graphics,
                width,
                height,
                model.playerName(),
                model.playerY(),
                model.entities(),
                model.distanceMeters(),
                model.score(),
                model.coins(),
                overlay
        );
    }

    static void draw(
            Graphics2D graphics,
            int width,
            int height,
            String playerName,
            double playerY,
            List<CoinRunnerModel.Entity> entities,
            double distanceMeters,
            double score,
            int coins,
            String overlay
    ) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(210, 237, 242));
            g.fillRect(0, 0, width, height);
            double scale = Math.min(width / WORLD_WIDTH, height / CoinRunnerModel.WORLD_HEIGHT);
            double offsetX = (width - WORLD_WIDTH * scale) / 2;
            double offsetY = (height - CoinRunnerModel.WORLD_HEIGHT * scale) / 2;
            g.translate(offsetX, offsetY);
            g.scale(scale, scale);
            drawBackdrop(g);
            for (CoinRunnerModel.Entity entity : entities) {
                if (entity.kind() == CoinRunnerModel.EntityKind.COIN) {
                    drawCoin(g, entity);
                } else {
                    drawObstacle(g, entity);
                }
            }
            drawRunner(g, playerName, playerY);
            drawMetrics(g, distanceMeters, score, coins);
            if (overlay != null && !overlay.isBlank()) {
                drawOverlay(g, overlay);
            }
        } finally {
            g.dispose();
        }
    }

    private static void drawBackdrop(Graphics2D g) {
        g.setColor(new Color(210, 237, 242));
        g.fillRect(0, 0, (int) WORLD_WIDTH, (int) CoinRunnerModel.WORLD_HEIGHT);
        g.setColor(new Color(232, 244, 241));
        Path2D hills = new Path2D.Double();
        hills.moveTo(0, 330);
        hills.curveTo(170, 230, 310, 310, 470, 270);
        hills.curveTo(650, 220, 790, 305, 1000, 250);
        hills.lineTo(1000, 390);
        hills.lineTo(0, 390);
        hills.closePath();
        g.fill(hills);
        g.setColor(new Color(69, 126, 117));
        g.fillRect(0, (int) CoinRunnerModel.GROUND_Y, (int) WORLD_WIDTH, 80);
        g.setColor(new Color(92, 159, 146));
        g.fillRect(0, (int) CoinRunnerModel.GROUND_Y, (int) WORLD_WIDTH, 7);
        g.setColor(new Color(232, 244, 241));
        g.setStroke(new BasicStroke(3));
        for (int x = -40; x < WORLD_WIDTH + 80; x += 85) {
            g.drawLine(x, 404, x + 38, 404);
        }
    }

    private static void drawCoin(Graphics2D g, CoinRunnerModel.Entity entity) {
        double x = entity.x();
        double y = CoinRunnerModel.GROUND_Y - entity.y();
        g.setColor(new Color(176, 123, 28, 70));
        g.fill(new Ellipse2D.Double(x - 14, y - 10, 31, 31));
        g.setColor(COIN);
        g.fill(new Ellipse2D.Double(x - 15, y - 15, 30, 30));
        g.setColor(new Color(255, 226, 128));
        g.setStroke(new BasicStroke(3));
        g.draw(new Ellipse2D.Double(x - 10, y - 10, 20, 20));
        g.setColor(new Color(142, 91, 19));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        drawCentered(g, "C", x, y + 5);
    }

    private static void drawObstacle(Graphics2D g, CoinRunnerModel.Entity entity) {
        double size = entity.size();
        double x = entity.x() - size / 2;
        double y = CoinRunnerModel.GROUND_Y - size;
        g.setColor(new Color(132, 55, 49, 60));
        g.fill(new RoundRectangle2D.Double(x + 4, y + 7, size, size, 9, 9));
        g.setColor(DANGER);
        g.fill(new RoundRectangle2D.Double(x, y, size, size, 9, 9));
        g.setColor(new Color(255, 225, 211));
        g.setStroke(new BasicStroke(4));
        g.drawLine((int) (x + 9), (int) (y + size - 9), (int) (x + size - 9), (int) (y + 9));
        g.setColor(new Color(151, 54, 47));
        g.setStroke(new BasicStroke(2));
        g.draw(new RoundRectangle2D.Double(x, y, size, size, 9, 9));
    }

    private static void drawRunner(Graphics2D g, String playerName, double playerY) {
        double x = CoinRunnerModel.PLAYER_X - CoinRunnerModel.PLAYER_SIZE / 2;
        double y = CoinRunnerModel.GROUND_Y - CoinRunnerModel.PLAYER_SIZE - playerY;
        g.setColor(new Color(28, 52, 60, 60));
        g.fill(new Ellipse2D.Double(x + 2, CoinRunnerModel.GROUND_Y + 1, 30, 10));
        g.setColor(ACCENT);
        g.fill(new RoundRectangle2D.Double(x, y, CoinRunnerModel.PLAYER_SIZE, CoinRunnerModel.PLAYER_SIZE, 11, 11));
        g.setColor(new Color(225, 248, 242));
        g.fill(new Ellipse2D.Double(x + 8, y + 9, 6, 7));
        g.fill(new Ellipse2D.Double(x + 21, y + 9, 6, 7));
        g.setColor(INK);
        g.fill(new Ellipse2D.Double(x + 10, y + 11, 3, 4));
        g.fill(new Ellipse2D.Double(x + 23, y + 11, 3, 4));
        g.setColor(new Color(255, 194, 131));
        g.fillRoundRect((int) (x + 12), (int) (y + 21), 12, 4, 4, 4);

        String id = playerName;
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, 16);
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        int labelWidth = Math.min(190, metrics.stringWidth(id) + 20);
        int labelX = (int) (CoinRunnerModel.PLAYER_X - labelWidth / 2.0);
        int labelY = (int) (y - 34);
        g.setColor(new Color(255, 255, 255, 225));
        g.fillRoundRect(labelX, labelY, labelWidth, 25, 11, 11);
        g.setColor(INK);
        drawCentered(g, clip(id, metrics, labelWidth - 16), CoinRunnerModel.PLAYER_X, labelY + 17);
    }

    private static void drawMetrics(Graphics2D g, double distanceMeters, double score, int coins) {
        g.setColor(new Color(255, 255, 255, 232));
        g.fillRoundRect(735, 28, 225, 106, 16, 16);
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.drawString("DISTANCE", 756, 54);
        g.drawString("SCORE", 756, 84);
        g.drawString("COINS", 756, 114);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        g.setColor(ACCENT);
        drawRight(g, String.format("%d m", Math.round(distanceMeters)), 936, 56);
        g.setColor(new Color(195, 130, 27));
        drawRight(g, String.format("%d", Math.round(score)), 936, 86);
        g.setColor(new Color(182, 91, 53));
        drawRight(g, Integer.toString(coins), 936, 116);
    }

    private static void drawOverlay(Graphics2D g, String text) {
        g.setColor(new Color(25, 39, 49, 175));
        g.fillRoundRect(260, 182, 480, 92, 20, 20);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        drawCentered(g, text, WORLD_WIDTH / 2, 237);
    }

    private static String clip(String value, FontMetrics metrics, int maxWidth) {
        if (metrics.stringWidth(value) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        String candidate = value;
        while (!candidate.isEmpty() && metrics.stringWidth(candidate + suffix) > maxWidth) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate + suffix;
    }

    private static void drawCentered(Graphics2D g, String text, double centerX, double baselineY) {
        int width = g.getFontMetrics().stringWidth(text);
        g.drawString(text, (float) (centerX - width / 2.0), (float) baselineY);
    }

    private static void drawRight(Graphics2D g, String text, double rightX, double baselineY) {
        int width = g.getFontMetrics().stringWidth(text);
        g.drawString(text, (float) (rightX - width), (float) baselineY);
    }
}
