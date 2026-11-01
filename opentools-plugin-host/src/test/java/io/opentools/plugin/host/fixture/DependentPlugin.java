package io.opentools.plugin.host.fixture;

import io.opentools.plugin.OpenToolsPlugin;
import io.opentools.plugin.PluginContext;

import javax.swing.JPanel;

public class DependentPlugin implements OpenToolsPlugin {
    @Override
    public void onEnable(PluginContext context) {
        context.registerPage("main", "Dependent Page", JPanel::new);
    }
}
