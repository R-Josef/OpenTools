package io.opentools.plugin.host;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PluginClassLoaderTest {
    @Test
    void sharesPublicContractsWithTheHost() {
        assertTrue(PluginClassLoader.sharedWithHost("io.opentools.api.tool.OpenTool"));
        assertTrue(PluginClassLoader.sharedWithHost("io.opentools.plugin.OpenToolsPlugin"));
        assertTrue(PluginClassLoader.sharedWithHost("io.opentools.core.tool.ToolRegistry"));
        assertTrue(PluginClassLoader.sharedWithHost("java.util.List"));
    }

    @Test
    void keepsPluginPrivatePackagesChildFirst() {
        assertFalse(PluginClassLoader.sharedWithHost("com.example.plugin.Plugin"));
        assertFalse(PluginClassLoader.sharedWithHost("io.opentools.external.ExternalTools"));
    }
}
