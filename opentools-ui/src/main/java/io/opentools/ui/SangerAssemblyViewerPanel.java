package io.opentools.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentools.core.i18n.LanguageManager;
import io.opentools.core.job.JobRecord;
import io.opentools.formats.fasta.FastaReader;
import io.opentools.formats.fasta.FastaRecord;
import io.opentools.formats.sanger.Ab1Reader;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reference/read/assembly overview with an embedded AB1 chromatogram. */
final class SangerAssemblyViewerPanel extends JPanel {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final LanguageManager language;
    private final AssemblyCanvas canvas;
    private final Ab1ReferenceCanvas ab1Canvas;
    private JScrollPane topScroll;
    private JScrollPane ab1Scroll;
    private final JLabel selection = new JLabel(" ");
    private final Map<String, Ab1Reader.Trace> traces = new LinkedHashMap<>();
    private final Map<String, String> readPaths = new LinkedHashMap<>();
    private double sharedZoom = 1.0;

    SangerAssemblyViewerPanel(LanguageManager language, JsonNode root, JobRecord job) throws Exception {
        this.language = language;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        Path assembly = artifact(root, "Assembled contigs");
        Path insert = artifact(root, "Vector-trimmed insert");
        Path corrected = artifact(root, "Reference-anchored consensus");
        Path finalConsensus = artifact(root, "Consensus with indels");
        Path readsTable = artifact(root, "Read and placement summary");
        String reference = reference(job);
        String insertSequence = firstSequence(insert);
        String assemblySequence = firstSequence(assembly);
        if (assemblySequence.isBlank()) assemblySequence = insertSequence;
        String consensus = firstSequence(finalConsensus);
        if (consensus.isBlank()) consensus = firstSequence(corrected);
        if (reference.isBlank()) reference = consensus;
        if (assemblySequence.isBlank()) assemblySequence = consensus;
        int assemblyOffset = findAssemblyOffset(assemblySequence, reference, insertSequence);
        List<ReadAlignment> reads = readAlignments(readsTable);
        loadTraces(job);

        canvas = new AssemblyCanvas(reference, consensus, assemblySequence, assemblyOffset, reads);
        ab1Canvas = new Ab1ReferenceCanvas(reference.length(), assemblySequence.length(),
                assemblyOffset, assemblySequence, reads);
        canvas.setSelectionListener((read, coordinate) -> {
            if (read != null) {
                Ab1Reader.Trace trace = traces.get(read);
                ReadAlignment alignment = canvas.alignment(read);
                ab1Canvas.setTrace(trace, alignment);
                if (trace != null) {
                    selection.setText(language.text("assembly.selected_read",
                            "Read: {0} · reference position {1} · AB1 trace", read, coordinate + 1));
                } else {
                    selection.setText(language.text("assembly.selected_read_no_trace",
                            "Read: {0} · reference position {1} · no AB1 trace", read, coordinate + 1));
                }
            } else if (coordinate >= 0) {
                selection.setText(language.text("assembly.position",
                        "Reference position {0}", coordinate + 1));
            }
        });

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton zoomOut = new JButton("−");
        JButton zoomIn = new JButton("+");
        JButton fit = new JButton(language.text("assembly.fit", "Fit"));
        JButton yOut = new JButton("Y−");
        JButton yIn = new JButton("Y+");
        JButton yFit = new JButton(language.text("assembly.y_fit", "Y fit"));
        zoomOut.addActionListener(event -> zoomShared(1 / 1.3));
        zoomIn.addActionListener(event -> zoomShared(1.3));
        fit.addActionListener(event -> fitShared());
        yOut.addActionListener(event -> {
            ab1Canvas.yZoomBy(1 / 1.25);
            updateAb1ScrollPolicy();
        });
        yIn.addActionListener(event -> {
            ab1Canvas.yZoomBy(1.25);
            updateAb1ScrollPolicy();
        });
        yFit.addActionListener(event -> {
            ab1Canvas.yFit();
            updateAb1ScrollPolicy();
        });
        controls.add(zoomOut);
        controls.add(zoomIn);
        controls.add(fit);
        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.add(new JLabel(language.text("assembly.title",
                "Reference, reads, assembly and AB1")), BorderLayout.WEST);
        top.add(controls, BorderLayout.EAST);

        JPanel ab1Controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        ab1Controls.add(new JLabel(language.text("assembly.ab1", "AB1")));
        JButton ab1Out = new JButton("−");
        JButton ab1In = new JButton("+");
        JButton ab1Fit = new JButton(language.text("assembly.fit", "Fit"));
        ab1Out.addActionListener(event -> zoomShared(1 / 1.3));
        ab1In.addActionListener(event -> zoomShared(1.3));
        ab1Fit.addActionListener(event -> fitShared());
        ab1Controls.add(ab1Out);
        ab1Controls.add(ab1In);
        ab1Controls.add(ab1Fit);
        ab1Controls.add(yOut);
        ab1Controls.add(yIn);
        ab1Controls.add(yFit);

        topScroll = new JScrollPane(canvas);
        topScroll.setRowHeaderView(new TrackHeader(canvas));
        topScroll.getHorizontalScrollBar().setUnitIncrement(20);
        topScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        ab1Scroll = new JScrollPane(ab1Canvas);
        ab1Scroll.setRowHeaderView(new Ab1Header(ab1Canvas));
        ab1Scroll.getHorizontalScrollBar().setUnitIncrement(20);
        ab1Scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        ab1Scroll.getHorizontalScrollBar().setModel(topScroll.getHorizontalScrollBar().getModel());
        JPanel topSection = new JPanel(new BorderLayout(0, 4));
        topSection.add(top, BorderLayout.NORTH);
        topSection.add(topScroll, BorderLayout.CENTER);
        JPanel ab1Section = new JPanel(new BorderLayout(0, 4));
        ab1Section.add(ab1Controls, BorderLayout.NORTH);
        ab1Section.add(ab1Scroll, BorderLayout.CENTER);
        ab1Section.setPreferredSize(new Dimension(0, 170));
        JPanel bottom = new JPanel(new BorderLayout(0, 2));
        bottom.add(ab1Section, BorderLayout.CENTER);
        bottom.add(selection, BorderLayout.SOUTH);
        add(topSection, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        if (!reads.isEmpty()) {
            canvas.selectRead(0);
            SwingUtilities.invokeLater(() -> canvas.selectRead(0));
        }
    }

    private void updateAb1ScrollPolicy() {
        if (ab1Scroll != null) {
            ab1Scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        }
        if (topScroll != null) {
            topScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        }
    }

    private void zoomShared(double factor) {
        sharedZoom = Math.max(0.2, Math.min(12.0, sharedZoom * factor));
        canvas.setZoom(sharedZoom);
        ab1Canvas.setZoom(sharedZoom);
    }

    private void fitShared() {
        int available = Math.max(200, getWidth() - 140);
        double fit = available / (double) Math.max(1, Math.max(1, canvas.referenceLength()) * 7);
        sharedZoom = Math.max(0.2, Math.min(12.0, fit));
        canvas.setZoom(sharedZoom);
        ab1Canvas.setZoom(sharedZoom);
    }

    int readCount() { return canvas.readCount(); }
    int selectedRead() { return canvas.selectedRead(); }

    private static Path artifact(JsonNode root, String title) {
        for (JsonNode artifact : root.path("artifacts")) {
            if (artifact.path("title").asText("").equalsIgnoreCase(title)) {
                String path = artifact.path("path").asText("");
                if (!path.isBlank() && Files.isRegularFile(Path.of(path))) return Path.of(path);
            }
        }
        return null;
    }

    private static String reference(JobRecord job) throws Exception {
        if (job.paramsJson() == null) return "";
        JsonNode params = JSON.readTree(job.paramsJson());
        String value = params.path("reference").asText("");
        if (value.isBlank() || !Files.isRegularFile(Path.of(value))) return "";
        return firstSequence(Path.of(value));
    }

    private static int findAssemblyOffset(String assembly, String reference, String insert) {
        if (assembly == null || reference == null || reference.isBlank()) return 0;
        int exact = assembly.indexOf(reference);
        if (exact >= 0) return exact;
        for (int length = 60; length >= 15; length -= 5) {
            if (reference.length() < length) continue;
            int position = assembly.indexOf(reference.substring(0, length));
            if (position >= 0) return position;
        }
        if (insert != null && !insert.isBlank()) {
            int position = assembly.indexOf(insert);
            if (position >= 0) return position;
            for (int length = 40; length >= 15; length -= 5) {
                if (insert.length() < length) continue;
                position = assembly.indexOf(insert.substring(0, length));
                if (position >= 0) return position;
            }
        }
        return 0;
    }

    private static String reverseComplement(String sequence) {
        StringBuilder result = new StringBuilder(sequence.length());
        for (int index = sequence.length() - 1; index >= 0; index--) {
            result.append(switch (sequence.charAt(index)) {
                case 'A' -> 'T';
                case 'C' -> 'G';
                case 'G' -> 'C';
                case 'T' -> 'A';
                default -> 'N';
            });
        }
        return result.toString();
    }

    private static String firstSequence(Path path) throws Exception {
        if (path == null) return "";
        try (FastaReader reader = new FastaReader(path)) {
            FastaRecord record = reader.next();
            return record == null ? "" : record.sequence().toUpperCase(Locale.ROOT);
        }
    }

    private void loadTraces(JobRecord job) throws Exception {
        if (job.paramsJson() == null) return;
        JsonNode params = JSON.readTree(job.paramsJson());
        JsonNode reads = params.path("reads");
        List<Path> files = new ArrayList<>();
        if (reads.isArray()) {
            reads.forEach(node -> files.add(Path.of(node.asText(""))));
        } else if (reads.isTextual()) {
            for (String value : reads.asText("").split(",")) {
                files.add(Path.of(value.trim()));
            }
        }
        for (Path file : files) {
            if (file.getFileName() == null
                    || !file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ab1")
                    || !Files.isRegularFile(file)) continue;
            Ab1Reader.Trace trace = Ab1Reader.read(file);
            traces.put(trace.name(), trace);
            readPaths.put(trace.name(), file.toString());
        }
    }

    private static List<ReadAlignment> readAlignments(Path path) throws Exception {
        if (path == null) return List.of();
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty()) return List.of();
        String[] header = lines.getFirst().split("\t", -1);
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < header.length; i++) index.put(header[i], i);
        List<ReadAlignment> reads = new ArrayList<>();
        for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
            String[] fields = lines.get(lineIndex).split("\t", -1);
            String start = field(fields, index, "reference_start");
            String end = field(fields, index, "reference_end");
            if (start.isBlank() || end.isBlank()) continue;
            try {
                reads.add(new ReadAlignment(
                        field(fields, index, "read"),
                        Integer.parseInt(start),
                        Integer.parseInt(end),
                        integer(fields, index, "trim_start"),
                        integer(fields, index, "trim_end"),
                        field(fields, index, "cigar"),
                        field(fields, index, "orientation"),
                        field(fields, index, "identity")
                ));
            } catch (NumberFormatException ignored) {
                // Ignore malformed rows.
            }
        }
        return reads;
    }

    private static String field(String[] fields, Map<String, Integer> index, String name) {
        Integer position = index.get(name);
        return position == null || position >= fields.length ? "" : fields[position];
    }

    private static int integer(String[] fields, Map<String, Integer> index, String name) {
        String value = field(fields, index, name);
        try {
            return value.isBlank() ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private record ReadAlignment(
            String name,
            int start,
            int end,
            int trimStart,
            int trimEnd,
            String cigar,
            String orientation,
            String identity
    ) {
    }

    private static final class AssemblyCanvas extends JPanel {
        private final String reference;
        private final String consensus;
        private final String assembly;
        private final List<ReadAlignment> reads;
        private final int[] coverage;
        private final int assemblyOffset;
        private final int rowHeight = 22;
        private final int top = 100;
        private long selectedCoordinate = -1;
        private int selected = -1;
        private double zoom = 1.0;
        private double yZoom = 1.0;
        private Ab1Reader.Trace trace;
        private ReadAlignment traceRead;
        private SelectionListener listener = (read, coordinate) -> { };

        AssemblyCanvas(String reference, String consensus, String assembly, int assemblyOffset,
                       List<ReadAlignment> reads) {
            this.reference = reference;
            this.consensus = consensus;
            this.assembly = assembly;
            this.assemblyOffset = Math.max(0, assemblyOffset);
            this.reads = reads;
            this.coverage = coverage(reference.length(), reads);
            setBackground(Color.WHITE);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent event) {
                    int row = (event.getY() - top) / rowHeight;
                    int traceTop = top + Math.max(3, reads.size()) * rowHeight + 14;
                    if (event.getY() >= traceTop && trace != null && traceRead != null) {
                        int coordinate = coordinateAt(event.getX());
                        selectedCoordinate = coordinate;
                        listener.selected(traceRead.name(), coordinate);
                        repaint();
                    } else if (row >= 0 && row < reads.size()) {
                        selectRead(row);
                        int coordinate = coordinateAt(event.getX());
                        listener.selected(reads.get(row).name(), coordinate);
                    } else {
                        listener.selected(null, coordinateAt(event.getX()));
                    }
                }
            });
            resize();
        }

        void setSelectionListener(SelectionListener listener) {
            this.listener = listener == null ? (read, coordinate) -> { } : listener;
        }

        int readCount() { return reads.size(); }
        String readName(int index) {
            return index >= 0 && index < reads.size() ? reads.get(index).name() : "";
        }
        int referenceLength() { return reference.length(); }
        int selectedRead() { return selected; }

        void setZoom(double value) {
            zoom = Math.max(0.2, Math.min(12.0, value));
            resize();
        }
        String selectedReadName() {
            return selected >= 0 && selected < reads.size() ? reads.get(selected).name() : null;
        }

        ReadAlignment alignment(String name) {
            for (ReadAlignment read : reads) if (read.name().equals(name)) return read;
            return null;
        }

        void setTrace(Ab1Reader.Trace trace, ReadAlignment read) {
            this.trace = trace;
            this.traceRead = read;
            repaint();
        }

        void yZoomBy(double factor) {
            yZoom = Math.max(0.25, Math.min(8.0, yZoom * factor));
            repaint();
        }

        void yFit() {
            yZoom = 1.0;
            repaint();
        }

        void setCoordinate(int coordinate) {
            selectedCoordinate = Math.max(0, Math.min(reference.length() - 1, coordinate));
            repaint();
        }

        int referencePosition(String readName, int readBase) {
            for (ReadAlignment read : reads) {
                if (!read.name().equals(readName)) continue;
                int position = read.start();
                int consumed = 0;
                for (String part : read.cigar().split("(?<=\\D)|(?=\\d)")) {
                    if (part.isEmpty()) continue;
                    char operation = part.charAt(part.length() - 1);
                    int count;
                    try {
                        count = Integer.parseInt(part.substring(0, part.length() - 1));
                    } catch (NumberFormatException ex) {
                        continue;
                    }
                    if (operation == 'M' || operation == 'I') {
                        if (readBase < consumed + count) {
                            return position + (readBase - consumed);
                        }
                        consumed += count;
                        if (operation == 'M') position += count;
                    } else if (operation == 'D') {
                        position += count;
                    }
                }
                return position;
            }
            return -1;
        }

        void selectRead(int index) {
            if (index < 0 || index >= reads.size()) return;
            selected = index;
            selectedCoordinate = reads.get(index).start();
            listener.selected(reads.get(index).name(), (int) selectedCoordinate);
            repaint();
        }

        void zoomBy(double factor) {
            zoom = Math.max(0.2, Math.min(12.0, zoom * factor));
            resize();
        }

        void fit() {
            int width = getParent() == null ? 900 : getParent().getWidth();
            int length = Math.max(1, reference.length());
            zoom = Math.max(0.2, Math.min(12.0, Math.max(200, width - 100) / (length * 7.0)));
            resize();
        }

        private void resize() {
            int width = Math.max(600,
                    (int) Math.round(Math.max(1, assembly.length()) * 7 * zoom) + 100);
            setPreferredSize(new Dimension(width,
                    top + Math.max(3, reads.size()) * rowHeight + 20));
            revalidate();
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int left = 8;
                double pixelsPerBase = 7 * zoom;
                int referenceLeft = left + (int) Math.round(assemblyOffset * pixelsPerBase);
                int width = Math.max(120,
                        (int) Math.round(Math.max(1, reference.length()) * pixelsPerBase));
                int length = Math.max(1, reference.length());
                g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
                drawTrack(g, "Reference", reference, 20, referenceLeft, width, new Color(80, 120, 150), false);
                drawTrack(g, "Consensus", consensus, 45, referenceLeft, width, new Color(36, 139, 135), false);
                drawTrack(g, "Assembly", assembly, 70, left, width, new Color(190, 130, 40), true);
                for (int index = 0; index < reads.size(); index++) {
                    ReadAlignment read = reads.get(index);
                    int y = top + index * rowHeight;
                    boolean reverse = read.orientation().startsWith("reverse");
                    int assemblyStart = reverse ? assemblyOffset + read.start() : read.start();
                    int assemblyEnd = assemblyStart + Math.max(1, read.trimEnd() - read.trimStart());
                    int x1 = left + (int) Math.round(Math.max(0, assemblyStart) * 7 * zoom);
                    int x2 = left + (int) Math.round(Math.min(assembly.length(), assemblyEnd) * 7 * zoom);
                    g.setColor(index == selected ? new Color(42, 112, 190) : new Color(150, 168, 180));
                    g.fillRoundRect(x1, y, Math.max(3, x2 - x1), 13, 5, 5);
                    g.setColor(new Color(55, 65, 72));
                    g.drawString(read.orientation().startsWith("reverse") ? "<" : ">", Math.max(x1, x2 - 8), y + 11);
                }
                if (selectedCoordinate >= 0) {
                    int x = referenceLeft + (int) Math.round(width * selectedCoordinate / (double) length);
                    g.setColor(new Color(255, 190, 80, 150));
                    g.fillRect(x - 1, 8, 3, getHeight() - 16);
                }
            } finally {
                g.dispose();
            }
        }

        private void drawTrack(Graphics2D g, String label, String sequence, int y,
                               int left, int width, Color color, boolean assemblyTrack) {
            if (sequence == null || sequence.isEmpty()) return;
            int sequenceLength = Math.max(1, sequence.length());
            int referenceLength = Math.max(1, reference.length());
            g.setColor(color);
            for (int index = 0; index < sequenceLength; index++) {
                int x = assemblyTrack
                        ? left + (int) Math.round(index * 7 * zoom)
                        : left + (int) Math.round(width * index / (double) referenceLength);
                int gridIndex = assemblyTrack ? index : assemblyOffset + index;
                if (gridIndex % 10 == 0) g.drawLine(x, y, x, y + 18);
            }
            if (width / (double) referenceLength >= 4.5) {
                g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
                for (int index = 0; index < sequenceLength; index++) {
                    int x = assemblyTrack
                            ? left + (int) Math.round(index * 7 * zoom)
                            : left + (int) Math.round(width * index / (double) referenceLength);
                    char base = sequence.charAt(index);
                    g.setColor(switch (base) {
                        case 'A' -> new Color(37, 132, 74);
                        case 'C' -> new Color(49, 98, 180);
                        case 'G' -> new Color(196, 116, 26);
                        case 'T' -> new Color(178, 48, 48);
                        default -> new Color(120, 126, 130);
                    });
                    g.drawString(String.valueOf(base), x, y + 18);
                }
            }
            g.setColor(new Color(210, 216, 220));
            g.drawLine(left, y + 18, left + width, y + 18);
        }

        private void drawTrace(Graphics2D g, int left, int width, int length) {
            int traceTop = top + Math.max(3, reads.size()) * rowHeight + 14;
            g.setColor(new Color(55, 65, 72));
            g.drawString("AB1", 4, traceTop + 12);
            if (trace == null || traceRead == null) {
                g.setColor(new Color(120, 126, 130));
                g.drawString("No AB1 trace for the selected read.", left, traceTop + 24);
                return;
            }
            int samples = 0;
            int maximum = 1;
            for (int[] channel : trace.channels()) {
                samples = Math.max(samples, channel.length);
                for (int value : channel) maximum = Math.max(maximum, value);
            }
            if (samples == 0) {
                g.drawString("No trace channels.", left, traceTop + 24);
                return;
            }
            int traceHeight = Math.max(80, getHeight() - traceTop - 42);
            int yMaximum = Math.max(1, (int) Math.round(maximum / yZoom));
            g.setColor(new Color(230, 233, 235));
            g.drawLine(left, traceTop + traceHeight, left + width, traceTop + traceHeight);
            Color[] colors = {
                    new Color(37, 132, 74), new Color(49, 98, 180),
                    new Color(196, 116, 26), new Color(178, 48, 48)
            };
            int readLength = Math.max(1, trace.sequence().length());
            for (int channel = 0; channel < trace.channels().length; channel++) {
                int[] values = trace.channels()[channel];
                if (values.length == 0) continue;
                g.setColor(colors[Math.min(channel, colors.length - 1)]);
                int previousX = -1;
                int previousY = -1;
                for (int sample = 0; sample < samples; sample++) {
                    int baseIndex = (int) Math.round(sample * (readLength - 1.0)
                            / Math.max(1, samples - 1));
                    int alignedIndex = traceRead.orientation().startsWith("reverse")
                            ? readLength - 1 - baseIndex : baseIndex;
                    int reference = referencePosition(traceRead.name(), alignedIndex);
                    if (reference < 0) continue;
                    int x = left + (int) Math.round(width * reference / (double) length);
                    int y = Math.max(0, traceTop + traceHeight
                            - (int) Math.round(traceHeight * values[Math.min(sample, values.length - 1)]
                                    / (double) yMaximum));
                    if (previousX >= 0) g.drawLine(previousX, previousY, x, y);
                    previousX = x;
                    previousY = y;
                }
            }
            g.setFont(getFont().deriveFont(10f));
            int peaks = trace.peakPositions().length;
            for (int index = 0; index < trace.sequence().length(); index++) {
                int alignedIndex = traceRead.orientation().startsWith("reverse")
                        ? trace.sequence().length() - 1 - index : index;
                int reference = referencePosition(traceRead.name(), alignedIndex);
                if (reference < 0) continue;
                int x = left + (int) Math.round(width * reference / (double) length);
                char base = trace.sequence().charAt(index);
                g.setColor(colors[Math.max(0, Math.min(3, "ACGT".indexOf(base)))]);
                g.drawString(String.valueOf(base), x - 3, traceTop + traceHeight + 15);
            }
        }

        private void drawCoverage(Graphics2D g, int left, int width, int y, int length) {
            int max = 1;
            for (int value : coverage) max = Math.max(max, value);
            g.setColor(new Color(120, 170, 190));
            for (int index = 0; index < coverage.length; index++) {
                int x = left + (int) Math.round(width * index / (double) Math.max(1, length));
                int height = (int) Math.round(18.0 * coverage[index] / max);
                g.drawLine(x, y + 18, x, y + 18 - height);
            }
            g.setColor(new Color(90, 100, 105));
        }

        private int coordinateAt(int x) {
            int left = 8;
            int width = Math.max(120, getWidth() - left - 16);
            return Math.max(0, Math.min(reference.length() - 1,
                    (int) Math.round((x - left) * (double) Math.max(1, reference.length()) / width)));
        }

        private static int[] coverage(int length, List<ReadAlignment> reads) {
            int[] result = new int[Math.max(1, length)];
            for (ReadAlignment read : reads) {
                int position = read.start();
                for (String part : read.cigar().split("(?<=\\D)|(?=\\d)")) {
                    if (part.isEmpty()) continue;
                    char operation = part.charAt(part.length() - 1);
                    int count;
                    try {
                        count = Integer.parseInt(part.substring(0, part.length() - 1));
                    } catch (NumberFormatException ex) {
                        continue;
                    }
                    if (operation == 'M' || operation == 'D') {
                        for (int index = 0; index < count && position < result.length; index++, position++) {
                            result[position]++;
                        }
                    } else if (operation == 'I') {
                        // insertions do not consume reference coordinates
                    } else {
                        position += count;
                    }
                }
            }
            return result;
        }
    }

    private static final class Ab1ReferenceCanvas extends JPanel {
        private final int referenceLength;
        private final int gridLength;
        private final int assemblyOffset;
        private final String assembly;
        private final List<ReadAlignment> reads;
        private int traceStart = -1;
        private Ab1Reader.Trace trace;
        private ReadAlignment traceRead;
        private int traceOffset;
        private double zoom = 1.0;
        private double yZoom = 1.0;

        Ab1ReferenceCanvas(int referenceLength, int gridLength, int assemblyOffset,
                           String assembly, List<ReadAlignment> reads) {
            this.referenceLength = Math.max(1, referenceLength);
            this.gridLength = Math.max(1, gridLength);
            this.assemblyOffset = Math.max(0, assemblyOffset);
            this.assembly = assembly == null ? "" : assembly;
            this.reads = reads;
            setBackground(Color.WHITE);
            resize();
        }

        void setTrace(Ab1Reader.Trace trace, ReadAlignment read) {
            this.trace = trace;
            this.traceRead = read;
            this.traceStart = locateTrace(trace, read);
            this.traceOffset = calculateTraceOffset(trace);
            repaint();
        }

        private int calculateTraceOffset(Ab1Reader.Trace trace) {
            if (trace == null || trace.peakPositions().length == 0) return 0;
            int samples = 0;
            int maximum = 1;
            for (int[] channel : trace.channels()) {
                samples = Math.max(samples, channel.length);
                for (int value : channel) maximum = Math.max(maximum, value);
            }
            if (samples == 0) return 0;
            int threshold = Math.max(1, maximum / 10);
            int firstSignal = 0;
            for (int sample = 0; sample < samples; sample++) {
                boolean signal = false;
                for (int[] channel : trace.channels()) {
                    if (sample < channel.length && channel[sample] >= threshold) {
                        signal = true;
                        break;
                    }
                }
                if (signal) {
                    firstSignal = sample;
                    break;
                }
            }
            int offset = firstSignal - trace.peakPositions()[0];
            return offset > 100 ? offset : 0;
        }

        private int locateTrace(Ab1Reader.Trace trace, ReadAlignment read) {
            if (trace == null || read == null || assembly.isEmpty()) return -1;
            int length = Math.max(1, trace.sequence().length());
            int start = Math.max(0, Math.min(length - 1, read.trimStart()));
            int end = Math.max(start + 1, Math.min(length, read.trimEnd()));
            String trimmed = trace.sequence().substring(start, end);
            String display = read.orientation().startsWith("reverse")
                    ? reverseComplement(trimmed) : trimmed;
            if (read.orientation().startsWith("reverse")) {
                return Math.max(0, Math.min(assembly.length() - trimmed.length(),
                        assemblyOffset + read.start()));
            }
            int probe = Math.min(40, display.length());
            if (probe > 0) {
                int position = assembly.indexOf(display.substring(0, probe));
                if (position >= 0) return position;
            }
            return -1;
        }

        void setZoom(double value) {
            zoom = Math.max(0.2, Math.min(12.0, value));
            resize();
        }

        void yZoomBy(double factor) {
            yZoom = Math.max(1.0, Math.min(8.0, yZoom * factor));
            resize();
        }

        void yFit() {
            yZoom = 1.0;
            resize();
        }

        double yZoomLevel() {
            return yZoom;
        }

        private void resize() {
            int width = Math.max(600, (int) Math.round(gridLength * 7 * zoom) + 100);
            int height = (int) Math.round(105 * yZoom);
            setPreferredSize(new Dimension(width, height));
            revalidate();
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int left = 8;
                int referenceLeft = left + (int) Math.round(assemblyOffset * 7 * zoom);
                int width = Math.max(120, (int) Math.round(referenceLength * 7 * zoom));
                int height = Math.max(60, getHeight() - 32);
                if (trace == null || traceRead == null) {
                    g.setColor(new Color(120, 126, 130));
                    g.drawString("No AB1 trace for the selected read.", left, 30);
                    return;
                }
                int samples = 0;
                for (int[] channel : trace.channels()) {
                    samples = Math.max(samples, channel.length);
                }
                if (samples == 0) {
                    g.drawString("No trace channels.", left, 30);
                    return;
                }
                g.setColor(new Color(230, 233, 235));
                g.drawLine(left, height, left + width, height);
                Color[] colors = {
                        new Color(37, 132, 74), new Color(49, 98, 180),
                        new Color(196, 116, 26), new Color(178, 48, 48)
                };
                int traceLength = Math.max(1, trace.sequence().length());
                int trimStart = Math.max(0, Math.min(traceLength - 1, traceRead.trimStart()));
                int trimEnd = Math.max(trimStart + 1, Math.min(traceLength, traceRead.trimEnd()));
                int trimmedLength = trimEnd - trimStart;
                int sampleStart = sampleForBase(trimStart, samples, traceLength);
                int sampleEnd = sampleForBase(trimEnd - 1, samples, traceLength);
                int maximum = 1;
                java.util.List<Integer> sampledValues = new java.util.ArrayList<>();
                for (int[] channel : trace.channels()) {
                    int end = Math.min(channel.length, sampleEnd + 1);
                    for (int sample = sampleStart; sample < end; sample++) {
                        sampledValues.add(channel[sample]);
                    }
                }
                if (!sampledValues.isEmpty()) {
                    java.util.Collections.sort(sampledValues);
                    maximum = Math.max(1, sampledValues.get(
                            (int) Math.floor((sampledValues.size() - 1) * 0.95)));
                }
                boolean reverse = traceRead.orientation().startsWith("reverse");
                int[] reverseColors = {3, 2, 1, 0};
                for (int channel = 0; channel < trace.channels().length; channel++) {
                    int[] values = trace.channels()[channel];
                    if (values.length == 0) continue;
                    int colorIndex = reverse
                            ? reverseColors[Math.min(channel, reverseColors.length - 1)]
                            : Math.min(channel, colors.length - 1);
                    g.setColor(colors[colorIndex]);
                    int previousX = -1;
                    int previousY = -1;
                    for (int step = 0; step < trimmedLength; step++) {
                        int sourceBase = trimStart + (reverse ? trimmedLength - 1 - step : step);
                        int sample = sampleForBase(sourceBase, samples, traceLength);
                        int reference = traceRead.start() + (int) Math.round(
                                (traceRead.end() - traceRead.start()) * step
                                        / (double) Math.max(1, trimmedLength - 1));
                        if (reference < 0) continue;
                        int x = traceStart >= 0
                                ? left + (int) Math.round((traceStart + step) * 7 * zoom)
                                : referenceLeft + (int) Math.round(width * reference
                                        / (double) Math.max(1, referenceLength - 1));
                        int y = Math.max(0, height - (int) Math.round(
                                height * values[Math.max(0, Math.min(values.length - 1, sample))]
                                        / (double) maximum));
                        if (previousX >= 0) g.drawLine(previousX, previousY, x, y);
                        previousX = x;
                        previousY = y;
                    }
                }
                Font baseFont = new Font(Font.MONOSPACED, Font.PLAIN, 10);
                g.setFont(baseFont);
                FontMetrics metrics = g.getFontMetrics(baseFont);
                for (int step = 0; step < trimmedLength; step++) {
                    int source = trimStart + (reverse ? trimmedLength - 1 - step : step);
                    int reference = traceRead.start() + (int) Math.round(
                            (traceRead.end() - traceRead.start()) * step
                                    / (double) Math.max(1, trimmedLength - 1));
                    if (reference < 0) continue;
                    int x = traceStart >= 0
                            ? left + (int) Math.round((traceStart + step) * 7 * zoom)
                            : referenceLeft + (int) Math.round(width * reference
                                    / (double) Math.max(1, referenceLength - 1));
                    char base = trace.sequence().charAt(source);
                    if (reverse) base = complement(base);
                    g.setColor(colors[Math.max(0, Math.min(3, "ACGT".indexOf(base)))]);
                    g.drawString(String.valueOf(base), x - metrics.charWidth(base) / 2,
                            height + 14);
                }
            } finally {
                g.dispose();
            }
        }

        private int sampleForBase(int baseIndex, int samples, int traceLength) {
            if (trace != null && trace.peakPositions().length > baseIndex && baseIndex >= 0) {
                return Math.max(0, Math.min(samples - 1,
                        trace.peakPositions()[baseIndex] + traceOffset));
            }
            return Math.max(0, Math.min(samples - 1,
                    (int) Math.round(baseIndex * (samples - 1.0) / Math.max(1, traceLength - 1))));
        }

        private static char complement(char base) {
            return switch (base) {
                case 'A' -> 'T';
                case 'C' -> 'G';
                case 'G' -> 'C';
                case 'T' -> 'A';
                default -> 'N';
            };
        }

        private int referencePosition(int readBase) {
            if (traceRead == null) return -1;
            int position = traceRead.start();
            int consumed = 0;
            for (String part : traceRead.cigar().split("(?<=\\D)|(?=\\d)")) {
                if (part.isEmpty()) continue;
                char operation = part.charAt(part.length() - 1);
                int count;
                try {
                    count = Integer.parseInt(part.substring(0, part.length() - 1));
                } catch (NumberFormatException ex) {
                    continue;
                }
                if (operation == 'M' || operation == 'I') {
                    if (readBase < consumed + count) {
                        return position + (readBase - consumed);
                    }
                    consumed += count;
                    if (operation == 'M') position += count;
                } else if (operation == 'D') {
                    position += count;
                }
            }
            return position;
        }
    }

    private static final class TrackHeader extends JComponent {
        private final AssemblyCanvas canvas;

        TrackHeader(AssemblyCanvas canvas) {
            this.canvas = canvas;
            setOpaque(true);
            setBackground(Color.WHITE);
            ToolTipManager.sharedInstance().registerComponent(this);
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(130, canvas.getPreferredSize().height);
        }

        @Override public String getToolTipText(MouseEvent event) {
            int index = readAt(event.getY());
            return index < 0 ? null : canvas.readName(index);
        }

        private int readAt(int y) {
            int row = (y - 100) / 22;
            return row >= 0 && row < canvas.readCount() ? row : -1;
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(55, 65, 72));
                g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
                FontMetrics metrics = g.getFontMetrics();
                drawName(g, metrics, "Reference", 20);
                drawName(g, metrics, "Consensus", 45);
                drawName(g, metrics, "Assembly", 70);
                for (int index = 0; index < canvas.readCount(); index++) {
                    drawName(g, metrics, canvas.readName(index), 100 + index * 22 + 11);
                }
            } finally {
                g.dispose();
            }
        }

        private void drawName(Graphics2D g, FontMetrics metrics, String name, int y) {
            if (name == null || name.isEmpty()) return;
            int max = Math.max(20, getWidth() - 8);
            String text = name;
            if (metrics.stringWidth(text) > max) {
                int end = text.length();
                while (end > 0 && metrics.stringWidth(text.substring(0, end) + "...") > max) end--;
                text = text.substring(0, end) + "...";
            }
            g.drawString(text, 4, y);
        }
    }

    private static final class Ab1Header extends JComponent {
        private final Ab1ReferenceCanvas canvas;

        Ab1Header(Ab1ReferenceCanvas canvas) {
            this.canvas = canvas;
            setOpaque(true);
            setBackground(Color.WHITE);
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(130, canvas.getPreferredSize().height);
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(55, 65, 72));
                g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
                g.drawString("AB1", 4, 20);
            } finally {
                g.dispose();
            }
        }
    }

    private interface SelectionListener {
        void selected(String read, int coordinate);
    }
}
