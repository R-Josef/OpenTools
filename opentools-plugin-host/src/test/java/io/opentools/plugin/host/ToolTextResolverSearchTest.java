package io.opentools.plugin.host;

import io.opentools.api.tool.ToolDescriptor;
import io.opentools.core.i18n.LanguageManager;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ToolTextResolverSearchTest {
    @Test
    void searchUsesLocalizedTextAndLanguageSearchTermsOnly() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("lang/en_US.properties"))) {
            root = root.getParent();
        }
        assertNotNull(root, "repository language directory");
        LanguageManager language = new LanguageManager(root, "zh_CN");
        ToolTextResolver resolver = ToolTextResolver.hostOnly(language);
        ToolDescriptor descriptor = new ToolDescriptor(
                "go.level",
                "GO Term Levels",
                "Calculate shortest root distance for terms in a local GO parent hierarchy.",
                "Enrichment",
                List.of("hidden-english-alias"),
                List.of("TSV"),
                List.of("TSV"),
                "1.1.0",
                List.of()
        );

        String search = resolver.searchText(descriptor);

        assertTrue(search.contains("go 层级"));
        assertTrue(search.contains("go level"));
        assertTrue(search.contains("go层级"));
        assertFalse(search.contains("hidden-english-alias"));
    }
}
