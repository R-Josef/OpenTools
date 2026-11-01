package io.opentools.plugin;

/**
 * Entry point implemented by an OpenTools plugin.
 *
 * <p>The host creates one instance of the declared main class and invokes
 * {@link #onEnable(PluginContext)} once when the plugin is enabled.</p>
 */
public interface OpenToolsPlugin {
    /**
     * Registers the plugin's tools and pages.
     *
     * @param context host-owned registration and data context
     * @throws Exception if registration fails; the host will roll back contributions
     */
    void onEnable(PluginContext context) throws Exception;

    /**
     * Releases resources when the plugin is disabled or the host shuts down.
     *
     * @throws Exception if cleanup fails; the host records the failure and continues
     */
    default void onDisable() throws Exception {
    }
}
