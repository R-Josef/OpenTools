package io.opentools.ui;

import io.opentools.core.i18n.LanguageManager;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.plugin.host.PluginManager;
import io.opentools.plugin.host.ToolTextResolver;
import io.opentools.plugin.host.ToolDocumentationPaths;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class DocsPanel extends JPanel {
    private static final List<DocGroup> GROUPS = List.of(
            new DocGroup("sequence", "Sequence", "categories/sequence.md"),
            new DocGroup("annotation", "Genome annotation+Annotation",
                    "categories/annotation-bed.md"),
            new DocGroup("table", "Table", "categories/table.md"),
            new DocGroup("alignment", "Alignment+Phylogeny+Comparative genomics",
                    "categories/alignment-phylogeny.md"),
            new DocGroup("expression", "Expression+Enrichment",
                    "categories/expression-enrichment.md"),
            new DocGroup("hts", "HTS", "categories/hts.md"),
            new DocGroup("visualization", "Visualization", "categories/visualization.md"),
            new DocGroup("plugin", "Plugin", "categories/plugin.md")
    );

    private final ToolRegistry registry;
    private final LanguageManager language;
    private final ToolTextResolver toolText;
    private final PluginManager plugins;
    private final String docsLanguage;
    private final JEditorPane viewer = new JEditorPane();
    private final JScrollPane viewerScroll = new JScrollPane(viewer);
    private final JTextField filter = new JTextField();
    private final JLabel location = new JLabel();
    private JTree navigation;
    private String activeDocument = "README.md";

    DocsPanel(ToolRegistry registry, LanguageManager language) {
        this(registry, language, null, ToolTextResolver.hostOnly(language));
    }

    DocsPanel(ToolRegistry registry, LanguageManager language, ToolTextResolver toolText) {
        this(registry, language, null, toolText);
    }

    DocsPanel(
            ToolRegistry registry,
            LanguageManager language,
            PluginManager plugins,
            ToolTextResolver toolText
    ) {
        this.registry = registry;
        this.language = language;
        this.plugins = plugins;
        this.toolText = toolText;
        this.docsLanguage = DocResources.docsLanguage(language.activeCode());
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        add(createSidebar(), BorderLayout.WEST);
        add(createViewer(), BorderLayout.CENTER);
        viewer.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        viewer.setContentType("text/html");
        viewer.setEditable(false);
        viewer.setOpaque(true);
        viewer.setBackground(Color.WHITE);
        viewer.addHyperlinkListener(event -> {
            if (event.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                followLink(event.getDescription());
            }
        });
        rebuildNavigation();
        openDocument("README.md");
    }

    private JPanel createSidebar() {
        JPanel panel = new JPanel(new BorderLayout(0, 9));
        panel.setPreferredSize(new Dimension(UiScaling.px(270), 100));
        panel.setMinimumSize(new Dimension(UiScaling.px(210), 100));
        JPanel heading = new JPanel(new BorderLayout(8, 0));
        JLabel title = new JLabel(t("docs.title", "Documentation"));
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 5f));
        JButton home = new JButton(t("docs.home", "Home"));
        home.addActionListener(ignored -> openDocument("README.md"));
        heading.add(title, BorderLayout.WEST);
        heading.add(home, BorderLayout.EAST);

        filter.putClientProperty(
                "JTextField.placeholderText",
                t("docs.search", "Search documentation and tools")
        );
        UiAccessibility.name(filter, t("docs.search", "Search documentation and tools"));
        filter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                rebuildNavigation();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                rebuildNavigation();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                rebuildNavigation();
            }
        });

        JPanel top = new JPanel(new BorderLayout(0, 7));
        top.add(heading, BorderLayout.NORTH);
        top.add(filter, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);
        return panel;
    }

    private JPanel createViewer() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        JPanel toolbar = new JPanel(new BorderLayout());
        location.setForeground(new Color(92, 103, 110));
        toolbar.add(location, BorderLayout.WEST);
        JPanel status = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        status.setOpaque(false);
        if (!language.activeCode().equals(docsLanguage)) {
            JLabel fallback = new JLabel(t(
                    "docs.language_fallback",
                    "No bundled guide for {0}; showing English.",
                    language.activeCode()
            ));
            fallback.setForeground(new Color(132, 83, 44));
            status.add(fallback);
        }
        JLabel offline = new JLabel(t("docs.offline", "Offline Markdown"));
        offline.setForeground(new Color(92, 103, 110));
        status.add(offline);
        toolbar.add(status, BorderLayout.EAST);
        panel.add(toolbar, BorderLayout.NORTH);
        viewerScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        viewerScroll.getVerticalScrollBar().setUnitIncrement(18);
        panel.add(viewerScroll, BorderLayout.CENTER);
        UiAccessibility.name(viewer, t("docs.title", "Documentation"));
        return panel;
    }

    private void rebuildNavigation() {
        String query = filter.getText() == null ? "" : filter.getText().trim().toLowerCase(Locale.ROOT);
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Docs");
        root.add(node(new DocChoice("README.md",
                t("docs.welcome", "Documentation home"), DocKind.DOCUMENT)));
        addLeaf(root, query, "quick-start.md", t("docs.quick_start", "Quick start"));
        addLeaf(root, query, "installation.md", t("docs.installation", "Installation and updates"));

        for (DocGroup group : GROUPS) {
            List<ToolDescriptor> tools = registry.list().stream()
                    .filter(tool -> group.categories().contains(tool.category()))
                    .sorted(java.util.Comparator.comparing(
                            tool -> localizedName(tool), String.CASE_INSENSITIVE_ORDER))
                    .toList();
            String groupLabel = groupLabel(group) + " (" + tools.size() + ")";
            boolean groupMatches = query.isEmpty() || groupLabel.toLowerCase(Locale.ROOT).contains(query);
            List<DefaultMutableTreeNode> matchingTools = new ArrayList<>();
            for (ToolDescriptor tool : tools) {
                String name = localizedName(tool);
                if (query.isEmpty() || groupMatches || toolText.searchText(tool).contains(query)) {
                    matchingTools.add(node(new DocChoice("tool:" + tool.id(), name, DocKind.TOOL)));
                }
            }
            if (query.isEmpty() || groupMatches || !matchingTools.isEmpty()) {
                DefaultMutableTreeNode groupNode = node(new DocChoice(
                        group.route(), groupLabel, DocKind.CATEGORY));
                matchingTools.forEach(groupNode::add);
                groupNode.setAllowsChildren(true);
                root.add(groupNode);
            }
        }

        DefaultMutableTreeNode cli = new DefaultMutableTreeNode(t("docs.cli_group", "CLI and MCP"));
        addLeaf(cli, query, "cli.md", t("docs.cli", "Command line"));
        addLeaf(cli, query, "mcp.md", t("docs.mcp", "MCP integration"));
        if (cli.getChildCount() > 0) {
            root.add(cli);
        }
        addLeaf(root, query, "workflow.md", t("docs.workflow", "Workflows"));
        addLeaf(root, query, "external-tools.md", t("docs.external", "External tools"));
        addLeaf(root, query, "faq.md", t("docs.faq", "FAQ"));

        DefaultMutableTreeNode reference = new DefaultMutableTreeNode(t("docs.reference", "Reference"));
        addLeaf(reference, query, "file-formats.md", t("docs.formats", "File formats"));
        addLeaf(reference, query, "errors-exit-codes.md", t("docs.errors", "Errors and exit codes"));
        addLeaf(reference, query, "plugin-development.md", t("docs.plugins", "Plugin developers"));
        addLeaf(reference, query, "release-packages.md", t("docs.releases", "Release packages"));
        if (reference.getChildCount() > 0) {
            root.add(reference);
        }

        DefaultTreeModel model = new DefaultTreeModel(root);
        navigation = new JTree(model);
        navigation.setRootVisible(false);
        navigation.setShowsRootHandles(true);
        navigation.setRowHeight(UiScaling.px(23));
        navigation.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        navigation.setCellRenderer((tree, value, selected, expanded, leaf, row, focus) -> {
            JLabel label = new JLabel();
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(2, 7, 2, 7));
            if (value instanceof DefaultMutableTreeNode treeNode
                    && treeNode.getUserObject() instanceof DocChoice choice) {
                label.setText(choice.label());
            } else {
                label.setText(String.valueOf(value));
                label.setFont(label.getFont().deriveFont(Font.BOLD));
            }
            if (selected) {
                label.setBackground(javax.swing.UIManager.getColor("Tree.selectionBackground"));
                label.setForeground(javax.swing.UIManager.getColor("Tree.selectionForeground"));
            } else {
                label.setBackground(Color.WHITE);
                label.setForeground(tree.getForeground());
            }
            return label;
        });
        navigation.addTreeSelectionListener(event -> {
            Object selected = navigation.getLastSelectedPathComponent();
            if (selected instanceof DefaultMutableTreeNode treeNode
                    && treeNode.getUserObject() instanceof DocChoice choice) {
                switch (choice.kind()) {
                    case DOCUMENT, CATEGORY -> openDocument(choice.route());
                    case TOOL -> openTool(choice.route().substring("tool:".length()));
                }
            }
        });
        expandAll();
        JScrollPane scroll = new JScrollPane(navigation);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(215, 222, 226)));
        JPanel sidebar = (JPanel) getComponent(0);
        if (sidebar.getComponentCount() > 1) {
            sidebar.remove(1);
        }
        sidebar.add(scroll, BorderLayout.CENTER);
        sidebar.revalidate();
        sidebar.repaint();
    }

    private void addLeaf(DefaultMutableTreeNode parent, String query, String route, String label) {
        if (query.isEmpty() || label.toLowerCase(Locale.ROOT).contains(query)
                || route.toLowerCase(Locale.ROOT).contains(query)) {
            String stableLabel = label;
            if (!(parent.getUserObject() instanceof String)) {
                stableLabel = label;
            }
            parent.add(node(new DocChoice(route, stableLabel, DocKind.DOCUMENT)));
        }
    }

    private static DefaultMutableTreeNode node(DocChoice choice) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(choice);
        node.setAllowsChildren(choice.kind() != DocKind.TOOL);
        return node;
    }

    private void expandAll() {
        for (int row = 0; row < navigation.getRowCount(); row++) {
            navigation.expandRow(row);
        }
    }

    void openDocument(String route) {
        String normalized = normalizeRoute(route);
        activeDocument = normalized;
        try {
            String markdown = DocResources.read(docsLanguage, normalized);
            if (CategoryTools.isCategory(normalized)) {
                markdown += CategoryTools.render(normalized, registry, language, toolText);
            }
            String html = MarkdownRenderer.render(markdown, normalized);
            setDocument(html, DocResources.url(docsLanguage, normalized), normalized);
        } catch (Exception ex) {
            String fallback = "# " + t("docs.load_failed", "Document could not be loaded") + "\n\n"
                    + "`" + normalized + "`\n\n" + ex.getMessage();
            setDocument(MarkdownRenderer.render(fallback, "README.md"), null, normalized);
        }
    }

    void openTool(String toolId) {
        registry.find(toolId).ifPresentOrElse(tool -> {
            activeDocument = "tool:" + toolId;
            ToolDescriptor descriptor = tool.descriptor();
            Optional<DocumentSource> custom = customDocument(descriptor);
            String generated = ToolDocumentation.render(descriptor, toolText);
            String markdown = generated;
            String sourcePath = "README.md";
            if (custom.isPresent()) {
                DocumentSource document = custom.get();
                markdown = document.markdown().stripTrailing()
                        + "\n\n---\n\n## "
                        + t(
                                "docs.generated_reference",
                                "Automatically generated tool reference"
                        )
                        + "\n\n"
                        + generated;
                sourcePath = document.sourcePath();
            }
            setDocument(
                    MarkdownRenderer.render(markdown, sourcePath),
                    null,
                    t("docs.tool_location", "Tool reference · {0}", toolId)
            );
        }, () -> openDocument("tool-reference.md"));
    }

    void refreshNavigation() {
        rebuildNavigation();
        if (activeDocument.startsWith("tool:")) {
            String toolId = activeDocument.substring("tool:".length());
            if (registry.find(toolId).isEmpty()) {
                openDocument("tool-reference.md");
            }
        }
    }

    private Optional<DocumentSource> customDocument(ToolDescriptor descriptor) {
        if (plugins != null) {
            Optional<String> markdown = plugins.toolDocumentation(descriptor);
            if (markdown.isPresent() && !markdown.get().isBlank()) {
                String sourcePath = ToolDocumentationPaths.pluginEntry(
                                language.activeCode(),
                                descriptor.id()
                        )
                        .orElse("README.md");
                return Optional.of(new DocumentSource(markdown.get(), sourcePath));
            }
        }
        try {
            return DocResources.readToolDocumentation(docsLanguage, descriptor)
                    .filter(document -> !document.markdown().isBlank())
                    .map(document -> new DocumentSource(
                            document.markdown(),
                            document.sourcePath()
                    ));
        } catch (java.io.IOException ex) {
            return Optional.empty();
        }
    }

    private void setDocument(String html, java.net.URL base, String label) {
        if (base != null) {
            viewer.getDocument().putProperty(
                    javax.swing.text.Document.StreamDescriptionProperty,
                    base
            );
        }
        viewer.setText(html);
        viewer.setCaretPosition(0);
        location.setText("  " + label);
    }

    private void followLink(String description) {
        if (description == null || description.isBlank()) {
            return;
        }
        if (description.startsWith("opentools-tool:")) {
            String target = description.substring("opentools-tool:".length());
            openTool(target);
            return;
        }
        if (description.startsWith("opentools-doc:")) {
            openDocument(description.substring("opentools-doc:".length()));
            return;
        }
        if (description.startsWith("#")) {
            viewer.scrollToReference(description.substring(1));
            return;
        }
        if (description.regionMatches(true, 0, "https://", 0, 8)
                || description.regionMatches(true, 0, "http://", 0, 7)) {
            openExternal(description);
            return;
        }
        openDocument(description);
    }

    private void openExternal(String target) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException("Desktop browser integration is unavailable");
            }
            Desktop.getDesktop().browse(URI.create(target));
        } catch (Exception ex) {
            location.setText("  " + t("docs.external_failed", "Could not open external link: {0}", target));
        }
    }

    private String localizedName(ToolDescriptor tool) {
        return toolText.name(tool);
    }

    private String groupLabel(DocGroup group) {
        return t("docs.group." + group.key(), group.fallback());
    }

    private String t(String key, String fallback, Object... arguments) {
        return language.text(key, fallback, arguments);
    }

    private static String normalizeRoute(String route) {
        String value = route == null ? "README.md" : route.trim().replace('\\', '/');
        if (value.isBlank() || value.startsWith("/") || value.matches("^[A-Za-z]:.*")
                || value.contains("..")) {
            return "README.md";
        }
        return value;
    }

    int navigationToolCount() {
        return countNodes(navigation.getModel().getRoot(), DocKind.TOOL);
    }

    int navigationCategoryCount() {
        return countNodes(navigation.getModel().getRoot(), DocKind.CATEGORY);
    }

    String activeDocument() {
        return activeDocument;
    }

    String docsLanguage() {
        return docsLanguage;
    }

    String viewerText() {
        return viewer.getText();
    }

    int horizontalScrollPolicy() {
        return viewerScroll.getHorizontalScrollBarPolicy();
    }

    private static int countNodes(Object value, DocKind kind) {
        if (!(value instanceof DefaultMutableTreeNode node)) {
            return 0;
        }
        int count = node.getUserObject() instanceof DocChoice choice && choice.kind() == kind ? 1 : 0;
        for (int index = 0; index < node.getChildCount(); index++) {
            count += countNodes(node.getChildAt(index), kind);
        }
        return count;
    }

    private enum DocKind {
        DOCUMENT,
        CATEGORY,
        TOOL
    }

    private record DocChoice(String route, String label, DocKind kind) {
    }

    private record DocGroup(String key, String fallback, String route) {
        List<String> categories() {
            return switch (key) {
                case "annotation" -> List.of("Genome annotation", "Annotation");
                case "alignment" -> List.of("Alignment", "Phylogeny", "Comparative genomics");
                case "expression" -> List.of("Expression", "Enrichment");
                default -> List.of(fallback.replace('+', ','));
            };
        }
    }

    private record DocumentSource(String markdown, String sourcePath) {
    }

    private static final class CategoryTools {
        private CategoryTools() {
        }

        static boolean isCategory(String route) {
            return route.startsWith("categories/");
        }

        static String render(
                String route,
                ToolRegistry registry,
                LanguageManager language,
                ToolTextResolver toolText
        ) {
            List<String> categories = categories(route);
            List<ToolDescriptor> tools = registry.list().stream()
                    .filter(tool -> categories.contains(tool.category()))
                    .sorted(java.util.Comparator.comparing(
                            toolText::name,
                            String.CASE_INSENSITIVE_ORDER))
                    .toList();
            StringBuilder markdown = new StringBuilder("\n## ")
                    .append(language.text("docs.tools_in_category", "Tools in this category"))
                    .append("\n\n");
            for (ToolDescriptor tool : tools) {
                markdown.append("- [")
                        .append(toolText.name(tool)
                                .replace("[", "\\[").replace("]", "\\]"))
                        .append("](tool:").append(tool.id()).append(") `")
                        .append(tool.id()).append("`\n");
            }
            markdown.append('\n');
            return markdown.toString();
        }

        private static List<String> categories(String route) {
            return switch (route) {
                case "categories/sequence.md" -> List.of("Sequence");
                case "categories/annotation-bed.md" -> List.of("Genome annotation", "Annotation");
                case "categories/table.md" -> List.of("Table");
                case "categories/alignment-phylogeny.md" ->
                        List.of("Alignment", "Phylogeny", "Comparative genomics");
                case "categories/expression-enrichment.md" ->
                        List.of("Expression", "Enrichment");
                case "categories/hts.md" -> List.of("HTS");
                case "categories/visualization.md" -> List.of("Visualization");
                case "categories/plugin.md" -> List.of("Plugin");
                default -> List.of();
            };
        }
    }
}
