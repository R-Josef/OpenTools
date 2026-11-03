package io.opentools.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentools.core.job.JobRecord;
import io.opentools.core.job.ToolExecutionService;
import io.opentools.core.job.ToolValidationException;
import io.opentools.core.i18n.LanguageManager;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.FormatDetection;
import io.opentools.formats.FormatDetector;
import io.opentools.plugin.host.ToolTextResolver;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

final class ToolFormPanel extends JPanel {
    private ToolExecutionService executionService;
    private final LanguageManager language;
    private final ToolTextResolver toolText;
    private final Runnable afterSubmit;
    private final Map<String, Supplier<Object>> valueReaders = new LinkedHashMap<>();
    private final JLabel title = new JLabel();
    private final JLabel description = new JLabel(" ");
    private final JPanel fieldsPanel = new JPanel(new GridBagLayout());
    private final JTextArea validationArea = new JTextArea(4, 20);
    private final JButton validateButton = new JButton();
    private final JButton runButton = new JButton();
    private final JLabel fileHint = new JLabel(" ");
    private ToolDescriptor tool;
    private int row;

    ToolFormPanel(
            ToolExecutionService executionService,
            LanguageManager language,
            ToolTextResolver toolText,
            Runnable afterSubmit
    ) {
        this.executionService = executionService;
        this.language = language;
        this.toolText = toolText;
        this.afterSubmit = afterSubmit;
        validateButton.setText(language.text("form.validate", "Validate"));
        runButton.setText(language.text("form.run", "Run"));
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JPanel header = new JPanel(new BorderLayout(0, 5));
        title.setFont(title.getFont().deriveFont(title.getFont().getSize2D() + 7f));
        description.setForeground(new Color(92, 99, 105));
        header.add(title, BorderLayout.NORTH);
        header.add(description, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        fieldsPanel.setBorder(BorderFactory.createEmptyBorder(6, 0, 8, 0));
        JScrollPane scrollPane = new JScrollPane(fieldsPanel);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(218, 222, 225)));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        validationArea.setEditable(false);
        validationArea.setLineWrap(true);
        validationArea.setWrapStyleWord(true);
        UiAccessibility.name(
                validationArea,
                language.text("form.validation_results", "Validation results")
        );
        UiAccessibility.name(validateButton, language.text("form.validate", "Validate"));
        UiAccessibility.name(runButton, language.text("form.run", "Run"));
        validationArea.setBackground(new Color(248, 249, 249));
        validationArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel footer = new JPanel(new BorderLayout(0, 8));
        JScrollPane validationScroll = new JScrollPane(validationArea);
        validationScroll.setPreferredSize(new Dimension(100, 95));
        footer.add(validationScroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout());
        actions.add(fileHint, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(validateButton);
        buttons.add(runButton);
        actions.add(buttons, BorderLayout.EAST);
        footer.add(actions, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);

        validateButton.addActionListener(ignored -> validateOnly());
        runButton.addActionListener(ignored -> submit());
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("ctrl ENTER"), "run-tool");
        getActionMap().put("run-tool", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                if (runButton.isEnabled()) {
                    submit();
                }
            }
        });
        setTool(null);
    }

    void setExecutionService(ToolExecutionService executionService) {
        this.executionService = executionService;
        validationArea.setText("");
    }

    void setTool(ToolDescriptor tool) {
        this.tool = tool;
        fieldsPanel.removeAll();
        valueReaders.clear();
        row = 0;
        validateButton.setEnabled(tool != null);
        runButton.setEnabled(tool != null);
        if (tool == null) {
            title.setText(language.text("tools.select_tool", "Select a tool"));
            description.setText(language.text(
                    "tools.select_tool_hint",
                    "Choose a tool from the catalog to configure it."
            ));
            fileHint.setText(" ");
            validationArea.setText("");
            fieldsPanel.revalidate();
            fieldsPanel.repaint();
            return;
        }
        title.setText(toolText.name(tool));
        description.setText(tool.id() + "  · "
                + toolText.description(tool));
        validationArea.setText("");
        for (ToolParameter parameter : tool.parameters()) {
            if (!parameter.advanced()) {
                addParameter(parameter);
            }
        }
        List<ToolParameter> advanced = tool.parameters().stream().filter(ToolParameter::advanced).toList();
        if (!advanced.isEmpty()) {
            JLabel separator = new JLabel(language.text("tools.advanced", "Advanced options"));
            separator.setForeground(new Color(100, 106, 111));
            separator.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(218, 222, 225)));
            GridBagConstraints separatorConstraints = baseConstraints();
            separatorConstraints.gridwidth = 2;
            separatorConstraints.insets = new Insets(18, 0, 5, 0);
            fieldsPanel.add(separator, separatorConstraints);
            row++;
            advanced.forEach(this::addParameter);
        }
        fieldsPanel.revalidate();
        fieldsPanel.repaint();
        TextContextMenus.install(fieldsPanel, language);
    }

    private void addParameter(ToolParameter parameter) {
        String parameterTitle = toolText.parameterTitle(tool, parameter);
        String parameterDescription = toolText.parameterDescription(tool, parameter);
        JLabel label = new JLabel(parameterTitle + (parameter.required() ? " *" : ""));
        label.setToolTipText(parameterDescription);
        GridBagConstraints labelConstraints = baseConstraints();
        labelConstraints.anchor = GridBagConstraints.NORTHWEST;
        labelConstraints.insets = new Insets(8, 0, 8, 14);
        fieldsPanel.add(label, labelConstraints);

        JPanel control = new JPanel(new BorderLayout(6, 4));
        control.setOpaque(false);
        switch (parameter.type()) {
            case FILE -> addFileControl(control, parameter, false);
            case OUTPUT_FILE -> addFileControl(control, parameter, true);
            case DIRECTORY -> addDirectoryControl(control, parameter);
            case TEXT -> addTextControl(control, parameter);
            case ENUM -> addEnumControl(control, parameter);
            case BOOLEAN -> addBooleanControl(control, parameter);
            case INTEGER -> addIntegerControl(control, parameter);
            case DECIMAL, STRING -> addStringControl(control, parameter);
        }
        JLabel help = new JLabel(parameterDescription);
        help.setForeground(new Color(112, 118, 122));
        help.setFont(help.getFont().deriveFont(help.getFont().getSize2D() - 1f));
        help.setBorder(BorderFactory.createEmptyBorder(0, 1, 0, 0));
        control.add(help, BorderLayout.SOUTH);
        UiAccessibility.nameTree(control, parameterTitle, parameterDescription);

        GridBagConstraints controlConstraints = baseConstraints();
        controlConstraints.gridx = 1;
        controlConstraints.weightx = 1;
        controlConstraints.fill = GridBagConstraints.HORIZONTAL;
        controlConstraints.insets = new Insets(8, 0, 8, 0);
        fieldsPanel.add(control, controlConstraints);
        row++;
    }

    private void addFileControl(JPanel control, ToolParameter parameter, boolean output) {
        JTextField field = new JTextField(textDefault(parameter.defaultValue()));
        useAvailableWidth(field);
        JButton browse = new JButton(output
                ? language.text("form.save_file", "Save to...")
                : language.text("form.choose_file", "Choose..."));
        control.add(field, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(browse);
        if (!output) {
            JButton useResult = new JButton(language.text("form.use_result", "Use result..."));
            buttons.add(useResult);
            useResult.addActionListener(ignored -> chooseRecentResult(field));
        }
        if (!output && TextInputEditor.supports(tool, parameter)) {
            JButton textInput = new JButton(language.text("form.input_text", "Enter text"));
            buttons.add(textInput);
            textInput.addActionListener(ignored -> {
                String title = language.text(
                        "form.text_editor_title",
                        "Edit input text: {0}",
                        toolText.parameterTitle(tool, parameter)
                );
                MainFrame frame = (MainFrame) SwingUtilities.getWindowAncestor(this);
                if (frame != null && TextInputEditor.edit(
                        this,
                        language,
                        frame.session().project(),
                        field,
                        tool,
                        parameter,
                        title
                )) {
                    updateFileHint(field.getText());
                }
            });
        }
        control.add(buttons, BorderLayout.EAST);
        valueReaders.put(parameter.id(), field::getText);
        browse.addActionListener(ignored -> chooseFile(field, output));
        if (!output) {
            installDrop(field);
            field.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusLost(java.awt.event.FocusEvent event) {
                    updateFileHint(field.getText());
                }
            });
        }
    }

    private void addDirectoryControl(JPanel control, ToolParameter parameter) {
        JTextField field = new JTextField(textDefault(parameter.defaultValue()));
        useAvailableWidth(field);
        JButton browse = new JButton(language.text("form.choose_directory", "Choose directory..."));
        control.add(field, BorderLayout.CENTER);
        control.add(browse, BorderLayout.EAST);
        valueReaders.put(parameter.id(), field::getText);
        browse.addActionListener(ignored -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
    }

    private void addTextControl(JPanel control, ToolParameter parameter) {
        JTextArea area = new JTextArea(textDefault(parameter.defaultValue()), 5, 30);
        area.setLineWrap(false);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(100, 105));
        control.add(scroll, BorderLayout.CENTER);
        valueReaders.put(parameter.id(), area::getText);
    }

    private void addEnumControl(JPanel control, ToolParameter parameter) {
        JComboBox<String> combo = new JComboBox<>(parameter.options().toArray(String[]::new));
        if (parameter.defaultValue() != null) {
            combo.setSelectedItem(String.valueOf(parameter.defaultValue()));
        }
        control.add(combo, BorderLayout.CENTER);
        valueReaders.put(parameter.id(), combo::getSelectedItem);
    }

    private void addBooleanControl(JPanel control, ToolParameter parameter) {
        JCheckBox checkBox = new JCheckBox();
        checkBox.setSelected(Boolean.parseBoolean(String.valueOf(parameter.defaultValue())));
        control.add(checkBox, BorderLayout.WEST);
        valueReaders.put(parameter.id(), checkBox::isSelected);
    }

    private void addIntegerControl(JPanel control, ToolParameter parameter) {
        int value = parameter.defaultValue() instanceof Number number ? number.intValue() : 0;
        int min = parameter.min() == null ? Integer.MIN_VALUE : parameter.min().intValue();
        int max = parameter.max() == null ? Integer.MAX_VALUE : parameter.max().intValue();
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        control.add(spinner, BorderLayout.WEST);
        valueReaders.put(parameter.id(), spinner::getValue);
    }

    private void addStringControl(JPanel control, ToolParameter parameter) {
        JTextField field = new JTextField(textDefault(parameter.defaultValue()));
        field.setToolTipText(parameter.placeholder());
        control.add(field, BorderLayout.CENTER);
        valueReaders.put(parameter.id(), field::getText);
    }

    private void chooseFile(JTextField field, boolean save) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(
                language.text("form.sequence_filter", "Sequence and text files"),
                "fa", "fasta", "fna", "txt", "tsv", "gz"
        ));
        int result = save ? chooser.showSaveDialog(this) : chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getAbsolutePath());
            if (!save) {
                updateFileHint(field.getText());
            }
        }
    }

    private void installDrop(JTextField field) {
        new DropTarget(field, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent event) {
                try {
                    event.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        field.setText(files.getFirst().getAbsolutePath());
                        updateFileHint(field.getText());
                    }
                    event.dropComplete(true);
                } catch (Exception ex) {
                    event.dropComplete(false);
                }
            }
        });
    }

    private void chooseRecentResult(JTextField field) {
        List<Path> artifacts = recentArtifacts();
        if (artifacts.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    language.text("form.no_results", "No completed job artifacts are available yet."),
                    language.text("form.use_result", "Use result..."),
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }
        Object[] choices = artifacts.stream().map(Path::toString).toArray();
        Object selected = JOptionPane.showInputDialog(
                this,
                language.text("form.use_result_prompt", "Choose an artifact from a recent job:"),
                language.text("form.use_result", "Use result..."),
                JOptionPane.PLAIN_MESSAGE,
                null,
                choices,
                choices[0]
        );
        if (selected != null) {
            field.setText(selected.toString());
            updateFileHint(field.getText());
        }
    }

    private List<Path> recentArtifacts() {
        List<Path> paths = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        try {
            for (JobRecord job : executionService.recentJobs(20)) {
                Path result = job.runDirectory().resolve("result.json");
                if (!Files.isRegularFile(result)) {
                    continue;
                }
                JsonNode root = mapper.readTree(result.toFile());
                for (JsonNode artifact : root.path("artifacts")) {
                    String value = artifact.path("path").asText("");
                    if (value.isBlank()) {
                        continue;
                    }
                    Path path = Path.of(value);
                    if (Files.isRegularFile(path) && !paths.contains(path)) {
                        paths.add(path);
                        if (paths.size() >= 50) {
                            return paths;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            // A damaged historical result must not block the form.
        }
        return paths;
    }

    private void updateFileHint(String value) {
        if (value == null || value.isBlank()) {
            fileHint.setText(" ");
            return;
        }
        Path path = Path.of(value);
        if (!Files.isRegularFile(path)) {
            fileHint.setText(language.text("form.file_missing", "File does not exist"));
            return;
        }
        try {
            FormatDetection detection = new FormatDetector().detect(path);
            fileHint.setText(language.text("form.format_detected", "Detected format: {0}",
                    detection.type().displayName()));
        } catch (Exception ex) {
            fileHint.setText(language.text("form.format_unknown", "Cannot detect format"));
        }
    }

    private void validateOnly() {
        ToolRequest request = request();
        ValidationResult result = executionService.validate(request);
        showValidation(result);
    }

    private void submit() {
        ToolRequest request = request();
        ValidationResult validation = executionService.validate(request);
        showValidation(validation);
        if (!validation.valid()) {
            return;
        }
        runButton.setEnabled(false);
        runButton.setText(language.text("form.submitting", "Submitting..."));
        Thread.ofVirtual().start(() -> {
            try {
                String runId = ((MainFrame) SwingUtilities.getWindowAncestor(this))
                        .session().submit(request);
                SwingUtilities.invokeLater(() -> {
                    validationArea.setText(language.text("form.job_submitted", "Job submitted\nRun ID: {0}", runId));
                    runButton.setText(language.text("form.run", "Run"));
                    runButton.setEnabled(true);
                    afterSubmit.run();
                });
            } catch (ToolValidationException ex) {
                SwingUtilities.invokeLater(() -> {
                    showValidation(ex.validationResult());
                    runButton.setText(language.text("form.run", "Run"));
                    runButton.setEnabled(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    validationArea.setText(language.text("form.submit_failed", "Submission failed\n{0}", rootMessage(ex)));
                    runButton.setText(language.text("form.run", "Run"));
                    runButton.setEnabled(true);
                });
            }
        });
    }

    private ToolRequest request() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        valueReaders.forEach((id, reader) -> parameters.put(id, reader.get()));
        return new ToolRequest(tool.id(), parameters);
    }

    private void showValidation(ValidationResult result) {
        if (result.issues().isEmpty()) {
            validationArea.setText(language.text("form.validation_passed", "Input validation passed."));
            return;
        }
        StringBuilder text = new StringBuilder();
        for (ValidationIssue issue : result.issues()) {
            text.append(issue.severity()).append(" [").append(issue.field()).append("] ")
                    .append(issue.message()).append('\n');
            if (issue.suggestion() != null && !issue.suggestion().isBlank()) {
                text.append("  ").append(issue.suggestion()).append('\n');
            }
        }
        validationArea.setText(text.toString());
        validationArea.setCaretPosition(0);
    }

    private GridBagConstraints baseConstraints() {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        return constraints;
    }

    private static String textDefault(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static void useAvailableWidth(JTextField field) {
        // Let the form layout consume the remaining width instead of sizing to path length.
        field.setColumns(1);
        field.setMinimumSize(new Dimension(0, field.getPreferredSize().height));
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
