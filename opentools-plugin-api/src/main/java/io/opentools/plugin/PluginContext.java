package io.opentools.plugin;

import io.opentools.api.tool.OpenTool;

import javax.swing.JPanel;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Host-owned context for registering plugin contributions.
 *
 * <p>Access to tools, pages, and the plugin data directory is controlled by the
 * capabilities declared in the plugin descriptor.</p>
 */
public interface PluginContext {
    /**
     * Returns the unique plugin identifier from the plugin descriptor.
     *
     * @return plugin identifier
     */
    String pluginId();

    /**
     * Returns the plugin's private data directory.
     *
     * @return writable private directory
     * @throws SecurityException if the plugin did not request the {@code data} capability
     */
    Path dataDirectory();

    /**
     * Appends a message to the plugin log.
     *
     * @param message diagnostic text
     */
    void log(String message);

    /**
     * Registers a headless tool.
     *
     * @param tool tool implementation
     * @throws SecurityException if the plugin did not request the {@code tool} capability
     */
    void registerTool(OpenTool tool);

    /**
     * Registers a page shown in the desktop workbench.
     *
     * @param id page identifier unique within the plugin
     * @param title page title
     * @param panelFactory factory creating the page component
     * @throws SecurityException if the plugin did not request the {@code page} capability
     */
    void registerPage(String id, String title, Supplier<JPanel> panelFactory);
}
