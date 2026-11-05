package io.opentools.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentools.core.job.JobRecord;
import io.opentools.core.job.ToolExecutionService;
import io.opentools.core.i18n.ErrorText;
import io.opentools.core.i18n.LanguageManager;
import io.opentools.plugin.host.ToolTextResolver;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ResultInspectorPanel extends JPanel {
    private ToolExecutionService executionService;
    private final LanguageManager language;
    private final ToolTextResolver toolText;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JComboBox<JobItem> jobs = new JComboBox<>();
    private final JTextArea summary = new JTextArea(7, 30);
    private final DefaultTableModel tableModel = new DefaultTableModel();
    private final JTable table = new JTable(tableModel);
    private final JTextArea paths = new JTextArea(2, 40);
    private final JTabbedPane views = new JTabbedPane();
    private final PlotViewerPanel plotViewer;
    private final int plotTabIndex;
    private BlastViewerPanel blastViewer;
    private int blastTabIndex = -1;
    private MemeViewerPanel memeViewer;
    private int memeTabIndex = -1;
    private Ab1ViewerPanel ab1Viewer;
    private int ab1TabIndex = -1;
    private JobRecord current;
    private JsonNode currentResult;
    private boolean plotLoaded;
    private String pendingRunId;

    ResultInspectorPanel(ToolExecutionService executionService, LanguageManager language) {
        this(executionService, language, ToolTextResolver.hostOnly(language));
    }

    ResultInspectorPanel(
            ToolExecutionService executionService,
            LanguageManager language,
            ToolTextResolver toolText
    ) {
        this.executionService = executionService;
        this.language = language;
        this.toolText = toolText;
        UiAccessibility.name(table, language.text("results.tab.data", "Result data table"));
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JPanel toolbar = new JPanel(new BorderLayout(8, 6));
        JLabel title = new JLabel(language.text("results.title", "Results"));
        title.setFont(title.getFont().deriveFont(title.getFont().getSize2D() + 7f));
        JPanel heading = new JPanel(new BorderLayout(12, 0));
        heading.add(title, BorderLayout.WEST);
        JPanel jobBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        jobBar.add(new JLabel(language.text("results.job", "Job")));
        jobs.setPreferredSize(new Dimension(320, 30));
        jobs.setMinimumSize(new Dimension(180, 30));
        jobBar.add(jobs);
        JButton refresh = new JButton(language.text("results.refresh", "Refresh"));
        jobBar.add(refresh);
        heading.add(jobBar, BorderLayout.EAST);
        toolbar.add(heading, BorderLayout.NORTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton openRun = new JButton(language.text("results.open_run", "Open run directory"));
        JButton openResult = new JButton(language.text("results.open_result", "Open result JSON"));
        JButton openManifest = new JButton(language.text("results.open_manifest", "Open manifest"));
        JButton exportTable = new JButton(language.text("results.export_table", "Export table"));
        controls.add(openRun);
        controls.add(openResult);
        controls.add(openManifest);
        controls.add(exportTable);
        toolbar.add(controls, BorderLayout.SOUTH);
        add(toolbar, BorderLayout.NORTH);

        summary.setEditable(false);
        summary.setLineWrap(true);
        summary.setWrapStyleWord(true);
        summary.setRows(4);
        paths.setEditable(false);
        paths.setFocusable(false);
        paths.setOpaque(false);
        paths.setLineWrap(true);
        paths.setWrapStyleWord(false);
        paths.setBorder(null);
        JScrollPane summaryScroll = new JScrollPane(summary);
        summaryScroll.setPreferredSize(new Dimension(100, 105));
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.add(summaryScroll, BorderLayout.CENTER);
        top.add(paths, BorderLayout.SOUTH);

        JPanel dataView = new JPanel(new BorderLayout());
        dataView.setBorder(BorderFactory.createLineBorder(new java.awt.Color(218, 222, 225)));
        dataView.add(new JScrollPane(table), BorderLayout.CENTER);
        plotViewer = new PlotViewerPanel(language);
        views.addTab(language.text("results.tab.data", "Data"), dataView);
        views.addTab(language.text("results.tab.visualization", "Visualization"), plotViewer);
        plotTabIndex = views.indexOfComponent(plotViewer);
        views.setEnabledAt(plotTabIndex, false);
        views.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
        views.addChangeListener(ignored -> {
            if (views.getSelectedIndex() == plotTabIndex && !plotLoaded && currentResult != null) {
                loadPlot(currentResult);
            }
        });

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.add(top, BorderLayout.NORTH);
        content.add(views, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);

        refresh.addActionListener(ignored -> refresh());
        jobs.addActionListener(ignored -> loadSelected());
        openRun.addActionListener(ignored -> open(current == null ? null : current.runDirectory()));
        openResult.addActionListener(ignored -> open(current == null ? null : current.runDirectory().resolve("result.json")));
        openManifest.addActionListener(ignored -> open(current == null ? null : current.runDirectory().resolve("manifest.json")));
        exportTable.addActionListener(ignored -> exportTable());
        summary.setText(language.text("results.empty", "Run a tool to see its result here."));
    }

    void selectJob(String runId) {
        pendingRunId = runId;
        refresh();
    }

    void refresh() {
        ToolExecutionService requestedService = executionService;
        Thread.ofVirtual().start(() -> {
            try {
                List<JobRecord> recent = requestedService.recentJobs(50);
                SwingUtilities.invokeLater(() -> {
                    if (requestedService != executionService) {
                        return;
                    }
                    Object selected = jobs.getSelectedItem();
                    jobs.removeAllItems();
                    recent.forEach(job -> jobs.addItem(new JobItem(
                            job,
                            toolText.text(
                                    job.toolId(),
                                    "tool." + job.toolId() + ".name",
                                    job.toolId()
                            ) + " · "
                                    + language.text("job.status." + job.status().name(), job.status().name())
                                    + " · " + job.runId().substring(0, 8)
                    )));
                    if (selected instanceof JobItem previous) {
                        for (int i = 0; i < jobs.getItemCount(); i++) {
                            if (jobs.getItemAt(i).job.runId().equals(previous.job.runId())) {
                                jobs.setSelectedIndex(i);
                                break;
                            }
                        }
                    }
                    if (pendingRunId != null) {
                        for (int i = 0; i < jobs.getItemCount(); i++) {
                            if (jobs.getItemAt(i).job.runId().equals(pendingRunId)) {
                                jobs.setSelectedIndex(i);
                                break;
                            }
                        }
                        pendingRunId = null;
                    }
                    if (jobs.getItemCount() == 0) {
                        summary.setText(language.text("results.none", "This project has no run results yet."));
                        tableModel.setDataVector(new Object[0][0], new Object[0]);
                        clearPlot();
                        clearBlast();
                    }
                    loadSelected();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> summary.setText(rootMessage(ex)));
            }
        });
    }

    void rebind(ToolExecutionService executionService) {
        this.executionService = executionService;
        current = null;
        pendingRunId = null;
        jobs.removeAllItems();
        summary.setText(language.text("results.none", "This project has no run results yet."));
        tableModel.setDataVector(new Object[0][0], new Object[0]);
        paths.setText("");
        clearPlot();
        clearBlast();
        clearMeme();
        clearAb1();
        refresh();
    }

    private void loadSelected() {
        JobItem item = (JobItem) jobs.getSelectedItem();
        JobRecord job = item == null ? null : item.job;
        current = job;
        currentResult = null;
        plotLoaded = false;
        if (job == null) {
            return;
        }
        paths.setText(language.text("results.run_directory", "Run directory: {0}", job.runDirectory()));
        paths.setToolTipText(job.runDirectory().toString());
        Path resultPath = job.runDirectory().resolve("result.json");
        if (!Files.isRegularFile(resultPath)) {
            summary.setText(language.text(
                    "results.no_result",
                    "Run status: {0}\nThe result file has not been generated yet.",
                    localizedStatus(job)
            ) + (job.errorMessage() == null ? "" : "\n"
                    + ErrorText.localize(language, job.errorCode(), job.errorMessage())));
            tableModel.setDataVector(new Object[0][0], new Object[0]);
            clearPlot();
            clearBlast();
            clearMeme();
            clearAb1();
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                JsonNode root = objectMapper.readTree(resultPath.toFile());
                String summaryText = localizedResultText(root, "summary");
                String detail = localizedResultText(root, "detail");
                JsonNode tableNode = root.path("table");
                List<String> columns = new ArrayList<>();
                tableNode.path("columns").forEach(node -> {
                    String value = node.asText();
                    columns.add(toolText.text(
                            job.toolId(),
                            "table.column." + normalizeKey(value),
                            toolText.text(
                                    job.toolId(),
                                    "result." + job.toolId() + ".column." + normalizeKey(value),
                                    value
                            )
                    ));
                });
                Object[][] rows = new Object[tableNode.path("rows").size()][columns.size()];
                int rowIndex = 0;
                for (JsonNode row : tableNode.path("rows")) {
                    for (int column = 0; column < columns.size(); column++) {
                        String value = row.path(column).asText("");
                        rows[rowIndex][column] = toolText.text(
                                job.toolId(),
                                "table.value." + normalizeKey(value),
                                toolText.text(
                                        job.toolId(),
                                        "result." + job.toolId() + ".value." + normalizeKey(value),
                                        value
                                )
                        );
                    }
                    rowIndex++;
                }
                SwingUtilities.invokeLater(() -> {
                    if (current != job) {
                        return;
                    }
                    currentResult = root;
                    summary.setText(language.text("results.status", "Status: {0}", localizedStatus(job))
                            + "\n" + language.text("results.summary", "Summary: {0}", summaryText)
                            + (detail.isBlank() ? "" : "\n" + language.text("results.detail", "Detail: {0}", detail))
                            + "\n" + language.text("results.manifest", "Input hashes and parameters: manifest.json"));
                    tableModel.setDataVector(rows, columns.toArray());
                    preparePlotTab(root);
                    prepareBlastTab(root, job);
                    prepareMemeTab(root, job);
                    prepareAb1Tab(root, job);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    if (current != job) {
                        return;
                    }
                    summary.setText(language.text(
                            "results.parse_failure",
                            "The result file exists but could not be parsed: {0}",
                            rootMessage(ex)
                    ));
                    tableModel.setDataVector(new Object[0][0], new Object[0]);
                    clearPlot();
                    clearBlast();
                    clearMeme();
                    clearAb1();
                });
            }
        });
    }

    private void preparePlotTab(JsonNode root) {
        boolean hasPlot = false;
        for (JsonNode artifact : root.path("artifacts")) {
            String mediaType = artifact.path("mediaType").asText("");
            String path = artifact.path("path").asText("");
            if (!path.isBlank()
                    && (mediaType.startsWith("image/")
                    || mediaType.contains("plot-preview")
                    || mediaType.contains("plot+json"))) {
                hasPlot = true;
                break;
            }
        }
        views.setEnabledAt(plotTabIndex, hasPlot);
        if (!hasPlot) {
            return;
        }
        Path preview = null;
        Path full = null;
        for (JsonNode artifact : root.path("artifacts")) {
            String mediaType = artifact.path("mediaType").asText("");
            String pathText = artifact.path("path").asText("");
            if (pathText.isBlank()) {
                continue;
            }
            Path path = Path.of(pathText);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            if (mediaType.equals("application/vnd.opentools.plot-preview+json")) {
                preview = path;
            } else if (mediaType.equals("application/vnd.opentools.plot+json")
                    || mediaType.equals("application/json") && path.getFileName().toString().endsWith(".plot.json")) {
                full = path;
            }
        }
        if (preview != null && !preview.equals(full)) {
            views.setToolTipTextAt(plotTabIndex, language.text(
                    "results.plot_preview",
                    "A bounded preview is shown; exported tool files use the complete matrix."
            ));
        }
    }

    private void loadPlot(JsonNode root) {
        plotLoaded = true;
        Path preview = null;
        Path full = null;
        Path image = null;
        for (JsonNode artifact : root.path("artifacts")) {
            String mediaType = artifact.path("mediaType").asText("");
            String pathText = artifact.path("path").asText("");
            if (pathText.isBlank()) {
                continue;
            }
            Path path = Path.of(pathText);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            if (mediaType.equals("application/vnd.opentools.plot-preview+json")) {
                preview = path;
            } else if (mediaType.equals("application/vnd.opentools.plot+json")
                    || mediaType.equals("application/json") && path.getFileName().toString().endsWith(".plot.json")) {
                full = path;
            } else if (mediaType.startsWith("image/")) {
                image = path;
            }
        }
        Path selected = preview != null ? preview : full;
        if (selected == null && image == null) {
            clearPlot();
            return;
        }
        try {
            if (selected != null) {
                plotViewer.load(selected);
            } else {
                plotViewer.loadImage(image);
            }
            views.setEnabledAt(plotTabIndex, true);
            if (preview != null && !preview.equals(full)) {
                views.setToolTipTextAt(plotTabIndex, language.text(
                        "results.plot_preview",
                        "A bounded preview is shown; exported tool files use the complete matrix."
                ));
            }
        } catch (Exception ex) {
            plotViewer.showLoadFailure(rootMessage(ex));
            views.setEnabledAt(plotTabIndex, true);
        }
    }

    private void prepareBlastTab(JsonNode root, JobRecord job) {
        clearBlast();
        if (!"alignment.blast.alignments".equals(job.toolId())
                && !"alignment.blast.local".equals(job.toolId())) {
            return;
        }
        String path = null;
        for (JsonNode artifact : root.path("artifacts")) {
            String title = artifact.path("title").asText("");
            if (title.contains("BLAST") && title.contains("alignment")) {
                path = artifact.path("path").asText("");
                break;
            }
        }
        if (path == null || !Files.isRegularFile(Path.of(path))) {
            return;
        }
        try {
            blastViewer = new BlastViewerPanel(language, Path.of(path));
            views.addTab(language.text("results.tab.blast", "BLAST"), blastViewer);
            blastTabIndex = views.indexOfComponent(blastViewer);
        } catch (Exception ex) {
            blastViewer = null;
            blastTabIndex = -1;
        }
    }

    private void clearBlast() {
        if (blastViewer != null) {
            views.remove(blastViewer);
            blastViewer = null;
            blastTabIndex = -1;
        }
        if (views.getSelectedIndex() >= views.getTabCount() && views.getTabCount() > 0) {
            views.setSelectedIndex(0);
        }
    }

    private void prepareMemeTab(JsonNode root, JobRecord job) {
        clearMeme();
        if (!"meme.run".equals(job.toolId())) {
            return;
        }
        String path = null;
        for (JsonNode artifact : root.path("artifacts")) {
            String title = artifact.path("title").asText("");
            if (title.toLowerCase(Locale.ROOT).contains("meme xml")) {
                path = artifact.path("path").asText("");
                break;
            }
        }
        if (path == null || !Files.isRegularFile(Path.of(path))) {
            return;
        }
        try {
            memeViewer = new MemeViewerPanel(language, Path.of(path));
            views.addTab(language.text("results.tab.meme", "MEME"), memeViewer);
            memeTabIndex = views.indexOfComponent(memeViewer);
        } catch (Exception ex) {
            memeViewer = null;
            memeTabIndex = -1;
        }
    }

    private void clearMeme() {
        if (memeViewer != null) {
            views.remove(memeViewer);
            memeViewer = null;
            memeTabIndex = -1;
        }
    }

    private void prepareAb1Tab(JsonNode root, JobRecord job) {
        clearAb1();
        if (!"sanger.assemble".equals(job.toolId()) || job.paramsJson() == null) {
            return;
        }
        try {
            JsonNode params = objectMapper.readTree(job.paramsJson());
            String directory = params.path("readsDirectory").asText("");
            if (directory.isBlank()) return;
            java.util.List<Path> files = new java.util.ArrayList<>();
            try (var stream = Files.list(Path.of(directory))) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
                                .endsWith(".ab1"))
                        .forEach(files::add);
            }
            if (files.isEmpty()) return;
            ab1Viewer = new Ab1ViewerPanel(language, files);
            views.addTab(language.text("results.tab.ab1", "AB1"), ab1Viewer);
            ab1TabIndex = views.indexOfComponent(ab1Viewer);
        } catch (Exception ex) {
            ab1Viewer = null;
            ab1TabIndex = -1;
        }
    }

    private void clearAb1() {
        if (ab1Viewer != null) {
            views.remove(ab1Viewer);
            ab1Viewer = null;
            ab1TabIndex = -1;
        }
    }

    private void clearPlot() {
        plotViewer.clear();
        views.setEnabledAt(plotTabIndex, false);
        plotLoaded = false;
        currentResult = null;
        if (views.getSelectedIndex() == plotTabIndex) {
            views.setSelectedIndex(0);
        }
    }

    private void open(Path path) {
        if (path == null) {
            return;
        }
        try {
            if (!Files.exists(path)) {
                JOptionPane.showMessageDialog(
                        this,
                        language.text("dialog.path_missing", "Path does not exist: {0}", path),
                        "OpenTools",
                        JOptionPane.WARNING_MESSAGE
                );
                return;
            }
            if (!Desktop.isDesktopSupported()) {
                JOptionPane.showMessageDialog(this, path.toString(), "OpenTools", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            Desktop.getDesktop().open(path.toFile());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    rootMessage(ex),
                    language.text("dialog.cannot_open", "Cannot open"),
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void exportTable() {
        if (tableModel.getRowCount() == 0 || tableModel.getColumnCount() == 0) {
            JOptionPane.showMessageDialog(
                    this,
                    language.text("results.no_table", "The selected result has no table data."),
                    "OpenTools",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }
        JFileChooser chooser = new JFileChooser();
        FileNameExtensionFilter tsv = new FileNameExtensionFilter(
                language.text("table.file.tsv", "Tab-separated values"),
                "tsv"
        );
        FileNameExtensionFilter csv = new FileNameExtensionFilter(
                language.text("table.file.csv", "Comma-separated values"),
                "csv"
        );
        chooser.addChoosableFileFilter(tsv);
        chooser.addChoosableFileFilter(csv);
        chooser.setFileFilter(tsv);
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        boolean comma = chooser.getFileFilter() == csv
                || chooser.getSelectedFile().getName().toLowerCase(java.util.Locale.ROOT).endsWith(".csv");
        Path output = chooser.getSelectedFile().toPath();
        try {
            writeTable(output, comma ? ',' : '\t');
            JOptionPane.showMessageDialog(
                    this,
                    language.text("results.export_success", "Table exported: {0}", output),
                    "OpenTools",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    language.text("results.export_failed", "Could not export table: {0}", rootMessage(ex)),
                    "OpenTools",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void writeTable(Path output, char delimiter) throws Exception {
        try (var writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (int column = 0; column < tableModel.getColumnCount(); column++) {
                if (column > 0) {
                    writer.write(delimiter);
                }
                writer.write(quote(tableModel.getColumnName(column), delimiter));
            }
            writer.newLine();
            for (int row = 0; row < tableModel.getRowCount(); row++) {
                for (int column = 0; column < tableModel.getColumnCount(); column++) {
                    if (column > 0) {
                        writer.write(delimiter);
                    }
                    Object value = tableModel.getValueAt(row, column);
                    writer.write(quote(value == null ? "" : value.toString(), delimiter));
                }
                writer.newLine();
            }
        }
    }

    private static String quote(String value, char delimiter) {
        if (delimiter != ',' || (!value.contains(",") && !value.contains("\"")
                && !value.contains("\n") && !value.contains("\r"))) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private String localizedStatus(JobRecord job) {
        return language.text("job.status." + job.status().name(), job.status().name());
    }

    private String localizedResultText(JsonNode root, String field) {
        String fallback = root.path(field).asText("");
        JsonNode keyNode = root.path(field + "Key");
        if (!keyNode.isTextual() || keyNode.asText().isBlank()) {
            return fallback;
        }
        List<JsonNode> arguments = new ArrayList<>();
        root.path(field + "Arguments").forEach(arguments::add);
        List<Object> values = arguments.stream()
                .map(node -> node.isNumber() ? (Object) node.numberValue() : (Object) node.asText())
                .toList();
        return toolText.text(
                current.toolId(),
                keyNode.asText(),
                fallback,
                values.toArray()
        );
    }

    private static String normalizeKey(String value) {
        return value.toLowerCase(java.util.Locale.ROOT)
                .replace("%", " percent")
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private record JobItem(JobRecord job, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}