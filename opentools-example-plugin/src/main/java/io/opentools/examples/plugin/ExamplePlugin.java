package io.opentools.examples.plugin;

import io.opentools.plugin.OpenToolsPlugin;
import io.opentools.plugin.PluginContext;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

public final class ExamplePlugin implements OpenToolsPlugin {
    @Override
    public void onEnable(PluginContext context) {
        context.log("Registering example plugin tools and page");
        context.registerTool(new CoinRunnerTool());
        context.registerTool(new HelloTool());
        context.registerPage("main", "Example Plugin", () -> page(context));
    }

    private static JPanel page(PluginContext context) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        panel.add(new JLabel(
                "<html><b>" + escape(context.text("page.main.heading", "Example OpenTools plugin"))
                        + "</b><br><br>"
                        + escape(context.text("page.main.pluginId", "Plugin ID")) + ": "
                        + escape(context.pluginId()) + "<br>"
                        + escape(context.text("page.main.dataDirectory", "Data directory")) + ": "
                        + escape(context.dataDirectory().toAbsolutePath().toString())
                        + "</html>"
        ), BorderLayout.NORTH);
        return panel;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
