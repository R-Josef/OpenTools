package io.opentools.ui;

import com.formdev.flatlaf.FlatLightLaf;
import io.opentools.analysis.BuiltInAnalysis;
import io.opentools.core.job.SqliteJobStore;
import io.opentools.core.job.ToolExecutionService;
import io.opentools.core.i18n.LanguageManager;
import io.opentools.core.runtime.AppPaths;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.api.tool.ToolRequest;
import io.opentools.core.workspace.Project;
import io.opentools.formats.BuiltInTools;
import io.opentools.external.ExternalTools;
import io.opentools.plugin.host.PluginManager;
import io.opentools.plugin.host.ToolTextResolver;
import io.opentools.visualization.BuiltInVisualizations;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;

public final class OpenToolsGui {
    private OpenToolsGui() {
    }

    public static void main(String[] args) {
        launchDefault();
    }

    public static void launchDefault() {
        try {
            installLookAndFeel();
            ProjectWorkflow projectWorkflow = new ProjectWorkflow();
            Project project = startupProject(projectWorkflow);
            ToolRegistry registry = new ToolRegistry();
            BuiltInTools.register(registry);
            BuiltInAnalysis.register(registry);
            ExternalTools.register(registry);
            BuiltInVisualizations.register(registry);
            SqliteJobStore store = new SqliteJobStore(project.databasePath());
            store.initialize();
            ToolExecutionService execution = new ToolExecutionService(registry, store);
            Path appHome = appRoot();
            String forcedLanguage = System.getProperty("opentools.language");
            LanguageManager language = new LanguageManager(appHome, forcedLanguage);
            PluginManager plugins = new PluginManager(appHome, registry, language::activeCode);
            launch(registry, project, execution, plugins, language);
        } catch (Exception ex) {
            ex.printStackTrace();
            javax.swing.JOptionPane.showMessageDialog(
                    null,
                    "OpenTools could not start.\n\n" + rootMessage(ex),
                    "Startup failed",
                    javax.swing.JOptionPane.ERROR_MESSAGE
            );
        }
    }

    static Project startupProject(ProjectWorkflow workflow) throws Exception {
        for (Path recent : workflow.recentProjects()) {
            if (Files.isRegularFile(recent.resolve(ProjectWorkflow.PROJECT_METADATA))) {
                try {
                    return workflow.open(recent);
                } catch (Exception ignored) {
                    // A damaged recent project must not block startup.
                }
            }
        }
        return workflow.defaultProject();
    }

    public static void launch(
            ToolRegistry registry,
            Project project,
            ToolExecutionService execution,
            PluginManager plugins,
            LanguageManager language
    ) {
        SwingUtilities.invokeLater(() -> {
            installLookAndFeel();
            MainFrame frame = new MainFrame(new UiSession(registry, project, execution, plugins, language));
            frame.setVisible(true);
        });
    }

    private static void installLookAndFeel() {
        FlatLightLaf.setup();
        UIManager.put("defaultFont", UiScaling.defaultFont());
        UIManager.put("Component.arc", UiScaling.px(8));
        UIManager.put("Button.arc", UiScaling.px(8));
        UIManager.put("TextComponent.arc", UiScaling.px(8));
        UIManager.put("ScrollBar.width", UiScaling.px(11));
    }

    private static Path appRoot() {
        return AppPaths.applicationHome();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public static final class UiSession implements AutoCloseable {
        private final ToolRegistry registry;
        private final PluginManager plugins;
        private final LanguageManager language;
        private final ToolTextResolver toolText;
        private Project project;
        private ToolExecutionService execution;

        public UiSession(
                ToolRegistry registry,
                Project project,
                ToolExecutionService execution,
                PluginManager plugins,
                LanguageManager language
        ) {
            this.registry = registry;
            this.project = project;
            this.execution = execution;
            this.plugins = plugins;
            this.language = language;
            this.toolText = new ToolTextResolver(plugins, language);
        }

        public ToolRegistry registry() {
            return registry;
        }

        public synchronized Project project() {
            return project;
        }

        public synchronized ToolExecutionService execution() {
            return execution;
        }

        public PluginManager plugins() {
            return plugins;
        }

        public LanguageManager language() {
            return language;
        }

        public ToolTextResolver toolText() {
            return toolText;
        }

        public synchronized String submit(ToolRequest request) throws Exception {
            return execution.submit(project, request);
        }

        public synchronized String retry(String runId) throws Exception {
            return execution.retry(project, runId);
        }

        public synchronized void switchProject(Project next) throws Exception {
            if (project.root().toAbsolutePath().normalize()
                    .equals(next.root().toAbsolutePath().normalize())) {
                project = next;
                return;
            }
            boolean active = execution.recentJobs(10_000).stream()
                    .anyMatch(job -> job.status().name().equals("RUNNING")
                            || job.status().name().equals("QUEUED"));
            if (active) {
                throw new IllegalStateException(
                        "A project with queued or running jobs cannot be switched.");
            }
            SqliteJobStore store = new SqliteJobStore(next.databasePath());
            store.initialize();
            ToolExecutionService previous = execution;
            execution = new ToolExecutionService(registry, store);
            project = next;
            try {
                previous.close();
            } catch (Exception ignored) {
                // The new project is ready; a shutdown issue in the idle old service
                // must not make the switch appear to fail.
            }
        }

        @Override
        public synchronized void close() throws Exception {
            try {
                plugins.close();
            } finally {
                execution.close();
            }
        }
    }
}
