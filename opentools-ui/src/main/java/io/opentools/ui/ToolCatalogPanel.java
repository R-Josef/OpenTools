package io.opentools.ui;

import io.opentools.core.job.ToolExecutionService;
import io.opentools.core.i18n.LanguageManager;
import io.opentools.api.tool.InteractiveOpenTool;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolPanel;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolUiContext;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.plugin.host.ToolTextResolver;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class ToolCatalogPanel extends JPanel {
    private static final List<String> CATEGORY_ORDER = List.of(
            "Sequence",
            "Genome annotation",
            "Table",
            "Alignment",
            "Phylogeny",
            "Comparative genomics",
            "Expression",
            "Enrichment",
            "HTS",
            "Visualization",
            "Annotation",
            "Plugin"
    );
    private final ToolRegistry registry;
    private final LanguageManager language;
    private final ToolTextResolver toolText;
    private final DefaultListModel<CatalogRow> model = new DefaultListModel<>();
    private final JList<CatalogRow> list = new JList<>(model);
    private final JTextField filter = new JTextField();
    private final JComboBox<CategoryChoice> categoryFilter = new JComboBox<>();
    private final JLabel count = new JLabel(" ");
    private final ToolFormPanel form;
    private final JPanel editorPanel = new JPanel();
    private final CardLayout editorCards = new CardLayout();
    private final ToolRunSubmitter submitter;
    private final Runnable afterSubmit;
    private ToolPanel interactivePanel;
    private String displayedToolId;
    private String activeFilter = "";
    private String activeCategory;
    private boolean updatingCategoryFilter;

    ToolCatalogPanel(
            ToolRegistry registry,
            ToolExecutionService executionService,
            LanguageManager language,
            ToolTextResolver toolText,
            ToolRunSubmitter submitter,
            Runnable afterSubmit
    ) {
        this.registry = registry;
        this.language = language;
        this.toolText = toolText;
        this.submitter = submitter;
        this.afterSubmit = afterSubmit;
        setLayout(new BorderLayout(12, 0));
        setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        this.form = new ToolFormPanel(executionService, language, toolText, afterSubmit);
        editorPanel.setLayout(editorCards);
        editorPanel.add(form, "form");
        UiAccessibility.name(filter, language.text("tools.search_placeholder", "Search tools"));
        UiAccessibility.name(list, language.text("tools.title", "Tool catalog"));

        JPanel sidebar = new JPanel(new BorderLayout(0, 8));
        JLabel title = new JLabel(language.text("tools.title", "Tool catalog"));
        title.setFont(title.getFont().deriveFont(title.getFont().getSize2D() + 5f));
        filter.putClientProperty(
                "JTextField.placeholderText",
                language.text("tools.search_placeholder", "Search tools, formats, or aliases")
        );
        filter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent event) {
                applySearch(filter.getText());
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent event) {
                applySearch(filter.getText());
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent event) {
                applySearch(filter.getText());
            }
        });

        categoryFilter.setRenderer((combo, value, index, selected, focus) -> {
            JLabel label = new JLabel(value == null ? "" : value.label());
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));
            if (selected) {
                label.setBackground(combo.getSelectionBackground());
                label.setForeground(combo.getSelectionForeground());
            } else {
                label.setBackground(combo.getBackground());
                label.setForeground(combo.getForeground());
            }
            return label;
        });
        categoryFilter.addActionListener(event -> {
            if (!updatingCategoryFilter && categoryFilter.getSelectedItem() instanceof CategoryChoice choice) {
                activeCategory = choice.category();
                reload();
            }
        });
        JPanel categoryRow = new JPanel(new BorderLayout(8, 0));
        categoryRow.add(new JLabel(language.text("tools.category", "Category")), BorderLayout.WEST);
        categoryRow.add(categoryFilter, BorderLayout.CENTER);

        JPanel controls = new JPanel(new GridLayout(0, 1, 0, 6));
        controls.add(filter);
        controls.add(categoryRow);
        controls.add(count);
        JPanel top = new JPanel(new BorderLayout(0, 9));
        top.add(title, BorderLayout.NORTH);
        top.add(controls, BorderLayout.CENTER);
        sidebar.add(top, BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(-1);
        list.setCellRenderer((jlist, value, index, selected, focus) -> {
            if (value.header()) {
                JPanel header = new JPanel(new BorderLayout(8, 0));
                header.setOpaque(true);
                header.setBackground(new Color(241, 244, 246));
                header.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(218, 222, 225)),
                        BorderFactory.createEmptyBorder(8, 8, 5, 8)
                ));
                JLabel categoryName = new JLabel(localizedCategory(value.categoryHeader()));
                categoryName.setFont(categoryName.getFont().deriveFont(java.awt.Font.BOLD));
                JLabel categoryCount = new JLabel(toolCount(value.categoryCount()));
                categoryCount.setForeground(new Color(105, 111, 116));
                header.add(categoryName, BorderLayout.WEST);
                header.add(categoryCount, BorderLayout.EAST);
                UiAccessibility.name(
                        header,
                        localizedCategory(value.categoryHeader()) + ", "
                                + toolCount(value.categoryCount())
                );
                return header;
            }
            JPanel row = new JPanel(new GridLayout(2, 1, 0, 1));
            row.setOpaque(true);
            row.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            ToolDescriptor tool = value.tool();
            JLabel name = new JLabel(toolText.name(tool));
            JLabel meta = new JLabel(localizedCategory(tool.category()) + " · " + tool.id());
            meta.setForeground(new Color(105, 111, 116));
            meta.setFont(meta.getFont().deriveFont(meta.getFont().getSize2D() - 1f));
            row.add(name);
            row.add(meta);
            if (selected) {
                row.setBackground(jlist.getSelectionBackground());
                name.setForeground(jlist.getSelectionForeground());
                meta.setForeground(jlist.getSelectionForeground());
            } else {
                row.setBackground(jlist.getBackground());
            }
            return row;
        });
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                CatalogRow selected = list.getSelectedValue();
                if (selected != null && selected.header()) {
                    int next = nextToolIndex(list.getSelectedIndex() + 1);
                    if (next >= 0) {
                        int target = next;
                        SwingUtilities.invokeLater(() -> list.setSelectedIndex(target));
                    }
                    return;
                }
                showEditor(selected == null ? null : selected.tool());
            }
        });
        JScrollPane listScroll = new JScrollPane(list);
        sidebar.add(listScroll, BorderLayout.CENTER);
        listScroll.setPreferredSize(new Dimension(310, 500));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, editorPanel);
        split.setDividerLocation(320);
        split.setDividerSize(7);
        split.setResizeWeight(0);
        add(split, BorderLayout.CENTER);
        reload();
    }

    void reload() {
        List<ToolDescriptor> matching = registry.search(activeFilter);
        List<ToolDescriptor> allTools = registry.list();
        SwingUtilities.invokeLater(() -> {
            ToolDescriptor selected = selectedTool();
            rebuildCategoryChoices(allTools);
            List<ToolDescriptor> visible = matching.stream()
                    .filter(tool -> activeCategory == null
                            || tool.category().equalsIgnoreCase(activeCategory))
                    .toList();
            List<ToolDescriptor> tools = orderedTools(
                    visible,
                    toolText::name
            );
            model.clear();
            Map<String, List<ToolDescriptor>> grouped = new LinkedHashMap<>();
            for (ToolDescriptor tool : tools) {
                grouped.computeIfAbsent(tool.category(), ignored -> new ArrayList<>()).add(tool);
            }
            grouped.forEach((category, values) -> {
                model.addElement(CatalogRow.header(category, values.size()));
                values.forEach(tool -> model.addElement(CatalogRow.tool(tool)));
            });
            String toolCount = toolCount(tools.size());
            count.setText(activeCategory == null
                    ? toolCount
                    : localizedCategory(activeCategory) + " · " + toolCount);
            if (selected != null) {
                for (int i = 0; i < model.size(); i++) {
                    CatalogRow row = model.get(i);
                    if (!row.header() && row.tool().id().equals(selected.id())) {
                        list.setSelectedIndex(i);
                        break;
                    }
                }
            }
            if (selected != null && !selected.id().equals(displayedToolId)) {
                showEditor(selected);
            }
            if (selectedTool() == null) {
                int first = nextToolIndex(0);
                if (first >= 0) {
                    list.setSelectedIndex(first);
                } else {
                    form.setTool(null);
                }
            }
        });
    }

    void applySearch(String query) {
        activeFilter = query == null ? "" : query.trim();
        if (!filter.getText().equals(query == null ? "" : query)) {
            filter.setText(activeFilter);
        }
        reload();
    }

    void applyGlobalSearch(String query) {
        activeCategory = null;
        updateCategorySelection();
        applySearch(query);
    }

    void rebind(ToolExecutionService executionService) {
        form.setExecutionService(executionService);
        displayedToolId = null;
        disposeEditorContent();
        reload();
    }

    private void showEditor(ToolDescriptor tool) {
        disposeEditorContent();
        displayedToolId = tool == null ? null : tool.id();
        form.setTool(tool);
        if (tool == null) {
            editorCards.show(editorPanel, "form");
            return;
        }
        if (registry.find(tool.id()).orElse(null) instanceof InteractiveOpenTool interactive) {
            try {
                ToolPanel panel = interactive.createPanel(new HostToolUiContext(tool));
                interactivePanel = panel;
                editorPanel.add(panel, "interactive");
                editorCards.show(editorPanel, "interactive");
                SwingUtilities.invokeLater(panel::requestFocusInWindow);
                return;
            } catch (Exception ex) {
                JLabel error = new JLabel(language.text(
                        "tools.interactive_failed",
                        "Interactive tool page failed: {0}",
                        rootMessage(ex)
                ));
                error.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
                editorPanel.add(error, "error");
                editorCards.show(editorPanel, "error");
                return;
            }
        }
        editorCards.show(editorPanel, "form");
    }

    private void disposeEditorContent() {
        if (interactivePanel != null) {
            interactivePanel.close();
            interactivePanel = null;
        }
        for (Component component : editorPanel.getComponents()) {
            if (component != form) {
                editorPanel.remove(component);
            }
        }
        editorPanel.revalidate();
        editorPanel.repaint();
    }

    @Override
    public void removeNotify() {
        disposeEditorContent();
        super.removeNotify();
    }

    private final class HostToolUiContext implements ToolUiContext {
        private final ToolDescriptor tool;

        private HostToolUiContext(ToolDescriptor tool) {
            this.tool = tool;
        }

        @Override
        public ToolDescriptor tool() {
            return tool;
        }

        @Override
        public String languageCode() {
            return language.activeCode();
        }

        @Override
        public String text(String key, String fallback, Object... arguments) {
            return toolText.text(tool, key, fallback, arguments);
        }

        @Override
        public String submit(ToolRequest request) throws Exception {
            String runId = submitter.submit(request);
            SwingUtilities.invokeLater(afterSubmit);
            return runId;
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    static List<ToolDescriptor> orderedTools(
            List<ToolDescriptor> tools,
            Function<ToolDescriptor, String> displayName
    ) {
        return tools.stream()
                .sorted(Comparator
                        .comparingInt((ToolDescriptor tool) -> categoryRank(tool.category()))
                        .thenComparing(ToolDescriptor::category, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void rebuildCategoryChoices(List<ToolDescriptor> tools) {
        String requested = activeCategory;
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<ToolDescriptor> ordered = orderedTools(
                tools,
                ToolDescriptor::name
        );
        for (ToolDescriptor tool : ordered) {
            counts.merge(tool.category(), 1, Integer::sum);
        }

        updatingCategoryFilter = true;
        try {
            categoryFilter.removeAllItems();
            categoryFilter.addItem(new CategoryChoice(
                    null,
                    language.text(
                            "tools.category_choice",
                            "{0} ({1})",
                            language.text("tools.all_categories", "All categories"),
                            tools.size()
                    )
            ));
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                categoryFilter.addItem(new CategoryChoice(
                        entry.getKey(),
                        language.text(
                                "tools.category_choice",
                                "{0} ({1})",
                                localizedCategory(entry.getKey()),
                                entry.getValue()
                        )
                ));
            }
            if (requested != null && !counts.containsKey(requested)) {
                activeCategory = null;
            }
            updateCategorySelection();
        } finally {
            updatingCategoryFilter = false;
        }
    }

    private void updateCategorySelection() {
        for (int index = 0; index < categoryFilter.getItemCount(); index++) {
            CategoryChoice choice = categoryFilter.getItemAt(index);
            if (java.util.Objects.equals(choice.category(), activeCategory)) {
                categoryFilter.setSelectedIndex(index);
                return;
            }
        }
    }

    private ToolDescriptor selectedTool() {
        CatalogRow selected = list.getSelectedValue();
        return selected == null || selected.header() ? null : selected.tool();
    }

    private int nextToolIndex(int start) {
        for (int index = Math.max(0, start); index < model.size(); index++) {
            if (!model.get(index).header()) {
                return index;
            }
        }
        return -1;
    }

    private String localizedCategory(String category) {
        return toolText.category(category);
    }

    private String toolCount(int count) {
        return count == 1
                ? language.text("tools.count_one", "{0} tool", count)
                : language.text("tools.count", "{0} tools", count);
    }

    private static int categoryRank(String category) {
        for (int index = 0; index < CATEGORY_ORDER.size(); index++) {
            if (CATEGORY_ORDER.get(index).equalsIgnoreCase(category)) {
                return index;
            }
        }
        return CATEGORY_ORDER.size();
    }

    private record CategoryChoice(String category, String label) {
    }

    private record CatalogRow(
            ToolDescriptor tool,
            String categoryHeader,
            int categoryCount
    ) {
        static CatalogRow tool(ToolDescriptor tool) {
            return new CatalogRow(tool, null, 0);
        }

        static CatalogRow header(String category, int count) {
            return new CatalogRow(null, category, count);
        }

        boolean header() {
            return categoryHeader != null;
        }
    }
}
