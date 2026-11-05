package io.opentools.ui;

import io.opentools.formats.sanger.Ab1Reader;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.IntConsumer;

/** Reusable AB1 trace component: drawing, horizontal zoom and base selection. */
final class Ab1ChromatogramPanel extends JPanel {
    private final TraceCanvas canvas = new TraceCanvas();
    private final JScrollPane scroll;

    Ab1ChromatogramPanel() {
        super(new BorderLayout());
        scroll = new JScrollPane(canvas);
        scroll.getHorizontalScrollBar().setUnitIncrement(18);
        add(scroll, BorderLayout.CENTER);
    }

    void setTrace(Ab1Reader.Trace trace) {
        canvas.setTrace(trace);
    }

    void setBaseSelectionListener(IntConsumer listener) {
        canvas.selectionListener = listener == null ? ignored -> { } : listener;
    }

    void selectBase(int index) {
        canvas.selectBase(index);
    }

    int selectedBase() {
        return canvas.selectedBase;
    }

    double zoomLevel() {
        return canvas.zoom;
    }

    double yZoomLevel() {
        return canvas.yZoom;
    }

    void zoomIn() {
        canvas.setZoom(canvas.zoom * 1.25);
    }

    void zoomOut() {
        canvas.setZoom(canvas.zoom / 1.25);
    }

    void fit() {
        canvas.fitViewport(scroll.getViewport().getWidth());
    }

    void yZoomIn() {
        canvas.setYZoom(canvas.yZoom * 1.25);
    }

    void yZoomOut() {
        canvas.setYZoom(canvas.yZoom / 1.25);
    }

    void yFit() {
        canvas.setYZoom(1.0);
    }

    private static final class TraceCanvas extends JComponent {
        private Ab1Reader.Trace trace;
        private double zoom = 1.0;
        private double yZoom = 1.0;
        private int selectedBase = -1;
        private IntConsumer selectionListener = ignored -> { };

        TraceCanvas() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(900, 260));
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                    selectBase(baseAt(event.getX()));
                }
            });
        }

        void setTrace(Ab1Reader.Trace trace) {
            this.trace = trace;
            this.zoom = 1.0;
            this.yZoom = 1.0;
            this.selectedBase = -1;
            updateSize();
        }

        void setZoom(double value) {
            zoom = Math.max(0.2, Math.min(12.0, value));
            updateSize();
        }

        void setYZoom(double value) {
            yZoom = Math.max(0.25, Math.min(8.0, value));
            repaint();
        }

        void fitViewport(int width) {
            if (trace == null) return;
            zoom = Math.max(0.2, Math.min(12.0,
                    Math.max(200, width - 24)
                            / (double) Math.max(1, trace.sequence().length() * 7)));
            updateSize();
        }

        void selectBase(int index) {
            if (trace == null || index < 0 || index >= trace.sequence().length()) return;
            selectedBase = index;
            selectionListener.accept(index);
            repaint();
        }

        private void updateSize() {
            if (trace == null) return;
            setPreferredSize(new Dimension(
                    Math.max(400, (int) Math.round(trace.sequence().length() * 7 * zoom) + 40), 260));
            revalidate();
            repaint();
        }

        private int baseAt(int x) {
            if (trace == null || trace.sequence().isEmpty()) return -1;
            int width = Math.max(120, getWidth() - 24);
            int samples = 0;
            for (int[] channel : trace.channels()) samples = Math.max(samples, channel.length);
            if (samples == 0) samples = Math.max(1, trace.sequence().length());
            int peaks = trace.peakPositions().length;
            int best = -1;
            int distance = Integer.MAX_VALUE;
            for (int index = 0; index < trace.sequence().length(); index++) {
                int position = peaks > index ? trace.peakPositions()[index]
                        : (int) Math.round(index * (samples - 1.0)
                                / Math.max(1, trace.sequence().length() - 1));
                int baseX = 12 + (int) Math.round(width * position / (double) Math.max(1, samples - 1));
                int delta = Math.abs(baseX - x);
                if (delta < distance) {
                    distance = delta;
                    best = index;
                }
            }
            return best;
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (trace == null) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = Math.max(120, getWidth() - 24);
                int height = Math.max(80, getHeight() - 48);
                int samples = 0;
                int maximum = 1;
                for (int[] channel : trace.channels()) {
                    samples = Math.max(samples, channel.length);
                    for (int value : channel) maximum = Math.max(maximum, value);
                }
                if (samples == 0) {
                    g.setColor(new Color(92, 99, 104));
                    g.drawString("No trace channels in this AB1 file.", 12, 24);
                    return;
                }
                int yMaximum = Math.max(1, (int) Math.round(maximum / yZoom));
                g.setColor(new Color(232, 234, 236));
                g.drawLine(12, height + 10, 12 + width, height + 10);
                g.setColor(new Color(120, 126, 130));
                g.setFont(getFont().deriveFont(10f));
                g.drawString(Integer.toString(yMaximum), 2, 13);
                g.drawString("0", 2, height + 10);
                if (selectedBase >= 0 && selectedBase < trace.sequence().length()) {
                    int position = trace.peakPositions().length > selectedBase
                            ? trace.peakPositions()[selectedBase]
                            : (int) Math.round(selectedBase * (samples - 1.0)
                                    / Math.max(1, trace.sequence().length() - 1));
                    int x = 12 + (int) Math.round(width * position / (double) Math.max(1, samples - 1));
                    g.setColor(new Color(255, 214, 120, 90));
                    g.fillRect(Math.max(12, x - 5), 8, 11, getHeight() - 16);
                }
                Color[] colors = {
                        new Color(37, 132, 74), new Color(49, 98, 180),
                        new Color(196, 116, 26), new Color(178, 48, 48)
                };
                for (int channel = 0; channel < trace.channels().length; channel++) {
                    int[] values = trace.channels()[channel];
                    if (values.length == 0) continue;
                    g.setColor(colors[Math.min(channel, colors.length - 1)]);
                    int previousX = 12;
                    int previousY = height + 10;
                    for (int sample = 0; sample < samples; sample++) {
                        int x = 12 + (int) Math.round(width * sample / (double) Math.max(1, samples - 1));
                        int value = values[Math.min(sample, values.length - 1)];
                        int y = Math.max(0, height + 10 - (int) Math.round(
                                height * value / (double) yMaximum));
                        if (sample > 0) g.drawLine(previousX, previousY, x, y);
                        previousX = x;
                        previousY = y;
                    }
                }
                g.setFont(getFont().deriveFont(11f));
                int peaks = trace.peakPositions().length;
                for (int index = 0; index < trace.sequence().length(); index++) {
                    int position = peaks > index ? trace.peakPositions()[index]
                            : (int) Math.round(index * (samples - 1.0)
                                    / Math.max(1, trace.sequence().length() - 1));
                    int x = 12 + (int) Math.round(width * position / (double) Math.max(1, samples - 1));
                    char base = trace.sequence().charAt(index);
                    g.setColor(colors[Math.max(0, Math.min(3, "ACGT".indexOf(base)))]);
                    g.drawString(String.valueOf(base), x - 3, height + 24);
                }
            } finally {
                g.dispose();
            }
        }
    }
}
