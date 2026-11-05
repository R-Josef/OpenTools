package io.opentools.ui;

import io.opentools.core.i18n.LanguageManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Lightweight MEME motif table and logo viewer. */
final class MemeViewerPanel extends JPanel {
    private final List<Motif> motifs;
    private final DefaultListModel<Motif> model = new DefaultListModel<>();
    private final JList<Motif> list = new JList<>(model);
    private final JLabel summary = new JLabel(" ", SwingConstants.LEFT);
    private final JLabel consensus = new JLabel(" ", SwingConstants.CENTER);
    private final DefaultTableModel siteModel = new DefaultTableModel();
    private final MotifLogo logo = new MotifLogo();
    private final JPanel right = new JPanel(new BorderLayout(0, 6));

    MemeViewerPanel(LanguageManager language, Path xml) throws Exception {
        motifs = parse(xml);
        setLayout(new BorderLayout(10, 0));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        for (Motif motif : motifs) model.addElement(motif);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((jlist, value, index, selected, focus) -> {
            JLabel label = new JLabel(value.id() + " · E=" + value.evalue());
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7));
            if (selected) {
                label.setBackground(jlist.getSelectionBackground());
                label.setForeground(jlist.getSelectionForeground());
            }
            return label;
        });
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) show(list.getSelectedValue());
        });
        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.add(new JLabel(language.text("meme.viewer.motifs", "Motifs")), BorderLayout.NORTH);
        left.add(new JScrollPane(list), BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(230, 400));
        right.add(logo, BorderLayout.CENTER);
        JPanel lower = new JPanel(new BorderLayout(0, 4));
        lower.add(consensus, BorderLayout.NORTH);
        JTable siteTable = new JTable(siteModel);
        siteTable.setFillsViewportHeight(true);
        siteModel.setColumnIdentifiers(new Object[]{"Sequence", "Position", "Strand", "P-value"});
        JPanel sitePanel = new JPanel(new BorderLayout(0, 3));
        sitePanel.add(new JLabel(language.text("meme.viewer.sites", "Contributing sites")), BorderLayout.NORTH);
        sitePanel.add(new JScrollPane(siteTable), BorderLayout.CENTER);
        sitePanel.setPreferredSize(new Dimension(0, 150));
        lower.add(sitePanel, BorderLayout.CENTER);
        JButton copy = new JButton(language.text("meme.viewer.copy_consensus", "Copy consensus"));
        copy.addActionListener(event -> {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(consensus.getText()), null);
        });
        lower.add(copy, BorderLayout.SOUTH);
        right.add(lower, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setDividerSize(7);
        split.setResizeWeight(0);
        add(split, BorderLayout.CENTER);
        add(summary, BorderLayout.SOUTH);
        if (!motifs.isEmpty()) {
            list.setSelectedIndex(0);
        } else {
            summary.setText(language.text("meme.viewer.empty", "No motifs were found in this XML."));
        }
    }

    int motifCount() { return motifs.size(); }
    int selectedMotif() { return list.getSelectedIndex(); }
    int siteCount() { return siteModel.getRowCount(); }
    String consensusText() { return consensus.getText(); }

    void selectMotif(int index) {
        if (index >= 0 && index < motifs.size()) list.setSelectedIndex(index);
    }

    private void show(Motif motif) {
        if (motif == null) return;
        logo.setMotif(motif);
        consensus.setText(motif.consensus());
        summary.setText(motif.id() + " · width " + motif.width() + " · sites " + motif.sites()
                + " · E=" + motif.evalue());
        siteModel.setRowCount(0);
        for (Site site : motif.siteList()) {
            siteModel.addRow(new Object[]{site.sequence(), site.position(), site.strand(), site.pvalue()});
        }
    }

    private static List<Motif> parse(Path xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        Document document = factory.newDocumentBuilder().parse(xml.toFile());
        NodeList nodes = document.getElementsByTagName("motif");
        List<Motif> result = new ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            String id = element.getAttribute("id");
            if (id.isBlank()) continue;
            List<Map<String, Double>> matrix = new ArrayList<>();
            NodeList probabilities = element.getElementsByTagName("probabilities");
            if (probabilities.getLength() > 0) {
                NodeList arrays = ((Element) probabilities.item(0)).getElementsByTagName("alphabet_array");
                for (int arrayIndex = 0; arrayIndex < arrays.getLength(); arrayIndex++) {
                    Map<String, Double> values = readValues((Element) arrays.item(arrayIndex));
                    if (!values.isEmpty()) matrix.add(values);
                }
            } else {
                NodeList positions = element.getElementsByTagName("pos");
                for (int posIndex = 0; posIndex < positions.getLength(); posIndex++) {
                    NodeList arrays = ((Element) positions.item(posIndex)).getElementsByTagName("alphabet_array");
                    if (arrays.getLength() == 0) continue;
                    Map<String, Double> values = readValues((Element) arrays.item(0));
                    if (!values.isEmpty()) matrix.add(values);
                }
            }
            if (matrix.isEmpty()) continue;
            List<Site> sites = new ArrayList<>();
            NodeList siteNodes = element.getElementsByTagName("contributing_site");
            for (int siteIndex = 0; siteIndex < siteNodes.getLength(); siteIndex++) {
                Element site = (Element) siteNodes.item(siteIndex);
                sites.add(new Site(
                        site.getAttribute("sequence_id"),
                        site.getAttribute("position"),
                        site.getAttribute("strand"),
                        site.getAttribute("pvalue")
                ));
            }
            result.add(new Motif(
                    element.getAttribute("alt").isBlank()
                            ? id : element.getAttribute("alt"),
                    integer(element.getAttribute("width")),
                    integer(element.getAttribute("sites")),
                    element.getAttribute("e_value").isBlank()
                            ? element.getAttribute("evalue") : element.getAttribute("e_value"),
                    matrix,
                    sites
            ));
        }
        return result;
    }

    private static Map<String, Double> readValues(Element array) {
        Map<String, Double> values = new LinkedHashMap<>();
        NodeList valueNodes = array.getElementsByTagName("value");
        for (int valueIndex = 0; valueIndex < valueNodes.getLength(); valueIndex++) {
            Element value = (Element) valueNodes.item(valueIndex);
            String symbol = value.getAttribute("letter_id");
            if (symbol.isBlank()) symbol = value.getAttribute("id");
            try {
                values.put(symbol, Double.parseDouble(value.getTextContent().trim()));
            } catch (NumberFormatException ignored) {
                values.put(symbol, 0.0);
            }
        }
        return values;
    }

    private static int integer(String value) {
        try {
            return value == null || value.isBlank() ? 0 : Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private record Site(String sequence, String position, String strand, String pvalue) {
    }

    private record Motif(
            String id,
            int width,
            int sites,
            String evalue,
            List<Map<String, Double>> matrix,
            List<Site> siteList
    ) {
        String consensus() {
            StringBuilder text = new StringBuilder();
            for (Map<String, Double> row : matrix) {
                text.append(row.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey).orElse("N"));
            }
            return text.toString();
        }
    }

    private static final class MotifLogo extends JComponent {
        private static final Color[] COLORS = {
                new Color(34, 139, 74), new Color(45, 98, 178),
                new Color(196, 116, 26), new Color(178, 48, 48),
                new Color(100, 70, 150), new Color(80, 80, 80)
        };
        private Motif motif;

        MotifLogo() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(650, 230));
        }

        void setMotif(Motif motif) {
            this.motif = motif;
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (motif == null || motif.matrix().isEmpty()) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = getWidth();
                int height = getHeight() - 34;
                int columns = motif.matrix().size();
                int cell = Math.max(14, Math.min(50, (width - 30) / Math.max(1, columns)));
                int baseY = height - 10;
                double maximumBits = Math.log(20) / Math.log(2);
                g.setColor(new Color(92, 99, 104));
                g.drawLine(16, baseY, 16 + cell * columns, baseY);
                for (int column = 0; column < columns; column++) {
                    Map<String, Double> row = motif.matrix().get(column);
                    double total = row.values().stream().mapToDouble(Double::doubleValue).sum();
                    if (total <= 0) continue;
                    double entropy = 0;
                    for (double value : row.values()) {
                        if (value > 0) {
                            double p = value / total;
                            entropy -= p * (Math.log(p) / Math.log(2));
                        }
                    }
                    double information = Math.max(0, maximumBits - entropy);
                    List<Map.Entry<String, Double>> ordered = row.entrySet().stream()
                            .sorted(Comparator.comparingDouble(Map.Entry::getValue))
                            .toList();
                    int x = 18 + column * cell;
                    int y = baseY;
                    for (Map.Entry<String, Double> entry : ordered) {
                        double fraction = entry.getValue() / total;
                        int letterHeight = (int) Math.round(information / maximumBits * (height - 20) * fraction);
                        if (letterHeight < 2) continue;
                        int font = Math.max(7, Math.min(cell - 2, letterHeight));
                        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, font));
                        FontMetrics metrics = g.getFontMetrics();
                        String symbol = entry.getKey();
                        int color = Math.abs(symbol.hashCode()) % COLORS.length;
                        g.setColor(COLORS[color]);
                        g.drawString(symbol, x + (cell - metrics.stringWidth(symbol)) / 2,
                                y - metrics.getDescent() / 2);
                        y -= letterHeight;
                    }
                }
                g.setColor(new Color(92, 99, 104));
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                g.drawString("1", 16, getHeight() - 5);
                g.drawString(Integer.toString(columns), 16 + cell * columns - 10, getHeight() - 5);
            } finally {
                g.dispose();
            }
        }
    }
}
