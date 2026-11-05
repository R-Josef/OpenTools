package io.opentools.ui;

import io.opentools.core.i18n.LanguageManager;
import io.opentools.formats.sanger.Ab1Reader;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
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

    private final DefaultListModel<ReadItem> model = new DefaultListModel<>();
    private final JList<ReadItem> list = new JList<>(model);
    private final Chromatogram chromatogram = new Chromatogram();
    private final JLabel summary = new JLabel(" ", SwingConstants.LEFT);

    Ab1ViewerPanel(LanguageManager language, List<Path> files) {
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
        JScrollPane scroll = new JScrollPane(chromatogram);
        scroll.getHorizontalScrollBar().setUnitIncrement(18);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, scroll);
        split.setResizeWeight(0);
        split.setDividerSize(7);
        add(split, BorderLayout.CENTER);
        add(summary, BorderLayout.SOUTH);
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        } else {
            summary.setText(language.text("ab1.viewer.empty", "No readable AB1 traces were found."));
        }
    }

    int readCount() { return model.size(); }
    int selectedRead() { return list.getSelectedIndex(); }
    int baseCount() { return chromatogram.trace == null ? 0 : chromatogram.trace.sequence().length(); }

    void selectRead(int index) {
        if (index >= 0 && index < model.size()) list.setSelectedIndex(index);
    }

    private void show(ReadItem item) {
        if (item == null) return;
        chromatogram.setTrace(item.trace());
        summary.setText(item.name() + " · " + item.trace().sequence().length() + " bases · "
                + item.trace().qualities().length + " quality values");
    }

    private record ReadItem(String name, Ab1Reader.Trace trace) {
    }

    private static final class Chromatogram extends JComponent {
        private Ab1Reader.Trace trace;
        private int baseWidth = 8;

        Chromatogram() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(900, 300));
        }

        void setTrace(Ab1Reader.Trace trace) {
            this.trace = trace;
            baseWidth = trace.sequence().length() > 900 ? 6 : 8;
            setPreferredSize(new Dimension(Math.max(900, trace.sequence().length() * baseWidth + 40), 300));
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
                g.setColor(new Color(232, 234, 236));
                g.drawLine(12, height + 10, 12 + width, height + 10);
                for (int channel = 0; channel < channels; channel++) {
                    int[] values = trace.channels()[channel];
                    if (values.length == 0) continue;
                    g.setColor(channelColor(channel));
                    int previousX = 12;
                    int previousY = height + 10;
                    for (int sample = 0; sample < samples; sample++) {
                        int x = 12 + (int) Math.round(width * sample / (double) Math.max(1, samples - 1));
                        int value = values[Math.min(sample, values.length - 1)];
                        int y = height + 10 - (int) Math.round(height * value / (double) maximum);
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
