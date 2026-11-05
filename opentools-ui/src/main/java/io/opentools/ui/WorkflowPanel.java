package io.opentools.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.core.job.JobRecord;
import io.opentools.core.job.JobStatus;
import io.opentools.core.job.ToolExecutionService;
import io.opentools.core.workspace.Project;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.QuadCurve2D;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * JSON workflow editor with a read-only DAG preview and a sequential GUI runner.
 */
final class WorkflowPanel extends JPanel {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "\\$\\{([A-Za-z0-9_-]+)\\.(output(?:\\[\\d+])?|metric\\.[A-Za-z0-9_.-]+|runId)\\}");
    private static final String TEMPLATE = """
            {
              "name": "my-workflow",
              "continueOnError": false,
              "steps": [
                {
                  "id": "step1",
                  "toolId": "fasta.stats",
                  "parameters": {"input": "data/sample.fa"}
                },
                {
                  "id": "step2",
                  "toolId": "table.preview",
                  "parameters": {"input": "${step1.output}"},
                  "dependsOn": ["step1"]
                }
              ]
            }
            """;

    private final OpenToolsGui.UiSession session;
    private final JTextArea editor = new JTextArea();
    private final WorkflowCanvas canvas = new WorkflowCanvas();
    private final JTextArea log = new JTextArea(8, 40);
    private final JList<String> saved = new JList<>();
    private final JLabel status = new JLabel();
    private final JButton run = new JButton("Run");
    private final JButton addStep = new JButton();
    private final JButton editStep = new JButton();
    private final JButton deleteStep = new JButton();
    private final JLabel selectedStep = new JLabel(" ");
    private WorkflowSpec current;
    private String selectedId;

    WorkflowPanel(OpenToolsGui.UiSession session) {
        this.session = session;
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JLabel title = new JLabel(session.language().text("workflows.title", "Workflows"));
        title.setFont(title.getFont().deriveFont(title.getFont().getSize2D() + 8f));
        JPanel heading = new JPanel(new BorderLayout());
        heading.add(title, BorderLayout.WEST);
        status.setText(session.language().text("workflows.status.ready", "Edit JSON, preview the DAG, then validate or run."));
        heading.add(status, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton create = new JButton(session.language().text("workflows.new", "New"));
        JButton save = new JButton(session.language().text("workflows.save", "Save"));
        JButton delete = new JButton(session.language().text("workflows.delete", "Delete"));
        JButton validate = new JButton(session.language().text("workflows.validate", "Validate"));
        run.setText(session.language().text("workflows.run", "Run"));
        addStep.setText(session.language().text("workflows.add_step", "Add step"));
        editStep.setText(session.language().text("workflows.edit_step", "Edit step"));
        deleteStep.setText(session.language().text("workflows.delete_step", "Delete step"));
        editStep.setEnabled(false);
        deleteStep.setEnabled(false);
        toolbar.add(create);
        toolbar.add(save);
        toolbar.add(delete);
        toolbar.add(validate);
        toolbar.add(run);
        toolbar.add(addStep);
        toolbar.add(editStep);
        toolbar.add(deleteStep);
        toolbar.add(selectedStep);

        saved.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        saved.setBorder(BorderFactory.createTitledBorder(session.language().text("workflows.saved", "Saved workflows")));
        saved.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                loadSelected();
            }
        });
        JScrollPane savedScroll = new JScrollPane(saved);
        savedScroll.setPreferredSize(new Dimension(210, 100));

        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        editor.setText(TEMPLATE);
        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { preview(); }
            @Override public void removeUpdate(DocumentEvent event) { preview(); }
            @Override public void changedUpdate(DocumentEvent event) { preview(); }
        });
        JScrollPane editorScroll = new JScrollPane(editor);
        editorScroll.setBorder(BorderFactory.createTitledBorder(session.language().text("workflows.editor", "Workflow JSON")));
        JScrollPane canvasScroll = new JScrollPane(canvas);
        canvasScroll.setBorder(BorderFactory.createTitledBorder(session.language().text("workflows.preview", "DAG preview")));
        JSplitPane editAndCanvas = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorScroll, canvasScroll);
        editAndCanvas.setResizeWeight(0.55);
        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, savedScroll, editAndCanvas);
        main.setResizeWeight(0.18);
        add(main, BorderLayout.CENTER);

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setBorder(BorderFactory.createTitledBorder(session.language().text("workflows.log", "Run log")));
        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.add(toolbar, BorderLayout.NORTH);
        bottom.add(logScroll, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        create.addActionListener(ignored -> {
            editor.setText(TEMPLATE);
            current = null;
            status.setText(session.language().text("workflows.status.new", "New workflow template loaded."));
        });
        save.addActionListener(ignored -> saveWorkflow());
        delete.addActionListener(ignored -> deleteWorkflow());
        validate.addActionListener(ignored -> validateWorkflow(true));
        run.addActionListener(ignored -> runWorkflow());
        addStep.addActionListener(ignored -> addStep());
        editStep.addActionListener(ignored -> editStep());
        deleteStep.addActionListener(ignored -> deleteStep());
        canvas.setSelectionListener(id -> {
            selectedId = id;
            selectedStep.setText(id == null ? " " : session.language().text(
                    "workflows.selected", "Selected: {0}", id));
            editStep.setEnabled(id != null);
            deleteStep.setEnabled(id != null);
        });
        refreshSaved();
        preview();
    }

    void rebind() {
        saved.clearSelection();
        refreshSaved();
        preview();
    }

    private void refreshSaved() {
        Path directory = session.project().root().resolve("workflows");
        List<String> names = new ArrayList<>();
        if (Files.isDirectory(directory)) {
            try (Stream<Path> files = Files.list(directory)) {
                files.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                        .sorted()
                        .forEach(names::add);
            } catch (Exception ex) {
                status.setText(ex.getMessage());
            }
        }
        saved.setListData(names.toArray(String[]::new));
        saved.setToolTipText(directory.toString());
    }

    private void loadSelected() {
        String name = saved.getSelectedValue();
        if (name == null) {
            return;
        }
        try {
            editor.setText(Files.readString(
                    session.project().root().resolve("workflows").resolve(name + ".json"),
                    StandardCharsets.UTF_8));
            preview();
        } catch (Exception ex) {
            status.setText(ex.getMessage());
        }
    }

    private void saveWorkflow() {
        try {
            WorkflowSpec spec = parse(editor.getText());
            Path directory = session.project().root().resolve("workflows");
            Files.createDirectories(directory);
            Path target = directory.resolve(spec.name().replaceAll("[^A-Za-z0-9._-]+", "-") + ".json");
            Files.writeString(target, editor.getText(), StandardCharsets.UTF_8);
            refreshSaved();
            saved.setSelectedValue(target.getFileName().toString().replaceFirst("\\.json$", ""), true);
            status.setText(session.language().text("workflows.status.saved", "Saved: {0}", target.getFileName()));
        } catch (Exception ex) {
            status.setText(session.language().text("workflows.status.invalid", "Invalid workflow: {0}", ex.getMessage()));
        }
    }

    private void deleteWorkflow() {
        String name = saved.getSelectedValue();
        if (name == null) {
            return;
        }
        try {
            Files.deleteIfExists(session.project().root().resolve("workflows").resolve(name + ".json"));
            refreshSaved();
            status.setText(session.language().text("workflows.status.deleted", "Deleted: {0}", name));
        } catch (Exception ex) {
            status.setText(ex.getMessage());
        }
    }

    private boolean validateWorkflow(boolean updateStatus) {
        try {
            WorkflowSpec spec = parse(editor.getText());
            topologicalOrder(spec);
            current = spec;
            canvas.setSpec(spec);
            if (updateStatus) {
                status.setText(session.language().text("workflows.status.valid", "Valid workflow: {0} steps.", spec.steps().size()));
            }
            return true;
        } catch (Exception ex) {
            canvas.setError(ex.getMessage());
            if (updateStatus) {
                status.setText(session.language().text("workflows.status.invalid", "Invalid workflow: {0}", ex.getMessage()));
            }
            return false;
        }
    }

    private void preview() {
        try {
            WorkflowSpec spec = parse(editor.getText());
            topologicalOrder(spec);
            current = spec;
            canvas.setSpec(spec);
        } catch (Exception ex) {
            canvas.setError(ex.getMessage());
        }
    }

    private void addStep() {
        try {
            WorkflowSpec spec = parse(editor.getText());
            topologicalOrder(spec);
            Step step = stepDialog(new Step(uniqueId(spec), "fasta.stats", Map.of(), List.of()));
            if (step == null) return;
            List<Step> steps = new ArrayList<>(spec.steps());
            steps.add(step);
            applySpec(new WorkflowSpec(spec.name(), spec.continueOnError(), steps));
        } catch (Exception ex) {
            status.setText(session.language().text("workflows.status.invalid", "Invalid workflow: {0}", ex.getMessage()));
        }
    }

    private void editStep() {
        try {
            WorkflowSpec spec = parse(editor.getText());
            Step existing = spec.steps().stream()
                    .filter(step -> step.id().equals(selectedId)).findFirst().orElse(null);
            if (existing == null) return;
            Step changed = stepDialog(existing);
            if (changed == null) return;
            List<Step> steps = spec.steps().stream()
                    .map(step -> step.id().equals(existing.id()) ? changed : step)
                    .toList();
            applySpec(new WorkflowSpec(spec.name(), spec.continueOnError(), steps));
            canvas.select(changed.id());
        } catch (Exception ex) {
            status.setText(session.language().text("workflows.status.invalid", "Invalid workflow: {0}", ex.getMessage()));
        }
    }

    private void deleteStep() {
        try {
            WorkflowSpec spec = parse(editor.getText());
            Step existing = spec.steps().stream()
                    .filter(step -> step.id().equals(selectedId)).findFirst().orElse(null);
            if (existing == null) return;
            List<Step> steps = spec.steps().stream()
                    .filter(step -> !step.id().equals(existing.id()))
                    .map(step -> new Step(step.id(), step.toolId(), step.parameters(),
                            step.dependsOn().stream().filter(id -> !id.equals(existing.id())).toList()))
                    .toList();
            selectedId = null;
            applySpec(new WorkflowSpec(spec.name(), spec.continueOnError(), steps));
        } catch (Exception ex) {
            status.setText(session.language().text("workflows.status.invalid", "Invalid workflow: {0}", ex.getMessage()));
        }
    }

    private Step stepDialog(Step initial) throws Exception {
        JTextField id = new JTextField(initial.id());
        JTextField toolId = new JTextField(initial.toolId());
        JTextField depends = new JTextField(String.join(",", initial.dependsOn()));
        JTextArea parameters = new JTextArea(JSON.writerWithDefaultPrettyPrinter()
                .writeValueAsString(initial.parameters()), 7, 34);
        parameters.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.add(new JLabel(session.language().text("workflows.step.id", "Step ID")));
        form.add(id);
        form.add(new JLabel(session.language().text("workflows.step.tool", "Tool ID")));
        form.add(toolId);
        form.add(new JLabel(session.language().text("workflows.step.depends", "Depends on")));
        form.add(depends);
        form.add(new JLabel(session.language().text("workflows.step.parameters", "Parameters JSON")));
        form.add(new JScrollPane(parameters));
        int choice = JOptionPane.showConfirmDialog(this, form,
                session.language().text("workflows.step.dialog", "Workflow step"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return null;
        String stepId = id.getText().trim();
        String stepTool = toolId.getText().trim();
        if (stepId.isBlank() || stepTool.isBlank()) {
            throw new IllegalArgumentException("Step ID and tool ID are required.");
        }
        Map<String, Object> values = JSON.readValue(parameters.getText(),
                new TypeReference<Map<String, Object>>() { });
        List<String> dependencies = new ArrayList<>();
        for (String dependency : depends.getText().split(",")) {
            if (!dependency.isBlank()) dependencies.add(dependency.trim());
        }
        if (dependencies.contains(stepId)) {
            throw new IllegalArgumentException("A step cannot depend on itself.");
        }
        return new Step(stepId, stepTool, values, dependencies);
    }

    private void applySpec(WorkflowSpec spec) throws Exception {
        topologicalOrder(spec);
        current = spec;
        editor.setText(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(spec));
        canvas.setSpec(spec);
        selectedId = spec.steps().stream().anyMatch(step -> step.id().equals(selectedId))
                ? selectedId : null;
        canvas.select(selectedId);
        status.setText(session.language().text("workflows.status.visual",
                "Visual edit applied: {0} steps.", spec.steps().size()));
    }

    private static String uniqueId(WorkflowSpec spec) {
        int index = spec.steps().size() + 1;
        while (true) {
            String candidate = "step" + index;
            if (spec.steps().stream().noneMatch(step -> step.id().equals(candidate))) return candidate;
            index++;
        }
    }

    private void runWorkflow() {
        if (!validateWorkflow(true)) {
            return;
        }
        WorkflowSpec spec = current;
        run.setEnabled(false);
        editor.setEnabled(false);
        log.setText("");
        ToolExecutionService execution = session.execution();
        ToolRegistry registry = session.registry();
        Project project = session.project();
        Thread.ofVirtual().start(() -> {
            try {
                executeWorkflow(spec, execution, registry, project);
            } finally {
                SwingUtilities.invokeLater(() -> {
                    run.setEnabled(true);
                    editor.setEnabled(true);
                });
            }
        });
    }

    private void executeWorkflow(
            WorkflowSpec spec,
            ToolExecutionService execution,
            ToolRegistry registry,
            Project project
    ) {
        Map<String, StepOutput> completed = new LinkedHashMap<>();
        Map<String, String> statuses = new LinkedHashMap<>();
        boolean stopped = false;
        for (String id : topologicalOrder(spec)) {
            Step step = spec.steps().stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
            if (stopped || step.dependsOn().stream().anyMatch(dependency -> !"SUCCEEDED".equals(statuses.get(dependency)))) {
                statuses.put(id, "SKIPPED");
                log(id + " SKIPPED");
                continue;
            }
            try {
                Map<String, Object> parameters = resolveParameters(step.parameters(), completed);
                parameters = resolveProjectPaths(step.toolId(), parameters, registry, project);
                ToolRequest request = new ToolRequest(step.toolId(), parameters);
                ValidationResult validation = execution.validate(request);
                if (!validation.valid()) {
                    throw new IllegalStateException(validation.issues().toString());
                }
                SwingUtilities.invokeLater(() -> log(id + " RUNNING"));
                String runId = execution.submit(project, request);
                JobRecord job = waitFor(execution, runId);
                if (job.status() != JobStatus.SUCCEEDED) {
                    throw new IllegalStateException(job.errorMessage() == null
                            ? job.status().name() : job.errorMessage());
                }
                StepOutput output = readOutput(job);
                completed.put(id, output);
                statuses.put(id, "SUCCEEDED");
                SwingUtilities.invokeLater(() -> log(id + " SUCCEEDED (" + runId.substring(0, 8) + ")"));
            } catch (Exception ex) {
                statuses.put(id, "FAILED");
                SwingUtilities.invokeLater(() -> log(id + " FAILED: " + ex.getMessage()));
                if (!spec.continueOnError()) {
                    stopped = true;
                }
            }
        }
        long succeeded = statuses.values().stream().filter("SUCCEEDED"::equals).count();
        long failed = statuses.values().stream().filter("FAILED"::equals).count();
        long skipped = statuses.values().stream().filter("SKIPPED"::equals).count();
        SwingUtilities.invokeLater(() -> {
            canvas.setStatuses(statuses);
            status.setText(session.language().text("workflows.status.finished",
                    "Workflow finished: {0} succeeded, {1} failed, {2} skipped.",
                    succeeded, failed, skipped));
        });
    }

    private static Map<String, Object> resolveProjectPaths(
            String toolId,
            Map<String, Object> parameters,
            ToolRegistry registry,
            Project project
    ) {
        var tool = registry.find(toolId).orElse(null);
        if (tool == null) {
            return parameters;
        }
        Map<String, Object> resolved = new LinkedHashMap<>(parameters);
        for (ToolParameter parameter : tool.descriptor().parameters()) {
            if (parameter.type() != ParameterType.FILE && parameter.type() != ParameterType.OUTPUT_FILE) {
                continue;
            }
            Object value = resolved.get(parameter.id());
            if (!(value instanceof String text) || text.isBlank() || text.contains("${")) {
                continue;
            }
            Path path = Path.of(text);
            if (!path.isAbsolute()) {
                resolved.put(parameter.id(), project.root().resolve(path).normalize().toString());
            }
        }
        return resolved;
    }

    private static JobRecord waitFor(ToolExecutionService execution, String runId) throws Exception {
        JobRecord job = execution.find(runId);
        long deadline = System.nanoTime() + 3_600_000_000_000L;
        while ((job.status() == JobStatus.QUEUED || job.status() == JobStatus.RUNNING)
                && System.nanoTime() < deadline) {
            Thread.sleep(200);
            job = execution.find(runId);
        }
        return job;
    }

    private static StepOutput readOutput(JobRecord job) throws Exception {
        Path result = job.runDirectory().resolve("result.json");
        JsonNode root = JSON.readTree(result.toFile());
        List<String> outputs = new ArrayList<>();
        for (JsonNode artifact : root.path("artifacts")) {
            String path = artifact.path("path").asText("");
            if (!path.isBlank()) {
                outputs.add(path);
            }
        }
        Map<String, Object> metrics = JSON.convertValue(
                root.path("metrics"), new TypeReference<Map<String, Object>>() {
                });
        return new StepOutput(job.runId(), List.copyOf(outputs), Map.copyOf(metrics));
    }

    private static Map<String, Object> resolveParameters(
            Map<String, Object> parameters,
            Map<String, StepOutput> completed
    ) throws Exception {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            resolved.put(entry.getKey(), resolveValue(entry.getValue(), completed));
        }
        return resolved;
    }

    private static Object resolveValue(Object value, Map<String, StepOutput> completed) throws Exception {
        if (value instanceof String text) {
            return resolveText(text, completed);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), resolveValue(entry.getValue(), completed));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) {
                copy.add(resolveValue(item, completed));
            }
            return copy;
        }
        return value;
    }

    private static String resolveText(String text, Map<String, StepOutput> completed) throws Exception {
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            StepOutput output = completed.get(matcher.group(1));
            if (output == null) {
                throw new IllegalStateException("Dependency has not succeeded: " + matcher.group(1));
            }
            String expression = matcher.group(2);
            String replacement;
            if (expression.equals("runId")) {
                replacement = output.runId();
            } else if (expression.startsWith("metric.")) {
                Object value = output.metrics().get(expression.substring("metric.".length()));
                if (value == null) throw new IllegalStateException("Missing metric: " + expression);
                replacement = String.valueOf(value);
            } else {
                int index = 0;
                if (expression.startsWith("output[")) {
                    index = Integer.parseInt(expression.substring(7, expression.length() - 1));
                }
                if (index < 0 || index >= output.outputs().size()) {
                    throw new IllegalStateException("Missing artifact: " + expression);
                }
                replacement = output.outputs().get(index);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private void log(String message) {
        log.append(message);
        log.append("\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    static WorkflowSpec parse(String text) throws Exception {
        WorkflowSpec spec = JSON.readValue(text, WorkflowSpec.class);
        if (spec.steps().isEmpty()) {
            throw new IllegalArgumentException("Workflow needs at least one step.");
        }
        return spec;
    }

    static List<String> topologicalOrder(WorkflowSpec spec) {
        Map<String, Step> byId = new LinkedHashMap<>();
        for (Step step : spec.steps()) {
            if (step.id() == null || step.id().isBlank()) {
                throw new IllegalArgumentException("Every step needs an id.");
            }
            if (byId.put(step.id(), step) != null) {
                throw new IllegalArgumentException("Duplicate step id: " + step.id());
            }
        }
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> children = new LinkedHashMap<>();
        for (Step step : spec.steps()) {
            indegree.put(step.id(), step.dependsOn().size());
            children.put(step.id(), new ArrayList<>());
        }
        for (Step step : spec.steps()) {
            for (String dependency : step.dependsOn()) {
                if (!byId.containsKey(dependency)) {
                    throw new IllegalArgumentException("Unknown dependency: " + dependency);
                }
                children.get(dependency).add(step.id());
            }
        }
        Deque<String> ready = new ArrayDeque<>();
        for (Step step : spec.steps()) {
            if (indegree.get(step.id()) == 0) ready.add(step.id());
        }
        List<String> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.removeFirst();
            order.add(id);
            for (String child : children.get(id)) {
                int remaining = indegree.merge(child, -1, Integer::sum);
                if (remaining == 0) ready.addLast(child);
            }
        }
        if (order.size() != byId.size()) {
            throw new IllegalArgumentException("Workflow contains a cycle.");
        }
        return order;
    }

    public record WorkflowSpec(String name, boolean continueOnError, List<Step> steps) {
        public WorkflowSpec {
            name = name == null || name.isBlank() ? "workflow" : name;
            steps = steps == null ? List.of() : List.copyOf(steps);
        }
    }

    public record Step(String id, String toolId, Map<String, Object> parameters, List<String> dependsOn) {
        public Step {
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
            dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        }
    }

    private record StepOutput(String runId, List<String> outputs, Map<String, Object> metrics) {
    }

    static final class WorkflowCanvas extends JPanel {
        private WorkflowSpec spec;
        private String error;
        private Map<String, String> statuses = Map.of();
        private final Map<String, Point> positions = new LinkedHashMap<>();
        private String selectedId;
        private Consumer<String> selectionListener = ignored -> { };
        private String dragging;
        private Point dragOffset;

        WorkflowCanvas() {
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    String id = nodeAt(event.getPoint());
                    select(id);
                    dragging = id;
                    if (id != null) {
                        Point point = positions.get(id);
                        dragOffset = new Point(event.getX() - point.x, event.getY() - point.y);
                    }
                }

                @Override public void mouseReleased(MouseEvent event) {
                    dragging = null;
                    dragOffset = null;
                }
            });
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseDragged(MouseEvent event) {
                    if (dragging == null || dragOffset == null) return;
                    positions.put(dragging, new Point(
                            Math.max(0, event.getX() - dragOffset.x),
                            Math.max(0, event.getY() - dragOffset.y)));
                    repaint();
                }
            });
        }

        void setSelectionListener(Consumer<String> listener) {
            selectionListener = listener == null ? ignored -> { } : listener;
        }

        void select(String id) {
            selectedId = id;
            selectionListener.accept(id);
            repaint();
        }

        String selectedId() { return selectedId; }

        void setSpec(WorkflowSpec spec) {
            this.spec = spec;
            this.error = null;
            this.statuses = Map.of();
            positions.keySet().removeIf(id -> spec.steps().stream().noneMatch(step -> step.id().equals(id)));
            if (selectedId != null && spec.steps().stream().noneMatch(step -> step.id().equals(selectedId))) {
                selectedId = null;
                selectionListener.accept(null);
            }
            layoutNodes();
            revalidate();
            repaint();
        }

        private void layoutNodes() {
            if (spec == null) return;
            Map<String, Integer> depth = depths(spec);
            Map<Integer, Integer> row = new LinkedHashMap<>();
            for (Step step : spec.steps()) {
                if (positions.containsKey(step.id())) continue;
                int d = depth.getOrDefault(step.id(), 0);
                int r = row.merge(d, 1, Integer::sum) - 1;
                positions.put(step.id(), new Point(30 + d * 220, 30 + r * 90));
            }
        }

        private String nodeAt(Point point) {
            if (spec == null) return null;
            for (Step step : spec.steps()) {
                Point position = positions.get(step.id());
                if (position != null && point.x >= position.x && point.x <= position.x + 160
                        && point.y >= position.y && point.y <= position.y + 56) {
                    return step.id();
                }
            }
            return null;
        }

        void setError(String error) {
            this.spec = null;
            this.error = error;
            this.selectedId = null;
            this.positions.clear();
            selectionListener.accept(null);
            repaint();
        }

        void setStatuses(Map<String, String> statuses) {
            this.statuses = statuses == null ? Map.of() : Map.copyOf(statuses);
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            if (spec == null) return new Dimension(600, 260);
            int width = 600;
            int height = 260;
            for (Point point : positions.values()) {
                width = Math.max(width, point.x + 200);
                height = Math.max(height, point.y + 100);
            }
            return new Dimension(width, height);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                if (error != null) {
                    g.setColor(new java.awt.Color(150, 60, 60));
                    g.drawString("Invalid workflow: " + error, 18, 30);
                    return;
                }
                if (spec == null) return;
                Map<String, java.awt.Point> points = positions;
                g.setStroke(new java.awt.BasicStroke(1.4f));
                for (Step step : spec.steps()) {
                    java.awt.Point target = points.get(step.id());
                    for (String dependency : step.dependsOn()) {
                        java.awt.Point source = points.get(dependency);
                        if (source == null || target == null) continue;
                        g.setColor(new java.awt.Color(150, 160, 170));
                        QuadCurve2D curve = new QuadCurve2D.Double(
                                source.x + 160, source.y + 28,
                                (source.x + 160 + target.x) / 2.0, (source.y + target.y) / 2.0 - 24,
                                target.x, target.y + 28);
                        g.draw(curve);
                    }
                }
                for (Step step : spec.steps()) {
                    java.awt.Point point = points.get(step.id());
                    String state = statuses.getOrDefault(step.id(), "");
                    g.setColor(switch (state) {
                        case "SUCCEEDED" -> new java.awt.Color(220, 240, 226);
                        case "FAILED" -> new java.awt.Color(250, 224, 224);
                        case "SKIPPED" -> new java.awt.Color(238, 238, 238);
                        default -> new java.awt.Color(232, 241, 247);
                    });
                    g.fillRoundRect(point.x, point.y, 160, 56, 12, 12);
                    boolean selected = step.id().equals(selectedId);
                    g.setColor(selected
                            ? new java.awt.Color(35, 110, 190)
                            : new java.awt.Color(70, 110, 130));
                    g.setStroke(new java.awt.BasicStroke(selected ? 2.6f : 1.2f));
                    g.drawRoundRect(point.x, point.y, 160, 56, 12, 12);
                    g.setStroke(new java.awt.BasicStroke(1.4f));
                    g.setColor(new java.awt.Color(38, 58, 70));
                    g.setFont(getFont().deriveFont(Font.BOLD, 13f));
                    g.drawString(step.id(), point.x + 12, point.y + 22);
                    g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
                    g.drawString(step.toolId(), point.x + 12, point.y + 40);
                    if (!state.isBlank()) {
                        g.drawString(state, point.x + 110, point.y + 40);
                    }
                }
            } finally {
                g.dispose();
            }
        }

        private static Map<String, Integer> depths(WorkflowSpec spec) {
            Map<String, Integer> depths = new LinkedHashMap<>();
            for (String id : topologicalOrder(spec)) {
                int depth = 0;
                for (Step step : spec.steps()) {
                    if (step.id().equals(id)) {
                        for (String dependency : step.dependsOn()) {
                            depth = Math.max(depth, depths.getOrDefault(dependency, 0) + 1);
                        }
                    }
                }
                depths.put(id, depth);
            }
            return depths;
        }
    }
}
