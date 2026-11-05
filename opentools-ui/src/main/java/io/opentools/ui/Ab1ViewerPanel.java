package io.opentools.ui;

import io.opentools.core.i18n.LanguageManager;
import io.opentools.formats.sanger.Ab1Reader;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.nio.file.Path;
import java.util.List;

/** AB1 chromatogram viewer with basecalls and quality shading. */
final class Ab1ViewerPanel extends JPanel {
    private static final Color A = new Color(37, 132, 74);
    private static final Color C = new Color(49, 98, 180);
    private static final Color G = new Color(196, 116, 26);
    private static final Color T = new Color(178, 48, 48);

    private final LanguageManager language;
    private final DefaultListModel<ReadItem> model = new DefaultListModel<>();
    private final JList<ReadItem> list = new JList<>(model);
    private final Chromatogram chromatogram = new Chromatogram();
    private final JLabel summary = new JLabel(" ", SwingConstants.LEFT);
    private final JLabel zoomLabel = new JLabel();
    private final JLabel yZoomLabel = new JLabel();
    private JScrollPane scroll;

    Ab1ViewerPanel(LanguageManager language, List<Path> files) {
        this.language = language;
        setLayout(new BorderLayout(10, 6));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        for (Path file : files) {
            try {
                Ab1Reader.Trace trace = Ab1Reader.read(file);
                model.addElement(new ReadItem(trace.name(), trace));
            } catch (Exception ignored) {
                // One malformed trace must not hide the other reads.
            }
        }
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((jlist, value, index, selected, focus) -> {
            JLabel label = new JLabel(value.name() + " · " + value.trace().sequence().length() + " bp");
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));
            if (selected) {
                label.setBackground(jlist.getSelectionBackground());
                label.setForeground(jlist.getSelectionForeground());
            }
            return label;
        });
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) show(list.getSelectedValue());
        });
        JPanel left = new JPanel(new BorderLayout(0, 4));
        left.add(new JLabel(language.text("ab1.viewer.reads", "AB1 reads")), BorderLayout.NORTH);
        left.add(new JScrollPane(list), BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(250, 360));
        scroll = new JScrollPane(chromatogram);
        scroll.getHorizontalScrollBar().setUnitIncrement(18);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton zoomIn = new JButton("+");
        JButton zoomOut = new JButton("−");
        JButton fit = new JButton(language.text("ab1.viewer.fit", "Fit"));
        JButton yIn = new JButton("Y+");
        JButton yOut = new JButton("Y−");
        JButton yFit = new JButton(language.text("ab1.viewer.y_fit", "Y fit"));
        zoomIn.addActionListener(event -> zoomBy(1.5));
        zoomOut.addActionListener(event -> zoomBy(1.0 / 1.5));
        fit.addActionListener(event -> fitWidthInternal());
        yIn.addActionListener(event -> zoomYBy(1.25));
        yOut.addActionListener(event -> zoomYBy(1.0 / 1.25));
        yFit.addActionListener(event -> fitY());
        controls.add(zoomOut);
        controls.add(zoomIn);
        controls.add(fit);
        controls.add(zoomLabel);
        controls.add(yOut);
        controls.add(yIn);
        controls.add(yFit);
        controls.add(yZoomLabel);
        JPanel right = new JPanel(new BorderLayout(0, 4));
        right.add(controls, BorderLayout.NORTH);
        right.add(scroll, BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0);
        split.setDividerSize(7);
        add(split, BorderLayout.CENTER);
        add(summary, BorderLayout.SOUTH);
        chromatogram.addMouseWheelListener(event -> {
            if (!event.isControlDown()) return;
            zoomBy(event.getWheelRotation() < 0 ? 1.5 : 1.0 / 1.5);
            event.consume();
        });
        updateZoomLabel();
        updateYZoomLabel();
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        } else {
            summary.setText(language.text("ab1.viewer.empty", "No readable AB1 traces were found."));
        }
    }

    int readCount() { return model.size(); }
    int selectedRead() { return list.getSelectedIndex(); }
    int baseCount() { return chromatogram.trace == null ? 0 : chromatogram.trace.sequence().length(); }
    int selectedBase() { return chromatogram.selectedBase(); }
    double zoomLevel() { return chromatogram.zoom(); }

    void selectBase(int index) { chromatogram.selectBase(index); }

    void zoomIn() { zoomBy(1.5); }
    void zoomOut() { zoomBy(1.0 / 1.5); }
    void fitWidth() { fitWidthInternal(); }
    void zoomYIn() { zoomYBy(1.25); }
    void zoomYOut() { zoomYBy(1.0 / 1.25); }
    void fitY() { chromatogram.setYZoom(1.0); updateYZoomLabel(); }
    double yZoomLevel() { return chromatogram.yZoom(); }

    void selectRead(int index) {
        if (index >= 0 && index < model.size()) list.setSelectedIndex(index);
    }

    private void show(ReadItem item) {
        if (item == null) return;
        chromatogram.setTrace(item.trace());
        chromatogram.setSelectionListener(index -> {
            if (index < 0 || index >= item.trace().sequence().length()) return;
            int quality = index < item.trace().qualities().length ? item.trace().qualities()[index] : 0;
            int position = index < item.trace().peakPositions().length
                    ? item.trace().peakPositions()[index] : index + 1;
            summary.setText(item.name() + " · base " + (index + 1) + " "
                    + item.trace().sequence().charAt(index) + " · Q" + quality
                    + " · peak " + position);
        });
        summary.setText(item.name() + " · " + item.trace().sequence().length() + " bases · "
                + item.trace().qualities().length + " quality values");
        updateZoomLabel();
    }

    private void zoomBy(double factor) {
        double center = scroll == null || scroll.getHorizontalScrollBar().getMaximum() <= 0
                ? 0 : (scroll.getHorizontalScrollBar().getValue()
                        + scroll.getViewport().getWidth() / 2.0)
                        / scroll.getHorizontalScrollBar().getMaximum();
        chromatogram.setZoom(chromatogram.zoom() * factor);
        if (scroll != null) {
            SwingUtilities.invokeLater(() -> {
                var bar = scroll.getHorizontalScrollBar();
                bar.setValue((int) Math.max(0, Math.min(bar.getMaximum() - bar.getVisibleAmount(),
                        center * bar.getMaximum() - scroll.getViewport().getWidth() / 2.0)));
            });
        }
        updateZoomLabel();
    }

    private void fitWidthInternal() {
        int width = scroll == null ? 900 : Math.max(200, scroll.getViewport().getWidth() - 24);
        chromatogram.fit(width);
        updateZoomLabel();
    }

    private void zoomYBy(double factor) {
        chromatogram.setYZoom(chromatogram.yZoom() * factor);
        updateYZoomLabel();
    }

    private void updateZoomLabel() {
        zoomLabel.setText(language.text("ab1.viewer.zoom", "Zoom {0}%",
                Integer.toString((int) Math.round(chromatogram.zoom() * 100))));
    }

    private void updateYZoomLabel() {
        yZoomLabel.setText(language.text("ab1.viewer.y_zoom", "Y {0}%",
                Integer.toString((int) Math.round(chromatogram.yZoom() * 100))));
    }

    private record ReadItem(String name, Ab1Reader.Trace trace) {
    }

    private static final class Chromatogram extends JComponent {
        private Ab1Reader.Trace trace;
        private int baseWidth = 8;
        private double zoom = 1.0;
        private double yZoom = 1.0;
        private int selectedBase = -1;
        private java.util.function.IntConsumer selectionListener = ignored -> { };

        Chromatogram() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(900, 300));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent event) {
                    selectBase(baseAt(event.getX()));
                }
            });
        }

        int selectedBase() { return selectedBase; }
        void setSelectionListener(java.util.function.IntConsumer listener) {
            selectionListener = listener == null ? ignored -> { } : listener;
        }

        void selectBase(int index) {
            if (trace == null || index < 0 || index >= trace.sequence().length()) return;
            selectedBase = index;
            selectionListener.accept(index);
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
            int bestDistance = Integer.MAX_VALUE;
            for (int index = 0; index < trace.sequence().length(); index++) {
                int position = peaks > index ? trace.peakPositions()[index]
                        : (int) Math.round(index * (samples - 1.0)
                                / Math.max(1, trace.sequence().length() - 1));
                int baseX = 12 + (int) Math.round(width * position / (double) Math.max(1, samples - 1));
                int distance = Math.abs(baseX - x);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = index;
                }
            }
            return best;
        }

        void setTrace(Ab1Reader.Trace trace) {
            this.trace = trace;
            baseWidth = trace.sequence().length() > 900 ? 6 : 8;
            zoom = 1.0;
            yZoom = 1.0;
            selectedBase = -1;
            resizeForZoom();
        }

        double zoom() { return zoom; }
        double yZoom() { return yZoom; }

        void setYZoom(double value) {
            yZoom = Math.max(0.25, Math.min(8.0, value));
            repaint();
        }

        void setZoom(double value) {
            zoom = Math.max(0.2, Math.min(12.0, value));
            resizeForZoom();
        }

        void fit(int availableWidth) {
            if (trace == null) return;
            zoom = Math.max(0.2, Math.min(12.0,
                    availableWidth / (double) Math.max(1, trace.sequence().length() * baseWidth)));
            resizeForZoom();
        }

        private void resizeForZoom() {
            if (trace == null) return;
            int width = (int) Math.round(trace.sequence().length() * baseWidth * zoom) + 40;
            setPreferredSize(new Dimension(Math.max(400, width), 300));
            revalidate();
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (trace == null) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = Math.max(120, getWidth() - 24);
                int height = Math.max(80, getHeight() - 48);
                int channels = trace.channels().length;
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
                for (int channel = 0; channel < channels; channel++) {
                    int[] values = trace.channels()[channel];
                    if (values.length == 0) continue;
                    g.setColor(channelColor(channel));
                    int previousX = 12;
                    int previousY = height + 10;
                    for (int sample = 0; sample < samples; sample++) {
                        int x = 12 + (int) Math.round(width * sample / (double) Math.max(1, samples - 1));
                        int value = values[Math.min(sample, values.length - 1)];
                        int y = height + 10 - (int) Math.round(height * value / (double) yMaximum);
                        y = Math.max(0, y);
                        if (sample > 0) g.drawLine(previousX, previousY, x, y);
                        previousX = x;
                        previousY = y;
                    }
                }
                g.setFont(getFont().deriveFont(11f));
                int peaks = trace.peakPositions().length;
                for (int index = 0; index < trace.sequence().length(); index++) {
                    int position = peaks > index
                            ? trace.peakPositions()[index]
                            : (int) Math.round(index * (samples - 1.0)
                                    / Math.max(1, trace.sequence().length() - 1));
                    int x = 12 + (int) Math.round(width * position / (double) Math.max(1, samples - 1));
                    char base = trace.sequence().charAt(index);
                    g.setColor(channelColor("ACGT".indexOf(base)));
                    g.drawString(String.valueOf(base), x - 3, height + 24);
                    if (index == selectedBase) {
                        g.setColor(new Color(30, 80, 160));
                        g.fillRoundRect(x - 5, height + 15, 10, 12, 4, 4);
                        g.setColor(Color.WHITE);
                        g.drawString(String.valueOf(base), x - 4, height + 24);
                    }
                    int quality = index < trace.qualities().length ? trace.qualities()[index] : 0;
                    g.setColor(quality < 20 ? new Color(190, 80, 50) : new Color(150, 160, 165));
                    g.fillRect(x - 1, height + 29, 3, Math.max(1, Math.min(12, quality / 5)));
                }
            } finally {
                g.dispose();
            }
        }

        private static Color channelColor(int channel) {
            return switch (channel) {
                case 0 -> A;
                case 1 -> C;
                case 2 -> G;
                default -> T;
            };
        }
    }
}
