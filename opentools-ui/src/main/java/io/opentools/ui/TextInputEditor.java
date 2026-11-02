package io.opentools.ui;

import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.core.i18n.LanguageManager;
import io.opentools.core.workspace.Project;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;

final class TextInputEditor {
    static final long MAX_EDITABLE_BYTES = 8L * 1024L * 1024L;
    private static final List<String> TEXT_FORMAT_MARKERS = List.of(
            "fasta", "fastq", "sam", "vcf", "gff", "gtf", "bed",
            "tsv", "csv", "tab", "newick", "xml", "hmm", "text",
            "plain", "json", "yaml", "phylogeny", "sequence", "table"
    );

    private TextInputEditor() {
    }

    static boolean supports(ToolDescriptor tool, ToolParameter parameter) {
        if (parameter.type() != ParameterType.FILE) {
            return false;
        }
        String parameterId = parameter.id().toLowerCase(Locale.ROOT);
        String parameterSignal = joined(
                parameterId,
                parameter.title(),
                parameter.description()
        );
        if (parameterSignal.contains("executable")
                || parameterSignal.contains("binary")
                || parameterId.endsWith("path")) {
            return false;
        }
        String formats = String.join(" ", tool.inputFormats()).toLowerCase(Locale.ROOT);
        if (formats.contains("bam") || formats.contains("bcf") || formats.contains("cram")) {
            return false;
        }
        String signal = parameterSignal + " " + formats;
        return TEXT_FORMAT_MARKERS.stream().anyMatch(signal::contains);
    }

    static boolean edit(
            Component parent,
            LanguageManager language,
            Project project,
            JTextField field,
            ToolDescriptor tool,
            ToolParameter parameter,
            String title
    ) {
        String content = "";
        String selectedPath = field.getText() == null ? "" : field.getText().trim();
        if (!selectedPath.isBlank()) {
            try {
                Path selected = Path.of(selectedPath);
                if (Files.isRegularFile(selected)) {
                    if (Files.size(selected) > MAX_EDITABLE_BYTES) {
                        showMessage(
                                parent,
                                language,
                                language.text(
                                        "form.text_editor_too_large",
                                        "The input file is larger than {0} MB and cannot be opened in the text editor.",
                                        MAX_EDITABLE_BYTES / (1024L * 1024L)
                                )
                        );
                        return false;
                    }
                    content = Files.readString(selected, StandardCharsets.UTF_8);
                }
            } catch (Exception ex) {
                showMessage(
                        parent,
                        language,
                        language.text(
                                "form.text_editor_read_failed",
                                "Could not read the input file: {0}",
                                rootMessage(ex)
                        )
                );
                return false;
            }
        }

        JTextArea editor = new JTextArea(content, 24, 88);
        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, editor.getFont().getSize()));
        editor.setLineWrap(false);
        TextContextMenus.install(editor, language);
        JScrollPane scroll = new JScrollPane(editor);
        scroll.setPreferredSize(new Dimension(880, 520));
        String confirm = language.text("form.text_editor_ok", "OK");
        String cancel = language.text("form.text_editor_cancel", "Cancel");
        int choice = JOptionPane.showOptionDialog(
                parent,
                scroll,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                new Object[]{confirm, cancel},
                confirm
        );
        if (choice != 0) {
            return false;
        }

        try {
            Path temporary = temporaryInputPath(project, tool, parameter);
            Files.createDirectories(temporary.getParent());
            Files.writeString(
                    temporary,
                    editor.getText(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            field.setText(temporary.toAbsolutePath().normalize().toString());
            field.setCaretPosition(field.getText().length());
            return true;
        } catch (Exception ex) {
            showMessage(
                    parent,
                    language,
                    language.text(
                            "form.text_editor_write_failed",
                            "Could not save the temporary input file: {0}",
                            rootMessage(ex)
                    )
            );
            return false;
        }
    }

    private static Path temporaryInputPath(
            Project project,
            ToolDescriptor tool,
            ToolParameter parameter
    ) {
        String name = safeName(tool.id()) + "-" + safeName(parameter.id()) + ".txt";
        return project.metadataDirectory().resolve("input-editor").resolve(name);
    }

    private static String safeName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String joined(String... values) {
        return String.join(" ", values).toLowerCase(Locale.ROOT);
    }

    private static void showMessage(Component parent, LanguageManager language, String message) {
        JOptionPane.showMessageDialog(
                parent,
                message,
                language.text("form.input_text", "Enter text"),
                JOptionPane.WARNING_MESSAGE
        );
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
