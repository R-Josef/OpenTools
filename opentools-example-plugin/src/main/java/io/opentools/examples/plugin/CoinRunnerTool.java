package io.opentools.examples.plugin;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.InteractiveOpenTool;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolPanel;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolUiContext;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class CoinRunnerTool extends AbstractOpenTool implements InteractiveOpenTool {
    private static final String TOOL_ID = "plugin.example.coin-runner";
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_ID,
            "Coin Runner",
            "Play a short keyboard-controlled coin runner and record the run.",
            "Plugin",
            List.of("game", "runner", "game tool", "金币", "跑酷"),
            List.of("Interactive"),
            List.of("JSON", "PNG"),
            "1.0.0",
            List.of(
                    new ToolParameter(
                            "playerName",
                            "Player name",
                            "Name shown above the runner.",
                            ParameterType.STRING,
                            true,
                            "Runner",
                            List.of(),
                            null,
                            null,
                            false,
                            null
                    ),
                    ToolParameter.integer("distance", "Distance", "Distance in meters.", 0, 0, 1_000_000),
                    ToolParameter.integer("score", "Score", "Final run score.", 0, 0, 100_000_000),
                    ToolParameter.integer("coins", "Coins", "Coins collected.", 0, 0, 100_000),
                    ToolParameter.integer("durationMs", "Duration", "Run duration in milliseconds.", 0, 0, 86_400_000),
                    new ToolParameter(
                            "endedReason",
                            "End reason",
                            "How the run ended.",
                            ParameterType.ENUM,
                            false,
                            "SECOND_CLICK",
                            List.of("SECOND_CLICK", "COLLISION"),
                            null,
                            null,
                            true,
                            null
                    )
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolPanel createPanel(ToolUiContext context) {
        return new CoinRunnerPanel(context);
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        String player = request.string("playerName");
        int distance = request.integer("distance");
        int score = request.integer("score");
        int coins = request.integer("coins");
        int durationMs = request.integer("durationMs");
        String reason = request.string("endedReason");
        context.progress(0.25, "Recording runner result");

        Path json = context.defaultOutputPath("coin-runner-result.json");
        Path png = context.defaultOutputPath("coin-runner-result.png");
        Files.writeString(json, json(player, distance, score, coins, durationMs, reason), StandardCharsets.UTF_8);
        renderResult(png, player, distance, score, coins);
        context.progress(1.0, "Coin runner result saved");

        ResultTable table = new ResultTable(
                List.of("Player", "Distance (m)", "Score", "Coins", "Duration (s)", "Ended"),
                List.of(List.of(
                        player,
                        Integer.toString(distance),
                        Integer.toString(score),
                        Integer.toString(coins),
                        String.format("%.1f", durationMs / 1000.0),
                        reason
                ))
        );
        return new ToolResult(
                "Coin runner completed",
                "result.plugin.example.coin-runner.summary",
                List.of(player, distance, score),
                "Recorded one interactive Coin Runner run.",
                "result.plugin.example.coin-runner.detail",
                List.of(),
                table,
                List.of(
                        new OutputArtifact(json, "Run data", "application/json"),
                        new OutputArtifact(png, "Final scene", "image/png")
                ),
                Map.of(
                        "playerName", player,
                        "distance", distance,
                        "score", score,
                        "coins", coins,
                        "durationMs", durationMs,
                        "endedReason", reason
                )
        );
    }

    private static void renderResult(Path output, String player, int distance, int score, int coins) throws Exception {
        BufferedImage image = new BufferedImage(1200, 552, BufferedImage.TYPE_INT_ARGB);
        List<CoinRunnerModel.Entity> entities = List.of(
                new CoinRunnerModel.Entity(CoinRunnerModel.EntityKind.COIN, 430, 68, 28),
                new CoinRunnerModel.Entity(CoinRunnerModel.EntityKind.COIN, 600, 132, 28),
                new CoinRunnerModel.Entity(CoinRunnerModel.EntityKind.OBSTACLE, 520, 0, 38)
        );
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            CoinRunnerRenderer.draw(
                    graphics,
                    image.getWidth(),
                    image.getHeight(),
                    player,
                    0,
                    entities,
                    distance,
                    score,
                    coins,
                    null
            );
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", output.toFile());
    }

    private static String json(
            String player,
            int distance,
            int score,
            int coins,
            int durationMs,
            String reason
    ) {
        return """
                {
                  "toolId": "%s",
                  "playerName": "%s",
                  "distanceMeters": %d,
                  "score": %d,
                  "coins": %d,
                  "durationMs": %d,
                  "endedReason": "%s"
                }
                """.formatted(TOOL_ID, jsonEscape(player), distance, score, coins, durationMs, jsonEscape(reason));
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
