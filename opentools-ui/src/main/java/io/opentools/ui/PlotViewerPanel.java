package io.opentools.ui;

import io.opentools.core.i18n.LanguageManager;
import io.opentools.visualization.export.PlotExporter;
import io.opentools.visualization.export.PlotFormat;
import io.opentools.visualization.io.PlotJson;
import io.opentools.visualization.model.PlotExport;
import io.opentools.visualization.model.PlotSpec;
import io.opentools.visualization.render.Java2DSceneRenderer;
import io.opentools.visualization.render.PlotSceneFactory;
import io.opentools.visualization.scene.PlotScene;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class PlotViewerPanel extends JPanel {
    private static final double MIN_SCALE = 0.03;
    private static final double MAX_SCALE = 12.0;

    private final LanguageManager language;
    private final PlotCanvas canvas;
    private final JLabel empty = new JLabel();
    private final JButton zoomOut;
    private final JButton zoomIn;
    private final JButton fit;
    private final JButton export;
    private final ArtifactImageCanvas imageCanvas;
    private PlotSpec spec;
    private PlotScene scene;

    PlotViewerPanel(LanguageManager language) {
        super(new BorderLayout(0, 8));
        this.language = language;
        this.imageCanvas = new ArtifactImageCanvas();
        this.canvas = new PlotCanvas();
        UiAccessibility.name(this, t("results.tab.visualization", "Visualization"));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        zoomOut = commandButton("-", t("plot.zoom_out", "Zoom out"));
        zoomIn = commandButton("+", t("plot.zoom_in", "Zoom in"));
        fit = commandButton(t("plot.fit", "Fit"), t("plot.fit_tooltip", "Fit the complete plot in the window"));
        export = commandButton(t("plot.export", "Export plot"), t("plot.export_tooltip", "Export the current plot"));
        controls.add(zoomOut);
        controls.add(zoomIn);
        controls.add(fit);
        controls.add(export);
        add(controls, BorderLayout.NORTH);

        empty.setHorizontalAlignment(JLabel.CENTER);
        empty.setForeground(new Color(102, 110, 116));
        empty.setFont(empty.getFont().deriveFont(Font.PLAIN));
        empty.setText(t("plot.no_plot", "No plot in this result."));
        add(empty, BorderLayout.CENTER);

        zoomOut.addActionListener(ignored -> canvas.zoomBy(0.82));
        zoomIn.addActionListener(ignored -> canvas.zoomBy(1.22));
        fit.addActionListener(ignored -> canvas.fitToWindow());
        export.addActionListener(ignored -> chooseExport());
        setPlot(null, null);
    }

    void load(Path path) throws IOException {
        PlotSpec loaded;
        try {
            loaded = PlotJson.readModel(path).spec();
        } catch (Exception modelFailure) {
            loaded = PlotJson.read(path);
        }
        setPlot(loaded, PlotSceneFactory.create(loaded));
    }

    void loadImage(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IOException("Unsupported image: " + path.getFileName());
        }
        spec = null;
        scene = null;
        imageCanvas.setImage(image);
        removeAll();
        add(imageCanvas, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    void setPlot(PlotSpec nextSpec, PlotScene nextScene) {
        spec = nextSpec;
        scene = nextScene;
        removeAll();
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        controls.add(zoomOut);
        controls.add(zoomIn);
        controls.add(fit);
        controls.add(export);
        add(controls, BorderLayout.NORTH);
        if (scene == null) {
            add(empty, BorderLayout.CENTER);
        } else {
            add(canvas, BorderLayout.CENTER);
            canvas.setScene(scene);
        }
        zoomOut.setEnabled(scene != null);
        zoomIn.setEnabled(scene != null);
        fit.setEnabled(scene != null);
        export.setEnabled(scene != null);
        revalidate();
        repaint();
    }

    void clear() {
        setPlot(null, null);
    }

    void showLoadFailure(String message) {
        clear();
        empty.setText(t("plot.load_failed", "Plot could not be displayed: {0}", message));
    }

    private void chooseExport() {
        if (spec == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        FileNameExtensionFilter png = new FileNameExtensionFilter("PNG", "png");
        FileNameExtensionFilter svg = new FileNameExtensionFilter("SVG", "svg");
        FileNameExtensionFilter pdf = new FileNameExtensionFilter("PDF", "pdf");
        chooser.addChoosableFileFilter(png);
        chooser.addChoosableFileFilter(svg);
        chooser.addChoosableFileFilter(pdf);
        chooser.setFileFilter(png);
        chooser.setSelectedFile(new java.io.File(suggestedBaseName() + ".png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        PlotFormat format = selectedFormat(chooser);
        Path base = stripExtension(chooser.getSelectedFile().toPath());
        Path output = Path.of(base + "." + format.extension());
        if (Files.exists(output)) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    t("plot.overwrite_question", "Replace the existing file?\n{0}", output),
                    t("plot.export", "Export plot"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }
        PlotExport options = chooseExportOptions(format, base.getFileName().toString());
        if (options == null) {
            return;
        }
        PlotSpec exportSpec = spec.withExport(options);
        setExportEnabled(false);
        Thread.ofVirtual().start(() -> {
            try {
                PlotExporter.export(exportSpec, base);
                SwingUtilities.invokeLater(() -> {
                    setExportEnabled(true);
                    JOptionPane.showMessageDialog(
                            this,
                            t("plot.export_success", "Plot exported: {0}", output),
                            t("plot.export", "Export plot"),
                            JOptionPane.INFORMATION_MESSAGE
                    );
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setExportEnabled(true);
                    JOptionPane.showMessageDialog(
                            this,
                            t("plot.export_failed", "Could not export plot: {0}", rootMessage(ex)),
                            t("plot.export", "Export plot"),
                            JOptionPane.ERROR_MESSAGE
                    );
                });
            }
        });
    }

    private PlotExport chooseExportOptions(PlotFormat format, String baseName) {
        JSpinner width = new JSpinner(new SpinnerNumberModel(spec.export().width(), 200, 20000, 50));
        JSpinner height = new JSpinner(new SpinnerNumberModel(spec.export().height(), 200, 20000, 50));
        JSpinner dpi = new JSpinner(new SpinnerNumberModel(spec.export().dpi(), 72, 1200, 1));
        JCheckBox transparent = new JCheckBox(
                t("plot.transparent", "Transparent background"),
                spec.export().transparent()
        );
        JPanel panel = new JPanel(new java.awt.GridLayout(0, 2, 8, 8));
        panel.add(new JLabel(t("plot.width", "Width (px)")));
        panel.add(width);
        panel.add(new JLabel(t("plot.height", "Height (px)")));
        panel.add(height);
        panel.add(new JLabel(t("plot.dpi", "DPI")));
        panel.add(dpi);
        panel.add(new JLabel(""));
        panel.add(transparent);
        int choice = JOptionPane.showConfirmDialog(
                this,
                panel,
                t("plot.export_options", "Export options"),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (choice != JOptionPane.OK_OPTION) {
            return null;
        }
        return new PlotExport(
                List.of(format.name()),
                baseName,
                ((Number) width.getValue()).intValue(),
                ((Number) height.getValue()).intValue(),
                ((Number) dpi.getValue()).intValue(),
                transparent.isSelected(),
                true
        );
    }

    private void setExportEnabled(boolean enabled) {
        export.setEnabled(enabled);
        export.setText(enabled ? t("plot.export", "Export plot") : t("plot.exporting", "Exporting..."));
    }

    private PlotFormat selectedFormat(JFileChooser chooser) {
        String name = chooser.getSelectedFile().getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".svg")) {
            return PlotFormat.SVG;
        }
        if (name.endsWith(".pdf")) {
            return PlotFormat.PDF;
        }
        if (chooser.getFileFilter().getDescription().equalsIgnoreCase("SVG")) {
            return PlotFormat.SVG;
        }
        if (chooser.getFileFilter().getDescription().equalsIgnoreCase("PDF")) {
            return PlotFormat.PDF;
        }
        return PlotFormat.PNG;
    }

    private String suggestedBaseName() {
        return spec == null ? "plot" : spec.export().baseName();
    }

    private static Path stripExtension(Path path) {
        String name = path.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        for (String extension : List.of(".png", ".svg", ".pdf", ".json")) {
            if (lower.endsWith(extension)) {
                String base = name.substring(0, name.length() - extension.length());
                Path parent = path.toAbsolutePath().getParent();
                return parent == null ? Path.of(base) : parent.resolve(base);
            }
        }
        return path.toAbsolutePath();
    }

    private JButton commandButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setFocusable(true);
        return button;
    }

    private String t(String key, String fallback, Object... arguments) {
        return language.text(key, fallback, arguments);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private final class PlotCanvas extends JPanel {
        private PlotScene plot;
        private double scale = 1;
        private double offsetX;
        private double offsetY;
        private boolean autoFit = true;
        private Point dragAnchor;
        private double dragOffsetX;
        private double dragOffsetY;

        private PlotCanvas() {
            setOpaque(true);
            setBackground(new Color(244, 246, 247));
            setMinimumSize(new Dimension(360, 220));
            setPreferredSize(new Dimension(900, 520));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            UiAccessibility.name(this, t("plot.canvas_accessibility", "Interactive plot canvas"));
            UiAccessibility.describe(
                    this,
                    t(
                            "plot.canvas_help",
                            "Use the mouse wheel to zoom, drag to pan, and Fit to reset the view."
                    )
            );
            ToolTipManager.sharedInstance().registerComponent(this);
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    if (plot == null || event.getButton() != MouseEvent.BUTTON1) {
                        return;
                    }
                    dragAnchor = event.getPoint();
                    dragOffsetX = offsetX;
                    dragOffsetY = offsetY;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (dragAnchor == null || plot == null) {
                        return;
                    }
                    offsetX = dragOffsetX + event.getX() - dragAnchor.x;
                    offsetY = dragOffsetY + event.getY() - dragAnchor.y;
                    autoFit = false;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    dragAnchor = null;
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }

                @Override
                public void mouseMoved(MouseEvent event) {
                    ToolTipManager.sharedInstance().mouseMoved(event);
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent event) {
                    if (plot == null) {
                        return;
                    }
                    double factor = Math.pow(1.12, -event.getPreciseWheelRotation());
                    zoomAt(scale * factor, event.getX(), event.getY());
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
        }

        private void setScene(PlotScene nextScene) {
            plot = nextScene;
            autoFit = true;
            fitToWindow();
        }

        private void fitToWindow() {
            if (plot == null || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            double availableWidth = Math.max(1, getWidth() - 24);
            double availableHeight = Math.max(1, getHeight() - 24);
            scale = Math.min(availableWidth / plot.width(), availableHeight / plot.height());
            scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
            offsetX = (getWidth() - plot.width() * scale) / 2;
            offsetY = (getHeight() - plot.height() * scale) / 2;
            autoFit = true;
            repaint();
        }

        private void zoomBy(double factor) {
            zoomAt(scale * factor, getWidth() / 2.0, getHeight() / 2.0);
        }

        private void zoomAt(double requestedScale, double screenX, double screenY) {
            if (plot == null) {
                return;
            }
            double sceneX = (screenX - offsetX) / scale;
            double sceneY = (screenY - offsetY) / scale;
            scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, requestedScale));
            offsetX = screenX - sceneX * scale;
            offsetY = screenY - sceneY * scale;
            autoFit = false;
            repaint();
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            if (plot == null) {
                return null;
            }
            double sceneX = (event.getX() - offsetX) / scale;
            double sceneY = (event.getY() - offsetY) / scale;
            String tooltip = plot.tooltipAt(sceneX, sceneY)
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\n", "<br>");
            return tooltip.isBlank() ? null : "<html>" + tooltip + "</html>";
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (plot == null) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                AffineTransform transform = new AffineTransform();
                transform.translate(offsetX, offsetY);
                transform.scale(scale, scale);
                g.transform(transform);
                Java2DSceneRenderer.paint(g, plot);
                g.setTransform(new AffineTransform());
                g.setColor(new Color(216, 222, 226));
                g.setStroke(new BasicStroke(1));
                g.drawRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 1));
            } finally {
                g.dispose();
            }
        }

        @Override
        public void doLayout() {
            super.doLayout();
            if (autoFit && plot != null) {
                fitToWindow();
            }
        }
    }

    private final class ArtifactImageCanvas extends JPanel {
        private BufferedImage image;

        private ArtifactImageCanvas() {
            setOpaque(true);
            setBackground(new Color(244, 246, 247));
            setPreferredSize(new Dimension(900, 520));
            UiAccessibility.name(this, t("results.tab.visualization", "Visualization"));
        }

        void setImage(BufferedImage image) {
            this.image = image;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (image == null) {
                return;
            }
            double scale = Math.min(
                    (getWidth() - 24.0) / image.getWidth(),
                    (getHeight() - 24.0) / image.getHeight()
            );
            scale = Math.max(0.01, scale);
            int width = (int) Math.round(image.getWidth() * scale);
            int height = (int) Math.round(image.getHeight() * scale);
            int x = (getWidth() - width) / 2;
            int y = (getHeight() - height) / 2;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(
                        java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
                );
                g.drawImage(image, x, y, width, height, null);
            } finally {
                g.dispose();
            }
        }
    }
}
