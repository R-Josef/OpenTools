package io.opentools.ui;

import io.opentools.plugin.PageContribution;
import io.opentools.plugin.host.PluginInfo;
import io.opentools.plugin.host.PluginManager;
import io.opentools.plugin.host.PluginState;
import io.opentools.core.i18n.LanguageManager;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class PluginPanel extends JPanel {
    private final PluginManager pluginManager;
    private final LanguageManager language;
    private final Runnable onChange;
    private final PluginTableModel model;
    private final JTable table;
    private final DefaultListModel<PageContribution> pageModel = new DefaultListModel<>();
    private final JList<PageContribution> pageList = new JList<>(pageModel);
    private final JButton enable = new JButton();
    private final JButton disable = new JButton();

    PluginPanel(PluginManager pluginManager, LanguageManager language, Runnable onChange) {
        this.pluginManager = pluginManager;
        this.language = language;
        this.onChange = onChange;
        this.model = new PluginTableModel();
        this.table = new JTable(model);
        UiAccessibility.name(table, language.text("plugins.title", "Installed plugins"));
        pageList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean selected,
                    boolean focused
            ) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list,
                        value,
                        index,
                        selected,
                        focused
                );
                label.setText(value instanceof PageContribution page ? page.title() : "");
                return label;
            }
        });
        UiAccessibility.name(pageList, language.text("plugins.pages", "Plugin pages"));
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JPanel toolbar = new JPanel(new BorderLayout());
        JLabel title = new JLabel(language.text("plugins.title", "Plugins"));
        title.setFont(title.getFont().deriveFont(title.getFont().getSize2D() + 7f));
        toolbar.add(title, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton refresh = new JButton(language.text("plugins.refresh", "Rescan"));
        enable.setText(language.text("plugins.enable", "Enable"));
        disable.setText(language.text("plugins.disable", "Disable"));
        JButton openPlugins = new JButton(language.text("plugins.open_plugins", "Open plugin directory"));
        buttons.add(refresh);
        buttons.add(enable);
        buttons.add(disable);
        buttons.add(openPlugins);
        toolbar.add(buttons, BorderLayout.EAST);
        add(toolbar, BorderLayout.NORTH);

        table.setRowHeight(26);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        int[] widths = {260, 110, 80, 65, 180, 480};
        for (int index = 0; index < widths.length; index++) {
            table.getColumnModel().getColumn(index).setPreferredWidth(UiScaling.px(widths[index]));
        }
        JPanel pages = new JPanel(new BorderLayout(0, 6));
        pages.add(new JLabel(language.text("plugins.pages", "Plugin pages")), BorderLayout.NORTH);
        pages.add(new JScrollPane(pageList), BorderLayout.CENTER);
        JButton openPage = new JButton(language.text("plugins.open_page", "Open page"));
        pages.add(openPage, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), pages);
        split.setResizeWeight(0.78);
        split.setDividerSize(7);
        add(split, BorderLayout.CENTER);

        refresh.addActionListener(ignored -> refresh());
        enable.addActionListener(ignored -> setSelectedEnabled(true));
        disable.addActionListener(ignored -> setSelectedEnabled(false));
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateActionState();
            }
        });
        openPlugins.addActionListener(ignored -> openDirectory(pluginManager.pluginsDirectory()));
        openPage.addActionListener(ignored -> openPage(pageList.getSelectedValue()));
        updateActionState();
    }

    void refresh() {
        setControlsBusy(true);
        Thread.ofVirtual().start(() -> {
            try {
                List<PluginInfo> plugins = pluginManager.scan();
                List<PageContribution> pages = pluginManager.pages();
                SwingUtilities.invokeLater(() -> applySnapshot(plugins, pages));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setControlsBusy(false);
                    showOperationFailure(ex);
                });
            }
        });
    }

    private void setSelectedEnabled(boolean enabled) {
        PluginInfo selected = selectedPlugin();
        if (selected == null) {
            return;
        }
        setControlsBusy(true);
        Thread.ofVirtual().start(() -> {
            try {
                List<PluginInfo> plugins = enabled
                        ? pluginManager.enable(selected.id())
                        : pluginManager.disable(selected.id());
                List<PageContribution> pages = pluginManager.pages();
                SwingUtilities.invokeLater(() -> applySnapshot(plugins, pages));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setControlsBusy(false);
                    showOperationFailure(ex);
                });
            }
        });
    }

    private void applySnapshot(List<PluginInfo> plugins, List<PageContribution> pages) {
        PluginInfo selected = selectedPlugin();
        String selectedId = selected == null ? "" : selected.id();
        model.setRows(plugins);
        pageModel.clear();
        pages.forEach(pageModel::addElement);
        if (!selectedId.isBlank()) {
            for (int row = 0; row < model.getRowCount(); row++) {
                if (model.row(row).id().equals(selectedId)) {
                    table.getSelectionModel().setSelectionInterval(row, row);
                    break;
                }
            }
        }
        setControlsBusy(false);
        updateActionState();
        onChange.run();
    }

    private void setControlsBusy(boolean busy) {
        enable.setEnabled(!busy);
        disable.setEnabled(!busy);
        if (busy) {
            enable.setText(language.text("plugins.updating", "Updating..."));
            disable.setText(language.text("plugins.updating", "Updating..."));
        } else {
            enable.setText(language.text("plugins.enable", "Enable"));
            disable.setText(language.text("plugins.disable", "Disable"));
        }
    }

    private void updateActionState() {
        PluginInfo selected = selectedPlugin();
        enable.setEnabled(selected != null && selected.state() != PluginState.ENABLED);
        disable.setEnabled(selected != null && selected.state() == PluginState.ENABLED);
    }

    private PluginInfo selectedPlugin() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            return null;
        }
        return model.row(table.convertRowIndexToModel(selectedRow));
    }

    private void showOperationFailure(Exception ex) {
        JOptionPane.showMessageDialog(
                this,
                language.text("plugins.operation_failed", "Plugin operation failed: {0}", rootMessage(ex)),
                language.text("plugins.title", "Plugins"),
                JOptionPane.ERROR_MESSAGE
        );
    }

    private void openPage(PageContribution page) {
        if (page == null) {
            return;
        }
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), page.title(), JDialog.ModalityType.MODELESS);
        try {
            dialog.setContentPane(page.panelFactory().get());
        } catch (Exception ex) {
            dialog.setContentPane(new JLabel(language.text(
                    "plugins.page_failed",
                    "Plugin page failed to load: {0}",
                    rootMessage(ex)
            )));
        }
        TextContextMenus.install(dialog.getContentPane(), language);
        dialog.setSize(new Dimension(900, 650));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void openDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(directory.toFile());
            } else {
                JOptionPane.showMessageDialog(this, directory.toString());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    rootMessage(ex),
                    language.text("plugins.cannot_open", "Cannot open directory"),
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private final class PluginTableModel extends AbstractTableModel {
        private List<PluginInfo> rows = new ArrayList<>();

        void setRows(List<PluginInfo> rows) {
            this.rows = new ArrayList<>(rows);
            fireTableDataChanged();
        }

        PluginInfo row(int index) {
            return rows.get(index);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 6;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> language.text("plugins.column.name", "Name");
                case 1 -> language.text("plugins.column.state", "State");
                case 2 -> language.text("plugins.column.version", "Version");
                case 3 -> language.text("plugins.column.api", "API");
                case 4 -> language.text("plugins.column.dependencies", "Dependencies");
                case 5 -> language.text("plugins.column.diagnostic", "Diagnostic");
                default -> "";
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PluginInfo row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.name();
                case 1 -> language.text("plugin.state." + row.state().name(), row.state().name());
                case 2 -> row.version();
                case 3 -> row.apiVersion();
                case 4 -> String.join(", ", row.dependencies());
                case 5 -> row.diagnostic();
                default -> "";
            };
        }
    }
}
