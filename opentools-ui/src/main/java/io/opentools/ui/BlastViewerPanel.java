package io.opentools.ui;

import io.opentools.core.i18n.LanguageManager;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Read-only BLAST overview and pairwise alignment viewer. */
final class BlastViewerPanel extends JPanel {
    private static final Color MATCH = new Color(22, 122, 60);
    private static final Color MISMATCH = new Color(184, 45, 45);
    private static final Color GAP = new Color(194, 106, 20);
    private static final Color AXIS = new Color(92, 99, 104);

    private final List<Alignment> alignments = new ArrayList<>();
    private final Overview overview;
    private final JTextPane detail = new JTextPane();
    private final JLabel status = new JLabel();
    private int selected = -1;

    BlastViewerPanel(LanguageManager language, Path alignmentTsv) throws Exception {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        load(alignmentTsv);
        overview = new Overview();
        overview.setPreferredSize(new Dimension(800, Math.max(90, 50 + alignments.size() * 24)));
        overview.setToolTipText(language.text("blast.viewer.overview_hint",
                "Click a hit to open its pairwise alignment."));
        detail.setEditable(false);
        detail.setContentType("text/plain");
        detail.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 13));
        status.setHorizontalAlignment(SwingConstants.LEFT);
        status.setForeground(new Color(92, 99, 104));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(overview), new JScrollPane(detail));
        split.setResizeWeight(0.38);
        split.setDividerSize(7);
        add(split, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        if (!alignments.isEmpty()) {
            select(0);
        } else {
            detail.setText(language.text("blast.viewer.empty", "No pairwise alignments are available."));
        }
    }

    int alignmentCount() {
        return alignments.size();
    }

    int selectedAlignment() {
        return selected;
    }

    String detailText() {
        return detail.getText();
    }

    void select(int index) {
        if (index < 0 || index >= alignments.size()) {
            return;
        }
        selected = index;
        Alignment alignment = alignments.get(index);
        status.setText("Query " + alignment.query() + " · Subject " + alignment.subject()
                + " · E=" + alignment.evalue() + " · bit=" + alignment.bitScore());
        renderDetail(alignment);
        overview.repaint();
    }

    private void load(Path path) throws Exception {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return;
        }
        int start = lines.get(0).startsWith("query\t") ? 1 : 0;
        for (int index = start; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) continue;
            String[] fields = lines.get(index).split("\t", -1);
            if (fields.length < 12) continue;
            try {
                alignments.add(new Alignment(
                        fields[0], fields[1],
                        Long.parseLong(fields[2]), Long.parseLong(fields[3]),
                        Long.parseLong(fields[4]), Long.parseLong(fields[5]),
                        fields[6], fields[7], fields[8], fields[9], fields[10], fields[11]
                ));
            } catch (NumberFormatException ignored) {
                // Skip a malformed row rather than failing the whole result viewer.
            }
        }
    }

    private void renderDetail(Alignment alignment) {
        StyledDocument document = detail.getStyledDocument();
        try {
            document.remove(0, document.getLength());
            document.insertString(0, alignment.query() + "  vs  " + alignment.subject()
                    + "\nE-value " + alignment.evalue() + "   bit score " + alignment.bitScore()
                    + "   query " + alignment.qStart() + ".." + alignment.qEnd()
                    + "   subject " + alignment.sStart() + ".." + alignment.sEnd() + "\n\n", null);
            String query = alignment.queryAlignment();
            String subject = alignment.subjectAlignment();
            String midline = alignment.midline();
            int width = 60;
            for (int offset = 0; offset < Math.max(query.length(), subject.length()); offset += width) {
                int end = Math.min(offset + width, Math.max(query.length(), subject.length()));
                appendLine(document, "Q " + alignment.qStart() + "  ", slice(query, offset, end), midline, offset, end, true);
                appendLine(document, "   ", slice(midline, offset, end), midline, offset, end, false);
                appendLine(document, "S " + alignment.sStart() + "  ", slice(subject, offset, end), midline, offset, end, false);
                document.insertString(document.getLength(), "\n", null);
            }
        } catch (BadLocationException ignored) {
            detail.setText(alignment.query() + " / " + alignment.subject());
        }
    }

    private void appendLine(
            StyledDocument document,
            String prefix,
            String text,
            String midline,
            int offset,
            int end,
            boolean sequence
    ) throws BadLocationException {
        document.insertString(document.getLength(), prefix, null);
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            char marker = offset + index < midline.length() ? midline.charAt(offset + index) : ' ';
            SimpleAttributeSet style = new SimpleAttributeSet();
            StyleConstants.setForeground(style, colorFor(value, marker, sequence));
            document.insertString(document.getLength(), String.valueOf(value), style);
        }
        document.insertString(document.getLength(), "\n", null);
    }

    private static Color colorFor(char value, char marker, boolean sequence) {
        if (!sequence) return AXIS;
        if (value == '-' || marker == '-') return GAP;
        return marker == '|' ? MATCH : MISMATCH;
    }

    private static String slice(String value, int start, int end) {
        if (value == null) return "";
        return value.substring(Math.min(start, value.length()), Math.min(end, value.length()));
    }

    private final class Overview extends JPanel {
        private static final int LEFT = 92;
        private static final int TOP = 30;
        private static final int ROW = 24;

        private Overview() {
            setBackground(Color.WHITE);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent event) {
                    int index = (event.getY() - TOP) / ROW;
                    if (index >= 0 && index < alignments.size()) {
                        select(index);
                    }
                }
            });
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int width = Math.max(120, getWidth() - LEFT - 24);
                long maximum = alignments.stream()
                        .mapToLong(alignment -> Math.max(alignment.qStart(), alignment.qEnd()))
                        .max().orElse(0);
                g.setColor(AXIS);
                g.drawLine(LEFT, 22, LEFT + width, 22);
                g.setFont(getFont().deriveFont(11f));
                g.drawString("0", LEFT - 4, 16);
                g.drawString(Long.toString(maximum), LEFT + width - 18, 16);
                for (int index = 0; index < alignments.size(); index++) {
                    Alignment alignment = alignments.get(index);
                    int y = TOP + index * ROW;
                    long start = Math.max(0, Math.min(alignment.qStart(), alignment.qEnd()));
                    long end = Math.max(alignment.qStart(), alignment.qEnd());
                    int x1 = LEFT + (int) Math.round(width * start / (double) Math.max(1, maximum));
                    int x2 = LEFT + (int) Math.round(width * end / (double) Math.max(1, maximum));
                    g.setColor(index == selected ? new Color(42, 112, 190) : new Color(126, 148, 166));
                    g.fillRoundRect(x1, y, Math.max(3, x2 - x1), 12, 6, 6);
                    g.setColor(new Color(64, 70, 74));
                    String label = alignment.subject();
                    if (label.length() > 22) label = label.substring(0, 19) + "...";
                    g.drawString(label, 4, y + 11);
                }
            } finally {
                g.dispose();
            }
        }
    }

    private record Alignment(
            String query,
            String subject,
            long qStart,
            long qEnd,
            long sStart,
            long sEnd,
            String evalue,
            String bitScore,
            String alignmentLength,
            String queryAlignment,
            String subjectAlignment,
            String midline
    ) {
    }
}
