package io.opentools.examples.plugin;

import io.opentools.api.tool.ToolPanel;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolUiContext;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

final class CoinRunnerPanel extends ToolPanel {
    private static final Color MUTED = new Color(99, 112, 118);

    private final ToolUiContext context;
    private final JLabel playerLabel = new JLabel();
    private final JTextField playerName;
    private final JLabel status = new JLabel();
    private final CoinRunnerModel model = new CoinRunnerModel(System.nanoTime());
    private final GameCanvas canvas = new GameCanvas();
    private final Timer timer;
    private long lastTickNanos;
    private boolean submitted;

    CoinRunnerPanel(ToolUiContext context) {
        super(new BorderLayout(0, 12));
        this.context = context;
        setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JPanel header = new JPanel(new BorderLayout(16, 0));
        JLabel title = new JLabel(context.text(
                "tool.plugin.example.coin-runner.name",
                "Coin Runner"
        ));
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 8f));
        header.add(title, BorderLayout.WEST);

        JPanel playerRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        playerLabel.setText(context.text("game.playerName", "Player"));
        playerLabel.setForeground(MUTED);
        playerName = new JTextField(context.text("game.defaultPlayerName", "Runner"), 18);
        playerName.setToolTipText(context.text(
                "game.playerNameTooltip",
                "Name shown above the runner"
        ));
        playerRow.add(playerLabel);
        playerRow.add(playerName);
        header.add(playerRow, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        add(new GameViewport(), BorderLayout.CENTER);
        status.setForeground(MUTED);
        status.setText(context.text(
                "game.clickSceneToStart",
                "Click the scene to start; click again to finish"
        ));
        add(status, BorderLayout.SOUTH);

        timer = new Timer(16, ignored -> tick());
        timer.setCoalesce(true);
        timer.start();
        installKeyBindings();
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (event.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                canvas.requestFocusInWindow();
                if (model.state() == CoinRunnerModel.State.RUNNING) {
                    model.finishByClick();
                    finishRun();
                } else {
                    startRun();
                }
            }
        });
    }

    private void installKeyBindings() {
        canvas.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke("SPACE"), "jump");
        canvas.getActionMap().put("jump", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (model.state() == CoinRunnerModel.State.RUNNING) {
                    model.jump();
                    canvas.repaint();
                }
            }
        });
    }

    private void startRun() {
        submitted = false;
        model.start(playerName.getText());
        playerName.setEnabled(false);
        lastTickNanos = System.nanoTime();
        status.setText(context.text(
                "game.runningHint",
                "Press Space to jump; click to finish"
        ));
        canvas.requestFocusInWindow();
        canvas.repaint();
    }

    private void tick() {
        if (model.state() != CoinRunnerModel.State.RUNNING) {
            lastTickNanos = System.nanoTime();
            return;
        }
        long now = System.nanoTime();
        double deltaSeconds = lastTickNanos == 0 ? 0 : (now - lastTickNanos) / 1_000_000_000.0;
        lastTickNanos = now;
        model.tick(deltaSeconds);
        if (model.state() == CoinRunnerModel.State.FINISHED) {
            finishRun();
        }
        canvas.repaint();
    }

    private void finishRun() {
        if (submitted) {
            return;
        }
        submitted = true;
        playerName.setEnabled(true);
        status.setText(context.text("game.submitting", "Submitting this run..."));
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("playerName", model.playerName());
        parameters.put("distance", (int) Math.round(model.distanceMeters()));
        parameters.put("score", (int) Math.round(model.score()));
        parameters.put("coins", model.coins());
        parameters.put("durationMs", (int) Math.round(model.elapsedSeconds() * 1000));
        parameters.put(
                "endedReason",
                model.endReason() == CoinRunnerModel.EndReason.COLLISION ? "COLLISION" : "SECOND_CLICK"
        );
        SwingUtilities.invokeLater(() -> {
            try {
                String runId = context.submit(new ToolRequest(context.tool().id(), parameters));
                status.setText(context.text(
                        "game.submitted",
                        "Job submitted: {0}",
                        shortId(runId)
                ));
            } catch (Exception ex) {
                submitted = false;
                status.setText(context.text(
                        "game.submitFailed",
                        "Submission failed: {0}",
                        rootMessage(ex)
                ));
            }
            canvas.repaint();
        });
    }

    @Override
    public void close() {
        timer.stop();
    }

    private static String shortId(String runId) {
        return runId.length() <= 12 ? runId : runId.substring(0, 8);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private final class GameCanvas extends JPanel {
        private GameCanvas() {
            setFocusable(true);
            setOpaque(true);
            setBackground(new Color(210, 237, 242));
            setPreferredSize(new Dimension(900, 440));
            setMinimumSize(new Dimension(480, 300));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            String overlay = switch (model.state()) {
                case READY -> context.text("game.clickToStart", "Click to start");
                case RUNNING -> null;
                case FINISHED -> context.text("game.runFinished", "Run finished");
            };
            CoinRunnerRenderer.draw(
                    (java.awt.Graphics2D) graphics,
                    Math.max(1, getWidth()),
                    Math.max(1, getHeight()),
                    model,
                    overlay
            );
        }
    }

    private final class GameViewport extends JPanel {
        private GameViewport() {
            setLayout(null);
            setOpaque(true);
            setBackground(new Color(221, 234, 237));
            add(canvas);
        }

        @Override
        public void doLayout() {
            int availableWidth = Math.max(1, getWidth());
            int availableHeight = Math.max(1, getHeight());
            int canvasWidth = availableWidth;
            int canvasHeight = (int) Math.round(
                    canvasWidth * CoinRunnerModel.WORLD_HEIGHT / CoinRunnerModel.WORLD_WIDTH
            );
            if (canvasHeight > availableHeight) {
                canvasHeight = availableHeight;
                canvasWidth = (int) Math.round(
                        canvasHeight * CoinRunnerModel.WORLD_WIDTH / CoinRunnerModel.WORLD_HEIGHT
                );
            }
            canvas.setBounds(
                    (availableWidth - canvasWidth) / 2,
                    (availableHeight - canvasHeight) / 2,
                    canvasWidth,
                    canvasHeight
            );
        }
    }
}
