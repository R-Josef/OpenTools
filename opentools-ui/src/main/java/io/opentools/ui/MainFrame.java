package io.opentools.ui;

import io.opentools.core.job.JobRecord;
import io.opentools.core.i18n.LanguageManager;
import io.opentools.core.runtime.AppVersion;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolRequest;
import io.opentools.core.workspace.Project;
import io.opentools.plugin.PageContribution;
import io.opentools.external.ExternalTools;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

final class MainFrame extends JFrame {
    private static final String HOME = "Home";
    private static final String DOCS = "Docs";
    private static final String PROJECTS = "Projects";
    private static final String TOOLS = "Tools";
    private static final String WORKFLOWS = "Workflows";
    private static final String JOBS = "Jobs";
    private static final String RESULTS = "Results";
    private static final String PLUGINS = "Plugins";
    private static final String SETTINGS = "Settings";
    private static final String[] VIEWS = {HOME, DOCS, PROJECTS, TOOLS, WORKFLOWS, JOBS, RESULTS, PLUGINS, SETTINGS};

    private final OpenToolsGui.UiSession session;
    private final ProjectWorkflow projectWorkflow = new ProjectWorkflow();
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final JTextField globalSearch = new JTextField();
    private final JLabel status = new JLabel();
    private final JobsTableModel jobsModel;
    private final JTable jobsTable;
    private final JTable homeJobsTable;
    private final JLabel homeSummary = new JLabel();
    private final JLabel homeRecent = new JLabel();
    private final JLabel headerProjectName = new JLabel();
    private final JLabel headerProjectRoot = new JLabel();
    private final JLabel projectInfo = new JLabel();
    private final JLabel settingsDetails = new JLabel();
    private final JComboBox<Path> recentProjects = new JComboBox<>();
    private final Timer refreshTimer;
    private ToolCatalogPanel catalogPanel;
    private DocsPanel docsPanel;
    private ResultInspectorPanel resultPanel;
    private WorkflowPanel workflowPanel;
    private PluginPanel pluginPanel;
    private String pendingJobRunId;

    MainFrame(OpenToolsGui.UiSession session) {
        super("OpenTools " + AppVersion.current());
        this.session = session;
        setTitle(windowTitle());
        this.jobsModel = new JobsTableModel(session.language());
        this.jobsTable = new JTable(jobsModel);
        this.homeJobsTable = new JTable(jobsModel);
        this.status.setText(t("status.loading", "Loading..."));
        setIconImages(AppIcon.windowImages());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(UiScaling.px(980), UiScaling.px(640)));
        setSize(
                Math.max(UiScaling.px(980), Integer.getInteger("opentools.window.width", 1440)),
                Math.max(UiScaling.px(640), Integer.getInteger("opentools.window.height", 880))
        );
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(createHeader(), BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout());
        body.add(createNavigation(), BorderLayout.WEST);
        body.add(createContent(), BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);
        TextContextMenus.install(this, session.language());
        UiAccessibility.name(this, t("app.title", "OpenTools"));
        UiAccessibility.describe(this, t("app.window.accessibility", "OpenTools desktop workspace"));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                requestClose();
            }
        });
        refreshTimer = new Timer(2500, ignored -> refreshJobs());
        refreshTimer.start();
        refreshJobs();
        showView(HOME);
        pluginPanel.refresh();
    }

    OpenToolsGui.UiSession session() {
        return session;
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        JPanel brand = new JPanel(new BorderLayout(10, 0));
        brand.setOpaque(false);
        JLabel brandIcon = new JLabel(AppIcon.icon(48));
        brand.add(brandIcon, BorderLayout.WEST);
        JPanel brandText = new JPanel(new GridLayout(2, 1));
        brandText.setOpaque(false);
        JLabel name = new JLabel("OpenTools");
        name.setFont(name.getFont().deriveFont(Font.BOLD, name.getFont().getSize2D() + 5f));
        headerProjectName.setText(session.project().name());
        headerProjectName.setForeground(new Color(100, 106, 111));
        brandText.add(name);
        brandText.add(headerProjectName);
        brand.add(brandText, BorderLayout.CENTER);
        header.add(brand, BorderLayout.WEST);

        globalSearch.setToolTipText(t("app.search.placeholder", "Search tools, formats, or keywords"));
        UiAccessibility.name(globalSearch, t("app.search.placeholder", "Search tools, formats, or keywords"));
        globalSearch.putClientProperty(
                "JTextField.placeholderText",
                t("app.search.placeholder", "Search tools, formats, or keywords")
        );
        globalSearch.addActionListener(ignored -> {
            showView(TOOLS);
            if (catalogPanel != null) {
                catalogPanel.applyGlobalSearch(globalSearch.getText());
            }
        });
        JPanel searchWrapper = new JPanel(new BorderLayout());
        searchWrapper.setOpaque(false);
        globalSearch.setMinimumSize(new Dimension(180, 30));
        searchWrapper.setBorder(BorderFactory.createEmptyBorder(3, 18, 3, 18));
        searchWrapper.add(globalSearch, BorderLayout.CENTER);
        header.add(searchWrapper, BorderLayout.CENTER);

        updateHeaderProject();
        header.add(headerProjectRoot, BorderLayout.EAST);
        return header;
    }

    private JScrollPane createNavigation() {
        DefaultListModel<String> model = new DefaultListModel<>();
        for (String view : VIEWS) {
            model.addElement(view);
        }
        JList<String> navigation = new JList<>(model);
        UiAccessibility.name(navigation, t("nav.accessibility", "Main navigation"));
        navigation.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        navigation.setSelectedIndex(0);
        navigation.setFixedCellHeight(38);
        navigation.setFixedCellWidth(152);
        navigation.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        navigation.setCellRenderer(new NavigationRenderer());
        navigation.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showView(navigation.getSelectedValue());
            }
        });
        JScrollPane scroll = new JScrollPane(navigation);
        scroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(218, 222, 225)));
        scroll.setPreferredSize(new Dimension(168, 100));
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    private JPanel createContent() {
        cardLayout.setVgap(0);
        cards.add(createHomePanel(), HOME);
        docsPanel = new DocsPanel(
                session.registry(),
                session.language(),
                session.plugins(),
                session.toolText()
        );
        cards.add(docsPanel, DOCS);
        cards.add(createProjectsPanel(), PROJECTS);
        catalogPanel = new ToolCatalogPanel(
                session.registry(),
                session.execution(),
                session.language(),
                session.toolText(),
                request -> session.submit(request),
                this::refreshJobs
        );
        cards.add(catalogPanel, TOOLS);
        workflowPanel = new WorkflowPanel(session);
        cards.add(workflowPanel, WORKFLOWS);
        cards.add(createJobsPanel(), JOBS);
        resultPanel = new ResultInspectorPanel(
                session.execution(),
                session.language(),
                session.toolText()
        );
        cards.add(resultPanel, RESULTS);
        pluginPanel = new PluginPanel(session.plugins(), session.language(), this::afterPluginScan);
        cards.add(pluginPanel, PLUGINS);
        cards.add(createSettingsPanel(), SETTINGS);
        return cards;
    }

    private JPanel createHomePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        JLabel title = new JLabel(t("home.title", "Current work"));
        title.setFont(title.getFont().deriveFont(title.getFont().getSize2D() + 12f));
        panel.add(title, BorderLayout.NORTH);

        JPanel summary = new JPanel(new GridLayout(3, 1, 0, 8));
        summary.setBorder(BorderFactory.createEmptyBorder(8, 0, 18, 0));
        homeSummary.setFont(homeSummary.getFont().deriveFont(homeSummary.getFont().getSize2D() + 4f));
        homeRecent.setForeground(new Color(94, 100, 105));
        summary.add(homeSummary);
        summary.add(homeRecent);
        JButton openTools = new JButton(t("home.open_tools", "Open tool catalog"));
        openTools.addActionListener(ignored -> showView(TOOLS));
        JButton openDocs = new JButton(t("home.open_docs", "Open documentation"));
        openDocs.addActionListener(ignored -> showView(DOCS));
        JPanel quick = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        quick.add(openDocs);
        quick.add(openTools);
        summary.add(quick);

        JPanel recent = new JPanel(new BorderLayout(0, 8));
        recent.add(new JLabel(t("home.recent_jobs", "Recent jobs")), BorderLayout.NORTH);
        homeJobsTable.setRowHeight(26);
        UiAccessibility.name(homeJobsTable, t("home.recent_jobs", "Recent jobs"));
        recent.add(new JScrollPane(homeJobsTable), BorderLayout.CENTER);
        panel.add(summary, BorderLayout.NORTH);
        panel.add(recent, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createProjectsPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 1));
        panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        JPanel content = new JPanel(new BorderLayout(0, 12));
        JLabel title = new JLabel(t("projects.title", "Projects"));
        title.setFont(title.getFont().deriveFont(title.getFont().getSize2D() + 12f));
        content.add(title, BorderLayout.NORTH);

        JPanel projectDetails = new JPanel(new BorderLayout(0, 12));
        projectDetails.add(projectInfo, BorderLayout.NORTH);
        JPanel recent = new JPanel(new BorderLayout(8, 0));
        recent.add(new JLabel(t("projects.recent", "Recent projects")), BorderLayout.WEST);
        recentProjects.setPreferredSize(new Dimension(520, 30));
        recentProjects.setMinimumSize(new Dimension(240, 30));
        recentProjects.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        UiAccessibility.name(recentProjects, t("projects.recent", "Recent projects"));
        recent.add(recentProjects, BorderLayout.CENTER);
        JButton openRecent = new JButton(t("projects.open_recent", "Open recent"));
        openRecent.addActionListener(ignored -> {
            Object selected = recentProjects.getSelectedItem();
            if (selected instanceof Path path) {
                openProject(path);
            }
        });
        recent.add(openRecent, BorderLayout.EAST);
        projectDetails.add(recent, BorderLayout.SOUTH);
        content.add(projectDetails, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton create = new JButton(t("projects.new", "New project"));
        create.addActionListener(ignored -> createProject());
        JButton openProject = new JButton(t("projects.open_project", "Open project"));
        openProject.addActionListener(ignored -> chooseProject());
        JButton openDirectory = new JButton(t("projects.open", "Open project directory"));
        openDirectory.addActionListener(ignored -> openPath(session.project().root()));
        actions.add(create);
        actions.add(openProject);
        actions.add(openDirectory);
        content.add(actions, BorderLayout.SOUTH);
        panel.add(content);
        refreshProjectLabels();
        refreshRecentProjects();
        return panel;
    }

    private JPanel createJobsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        JPanel toolbar = new JPanel(new BorderLayout());
        JLabel title = new JLabel(t("jobs.title", "Jobs"));
        title.setFont(title.getFont().deriveFont(title.getFont().getSize2D() + 7f));
        toolbar.add(title, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton refresh = new JButton(t("jobs.refresh", "Refresh"));
        JButton viewResult = new JButton(t("jobs.view_result", "View result"));
        JButton openLog = new JButton(t("jobs.open_log", "Open log"));
        JButton openRun = new JButton(t("jobs.open_run", "Open run directory"));
        JButton cancel = new JButton(t("jobs.cancel", "Cancel"));
        JButton retry = new JButton(t("jobs.retry", "Retry"));
        JButton copyCommand = new JButton(t("jobs.copy_command", "Copy command"));
        actions.add(refresh);
        actions.add(viewResult);
        actions.add(openLog);
        actions.add(openRun);
        actions.add(retry);
        actions.add(copyCommand);
        actions.add(cancel);
        toolbar.add(actions, BorderLayout.EAST);
        panel.add(toolbar, BorderLayout.NORTH);
        jobsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        jobsTable.setRowHeight(26);
        UiAccessibility.name(jobsTable, t("jobs.title", "Jobs"));
        int[] widths = {100, 170, 90, 120, 120, 120, 380};
        for (int i = 0; i < widths.length; i++) {
            jobsTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        panel.add(new JScrollPane(jobsTable), BorderLayout.CENTER);

        refresh.addActionListener(ignored -> refreshJobs());
        viewResult.addActionListener(ignored -> {
            JobRecord job = selectedJob();
            if (job == null) {
                status.setText(t("jobs.select_for_result", "Select a job first."));
                return;
            }
            showView(RESULTS);
            resultPanel.selectJob(job.runId());
        });
        openLog.addActionListener(ignored -> openSelectedJobFile("run.log"));
        openRun.addActionListener(ignored -> {
            JobRecord job = selectedJob();
            if (job != null) {
                openPath(job.runDirectory());
            }
        });
        cancel.addActionListener(ignored -> {
            JobRecord job = selectedJob();
            if (job != null) {
                boolean accepted = session.execution().cancel(job.runId());
                status.setText(accepted
                        ? t("jobs.requested_cancel", "Cancellation requested.")
                        : t("jobs.not_cancellable", "This job cannot be cancelled."));
                refreshJobs();
            }
        });
        retry.addActionListener(ignored -> {
            JobRecord job = selectedJob();
            if (job == null) {
                return;
            }
            Thread.ofVirtual().start(() -> {
                try {
                    String newRunId = session.retry(job.runId());
                    SwingUtilities.invokeLater(() -> {
                        status.setText(t("jobs.retry_submitted", "Retry submitted: {0}", newRunId));
                        refreshJobs();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            this,
                            rootMessage(ex),
                            t("jobs.retry", "Retry"),
                            JOptionPane.ERROR_MESSAGE
                    ));
                }
            });
        });
        copyCommand.addActionListener(ignored -> {
            JobRecord job = selectedJob();
            if (job == null) {
                return;
            }
            try {
                ToolRequest request = session.execution().requestFromJob(job.runId());
                String command = commandFor(request);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(command), null);
                status.setText(t("jobs.command_copied", "CLI command copied."));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, rootMessage(ex), "OpenTools", JOptionPane.ERROR_MESSAGE);
            }
        });
        return panel;
    }

    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        JLabel title = new JLabel(t("settings.title", "Settings and environment"));
        title.setFont(title.getFont().deriveFont(title.getFont().getSize2D() + 12f));
        panel.add(title, BorderLayout.NORTH);
        refreshSettingsDetails();
        panel.add(settingsDetails, BorderLayout.CENTER);

        JPanel languageBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        languageBar.add(new JLabel(t("settings.language", "Interface language")));
        JComboBox<LanguageManager.LanguageOption> languages =
                new JComboBox<>(session.language().availableLanguages().toArray(LanguageManager.LanguageOption[]::new));
        for (int i = 0; i < languages.getItemCount(); i++) {
            if (languages.getItemAt(i).code().equals(session.language().activeCode())) {
                languages.setSelectedIndex(i);
                break;
            }
        }
        boolean[] initializingLanguage = {true};
        languages.addActionListener(ignored -> {
            if (initializingLanguage[0]) {
                return;
            }
            LanguageManager.LanguageOption selected =
                    (LanguageManager.LanguageOption) languages.getSelectedItem();
            if (selected != null && !selected.code().equals(session.language().activeCode())) {
                applyLanguageImmediately(selected.code());
            }
        });
        UiAccessibility.name(languages, t("settings.language", "Interface language"));
        initializingLanguage[0] = false;
        languageBar.add(languages);
        panel.add(languageBar, BorderLayout.SOUTH);

        JPanel external = new JPanel(new BorderLayout(0, 7));
        external.setBorder(BorderFactory.createEmptyBorder(0, 24, 0, 0));
        external.setPreferredSize(new Dimension(480, 100));
        JPanel externalHeader = new JPanel(new BorderLayout());
        externalHeader.add(new JLabel(t("settings.external_tools", "External tools")), BorderLayout.WEST);
        JButton checkExternal = new JButton(t("settings.check_external", "Check"));
        externalHeader.add(checkExternal, BorderLayout.EAST);
        external.add(externalHeader, BorderLayout.NORTH);
        JTextArea externalStatus = new JTextArea();
        externalStatus.setEditable(false);
        externalStatus.setLineWrap(true);
        externalStatus.setWrapStyleWord(true);
        externalStatus.setText(t("settings.external_not_checked", "Not checked yet."));
        external.add(new JScrollPane(externalStatus), BorderLayout.CENTER);
        checkExternal.addActionListener(ignored -> {
            externalStatus.setText(t("settings.checking_external", "Checking external tools..."));
            Thread.ofVirtual().start(() -> {
                String text;
                try {
                    StringBuilder builder = new StringBuilder();
                    for (var tool : ExternalTools.diagnostics()) {
                        builder.append(tool.displayName())
                                .append(": ")
                                .append(tool.available()
                                        ? t("settings.tool_available", "available")
                                        : t("settings.tool_missing", "missing"))
                                .append(tool.version() == null || tool.version().isBlank() ? "" : " · " + tool.version())
                                .append(tool.available() && tool.executable() != null ? "\n" + tool.executable() : "")
                                .append(tool.diagnostic() == null || tool.diagnostic().isBlank()
                                        ? ""
                                        : "\n" + tool.diagnostic())
                                .append("\n\n");
                    }
                    text = builder.toString().strip();
                } catch (Exception ex) {
                    text = rootMessage(ex);
                }
                String finalText = text;
                SwingUtilities.invokeLater(() -> externalStatus.setText(finalText));
            });
        });
        panel.add(external, BorderLayout.EAST);
        return panel;
    }

    private void applyLanguageImmediately(String languageCode) {
        String previousLanguage = session.language().activeCode();
        try {
            session.language().setLanguage(languageCode);
        } catch (Exception ex) {
            showLanguageError(ex);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                MainFrame replacement = new MainFrame(session);
                replacement.status.setText(t("settings.language_applied", "Interface language changed."));
                replacement.setVisible(true);
                dispose();
            } catch (Exception ex) {
                try {
                    session.language().setLanguage(previousLanguage);
                } catch (Exception ignored) {
                    // Keep the original failure; the next launch resolves persisted language.
                }
                showLanguageError(ex);
            }
        });
    }

    private void showLanguageError(Exception exception) {
        JOptionPane.showMessageDialog(
                this,
                rootMessage(exception),
                t("settings.title", "Settings"),
                JOptionPane.ERROR_MESSAGE
        );
    }

    @Override
    public void dispose() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        super.dispose();
    }

    private JPanel createStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(218, 222, 225)),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        status.setForeground(new Color(95, 101, 106));
        bar.add(status, BorderLayout.WEST);
        JLabel runtime = new JLabel(
                t("settings.version_label", "Version") + " " + AppVersion.current()
                        + "  ·  Java " + System.getProperty("java.version")
        );
        runtime.setForeground(new Color(120, 125, 130));
        bar.add(runtime, BorderLayout.EAST);
        return bar;
    }

    private String windowTitle() {
        return "OpenTools " + AppVersion.current() + " · " + session.project().name();
    }

    private void showView(String view) {
        if (view == null) {
            return;
        }
        cardLayout.show(cards, view);
        if (HOME.equals(view)) {
            refreshJobs();
        } else if (JOBS.equals(view)) {
            refreshJobs();
        } else if (RESULTS.equals(view)) {
            resultPanel.refresh();
        } else if (TOOLS.equals(view)) {
            catalogPanel.reload();
        } else if (WORKFLOWS.equals(view)) {
            workflowPanel.rebind();
        }
    }

    private void refreshJobs() {
        var execution = session.execution();
        Project project = session.project();
        Thread.ofVirtual().start(() -> {
            try {
                List<JobRecord> jobs = execution.recentJobs(50);
                SwingUtilities.invokeLater(() -> {
                    if (execution != session.execution()) {
                        return;
                    }
                    String selectedRunId = selectedJobId(jobsTable);
                    String homeSelectedRunId = selectedJobId(homeJobsTable);
                    jobsModel.setJobs(jobs);
                    restoreJobSelection(jobsTable, selectedRunId);
                    restoreJobSelection(homeJobsTable, homeSelectedRunId);
                    if (pendingJobRunId != null) {
                        restoreJobSelection(jobsTable, pendingJobRunId);
                        pendingJobRunId = null;
                    }
                    long running = jobs.stream().filter(job -> job.status().name().equals("RUNNING")).count();
                    String last = jobs.isEmpty()
                            ? t("home.no_jobs", "No jobs yet")
                            : t(
                                    "home.recent_job",
                                    "Latest: {0} - {1}",
                                    jobs.getFirst().toolId(),
                                    t(
                                            "job.status." + jobs.getFirst().status().name(),
                                            jobs.getFirst().status().name()
                                    )
                            );
                    homeSummary.setText(project.name() + "  ·  "
                            + t("home.running_jobs", "{0} running jobs", running));
                    homeRecent.setText(last);
                    status.setText(t("status.project_saved", "Project saved")
                            + "  ·  " + t("status.running_jobs", "{0} running", running)
                            + "  ·  " + t("status.recent_jobs", "{0} recent jobs", jobs.size()));
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> status.setText(rootMessage(ex)));
            }
        });
    }

    private void createProject() {
        JFileChooser chooser = projectChooser(
                t("projects.new", "New project"),
                t("dialog.choose_project", "Choose the new project directory")
        );
        if (chooser.showDialog(this, t("projects.new", "Create")) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path root = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        String folderName = root.getFileName() == null ? "OpenTools Project" : root.getFileName().toString();
        String name = askProjectName(folderName);
        if (name == null) {
            return;
        }
        try {
            switchProject(projectWorkflow.create(root, name));
        } catch (IllegalStateException ex) {
            showProjectSwitchBlocked(ex);
        } catch (Exception ex) {
            showProjectError(ex);
        }
    }

    private void chooseProject() {
        JFileChooser chooser = projectChooser(
                t("projects.open_project", "Open project"),
                t("dialog.choose_project", "Choose the project root containing project.json")
        );
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openProject(chooser.getSelectedFile().toPath());
        }
    }

    private String askProjectName(String initialName) {
        JTextField field = new JTextField(initialName);
        field.selectAll();
        TextContextMenus.install(field, session.language());
        int choice = JOptionPane.showOptionDialog(
                this,
                field,
                t("projects.new_prompt", "Project name"),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                new Object[]{
                        t("form.text_editor_ok", "OK"),
                        t("form.text_editor_cancel", "Cancel")
                },
                t("form.text_editor_ok", "OK")
        );
        return choice == 0 ? field.getText() : null;
    }

    private void openProject(Path root) {
        try {
            switchProject(projectWorkflow.open(root));
        } catch (IllegalStateException ex) {
            showProjectSwitchBlocked(ex);
        } catch (Exception ex) {
            refreshRecentProjects();
            showProjectError(ex);
        }
    }

    private void switchProject(Project project) throws Exception {
        session.switchProject(project);
        applyProjectContext();
        status.setText(t("projects.switch_success", "Project opened: {0}", project.name()));
        showView(PROJECTS);
    }

    private void applyProjectContext() {
        refreshProjectLabels();
        refreshSettingsDetails();
        refreshRecentProjects();
        setTitle(windowTitle());
        catalogPanel.rebind(session.execution());
        if (workflowPanel != null) {
            workflowPanel.rebind();
        }
        resultPanel.rebind(session.execution());
        refreshJobs();
    }

    private JFileChooser projectChooser(String title, String accessibleTitle) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        Path parent = session.project().root().toAbsolutePath().getParent();
        if (parent != null) {
            chooser.setCurrentDirectory(parent.toFile());
        }
        UiAccessibility.name(chooser, accessibleTitle);
        return chooser;
    }

    private void refreshProjectLabels() {
        updateHeaderProject();
        projectInfo.setText("<html><b>" + escape(session.project().name()) + "</b><br>"
                + escape(session.project().root().toString()) + "<br><br>"
                + escape(t("projects.id", "Project ID: {0}", session.project().id()))
                + "</html>");
    }

    private void updateHeaderProject() {
        headerProjectName.setText(session.project().name());
        headerProjectRoot.setText(session.project().name());
        headerProjectRoot.setForeground(new Color(100, 106, 111));
        headerProjectRoot.setToolTipText(session.project().root().toString());
    }

    private void refreshSettingsDetails() {
        Path appRoot = session.plugins().pluginsDirectory().getParent();
        settingsDetails.setText("<html>"
                + "<b>" + escape(t("settings.version", "Application version")) + "</b><br>"
                + escape(AppVersion.current()) + "<br><br>"
                + "<b>" + escape(t("settings.java", "Java")) + "</b><br>"
                + escape(System.getProperty("java.version")) + "<br><br>"
                + "<b>" + escape(t("settings.os", "Operating system")) + "</b><br>"
                + escape(System.getProperty("os.name") + " " + System.getProperty("os.version")) + "<br><br>"
                + "<b>" + escape(t("settings.project_dir", "Project directory")) + "</b><br>"
                + escape(session.project().root().toString()) + "<br><br>"
                + "<b>" + escape(t("settings.tool_count", "Tool count")) + "</b><br>"
                + session.registry().list().size() + "<br><br>"
                + "<b>" + escape(t("settings.app_dir", "Application directory")) + "</b><br>"
                + escape(appRoot.toString())
                + "</html>");
    }

    private void refreshRecentProjects() {
        Path selected = recentProjects.getSelectedItem() instanceof Path path ? path : null;
        recentProjects.removeAllItems();
        for (Path project : projectWorkflow.recentProjects()) {
            recentProjects.addItem(project);
        }
        if (selected != null) {
            for (int index = 0; index < recentProjects.getItemCount(); index++) {
                if (recentProjects.getItemAt(index).equals(selected)) {
                    recentProjects.setSelectedIndex(index);
                    break;
                }
            }
        }
        recentProjects.setEnabled(recentProjects.getItemCount() > 0);
        recentProjects.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = (JLabel) new DefaultListCellRenderer().getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            if (value instanceof Path path) {
                boolean available = Files.isDirectory(path);
                label.setText(path + (available ? "" : "  (" + t("projects.missing", "missing") + ")"));
                if (!available && !isSelected) {
                    label.setForeground(new Color(145, 84, 68));
                }
            }
            return label;
        });
    }

    private void showProjectSwitchBlocked(IllegalStateException exception) {
        JOptionPane.showMessageDialog(
                this,
                t(
                        "projects.switch_running",
                        "Wait for queued and running jobs to finish before switching projects."
                ),
                t("projects.open_project", "Open project"),
                JOptionPane.WARNING_MESSAGE
        );
    }

    private void showProjectError(Exception exception) {
        String code = exception instanceof io.opentools.api.tool.OpenToolException openTool
                ? openTool.code() + "\n"
                : "";
        JOptionPane.showMessageDialog(
                this,
                code + rootMessage(exception),
                t("projects.switch_failed", "Could not open project"),
                JOptionPane.ERROR_MESSAGE
        );
    }

    private void afterPluginScan() {
        catalogPanel.reload();
        docsPanel.refreshNavigation();
        status.setText(t(
                "status.plugin_scan_complete",
                "Plugin scan complete: {0} entries, {1} tools",
                session.plugins().list().size(),
                session.registry().list().size()
        ));
    }

    private String commandFor(ToolRequest request) {
        StringBuilder command = new StringBuilder("opentools --project ")
                .append(quote(session.project().root().toString()))
                .append(" run ")
                .append(request.toolId());
        request.parameters().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> command.append(" --param ")
                        .append(quote(entry.getKey() + "=" + String.valueOf(entry.getValue()))));
        return command.toString();
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private JobRecord selectedJob() {
        int row = jobsTable.getSelectedRow();
        return row < 0 ? null : jobsModel.row(jobsTable.convertRowIndexToModel(row));
    }

    private String selectedJobId(JTable table) {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        JobRecord job = jobsModel.row(table.convertRowIndexToModel(row));
        return job == null ? null : job.runId();
    }

    private void restoreJobSelection(JTable table, String runId) {
        if (runId == null) {
            return;
        }
        for (int row = 0; row < table.getRowCount(); row++) {
            JobRecord job = jobsModel.row(table.convertRowIndexToModel(row));
            if (job != null && runId.equals(job.runId())) {
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
                return;
            }
        }
    }

    void openJob(String runId) {
        pendingJobRunId = runId;
        showView(JOBS);
    }

    private void openSelectedJobFile(String fileName) {
        JobRecord job = selectedJob();
        if (job != null) {
            openPath(job.runDirectory().resolve(fileName));
        }
    }

    private void openPath(Path path) {
        try {
            if (!Files.exists(path)) {
                JOptionPane.showMessageDialog(
                        this,
                        t("dialog.path_missing", "Path does not exist: {0}", path),
                        "OpenTools",
                        JOptionPane.WARNING_MESSAGE
                );
                return;
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(path.toFile());
            } else {
                JOptionPane.showMessageDialog(this, path.toString());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    rootMessage(ex),
                    t("dialog.cannot_open", "Cannot open"),
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void requestClose() {
        try {
            boolean active = session.execution().recentJobs(50).stream()
                    .anyMatch(job -> job.status().name().equals("RUNNING") || job.status().name().equals("QUEUED"));
            if (active) {
                int choice = JOptionPane.showConfirmDialog(
                        this,
                        t("dialog.exit_running", "Jobs are still running. Exiting will interrupt them. Continue?"),
                        t("dialog.exit_title", "Exit OpenTools"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (choice != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            session.close();
        } catch (Exception ignored) {
        }
        dispose();
        System.exit(0);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private String t(String key, String fallback, Object... arguments) {
        return session.language().text(key, fallback, arguments);
    }

    private final class NavigationRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            String id = String.valueOf(value);
            label.setText(t("nav." + id.toLowerCase(), id));
            if (!isSelected) {
                label.setBackground(new Color(247, 248, 248));
            }
            return label;
        }
    }
}
