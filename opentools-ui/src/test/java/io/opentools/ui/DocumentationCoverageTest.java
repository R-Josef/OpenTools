package io.opentools.ui;

import io.opentools.analysis.BuiltInAnalysis;
import io.opentools.core.i18n.LanguageManager;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.external.ExternalTools;
import io.opentools.formats.BuiltInTools;
import io.opentools.visualization.BuiltInVisualizations;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import javax.swing.JScrollPane;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DocumentationCoverageTest {
    private static ToolRegistry registry;
    private static LanguageManager language;

    @BeforeAll
    static void registerBuiltIns() {
        registry = new ToolRegistry();
        BuiltInTools.register(registry);
        BuiltInAnalysis.register(registry);
        ExternalTools.register(registry);
        BuiltInVisualizations.register(registry);
        language = new LanguageManager(findProjectRoot(), "zh_CN");
    }

    @Test
    void everyBuiltInTaskHasCompleteChineseMarkdown() {
        assertEquals(86, registry.list().size());
        for (ToolDescriptor tool : registry.list()) {
            String markdown = ToolDocumentation.render(tool, language);
            assertTrue(markdown.contains("## 用途与适用场景"), tool.id());
            assertTrue(markdown.contains("## 输入格式"), tool.id());
            assertTrue(markdown.contains("## 输出格式"), tool.id());
            assertTrue(markdown.contains("## 关键参数、默认值与范围"), tool.id());
            assertTrue(markdown.contains("## 典型示例"), tool.id());
            assertTrue(markdown.contains("## 注意事项和失败边界"), tool.id());
            assertFalse(markdown.contains("| 参数 | 类型 | 必填 |"), tool.id());
            assertTrue(markdown.contains("`" + tool.id() + "`"), tool.id());
            assertFalse(markdown.contains("null"), tool.id());
            for (ToolParameter parameter : tool.parameters()) {
                assertTrue(markdown.contains("`" + parameter.id() + "`"), tool.id() + "/" + parameter.id());
            }
            String html = MarkdownRenderer.render(markdown, "README.md");
            assertTrue(html.contains("<h1"), tool.id());
            assertFalse(html.isBlank(), tool.id());
        }
    }

    @Test
    void docsNavigationContainsAllCategoriesAndTasks() throws Exception {
        AtomicReference<DocsPanel> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(new DocsPanel(registry, language)));
        DocsPanel panel = reference.get();

        assertEquals(86, panel.navigationToolCount());
        assertEquals(8, panel.navigationCategoryCount());
        assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, panel.horizontalScrollPolicy());
        ToolDescriptor descriptor = registry.find("fasta.stats").orElseThrow().descriptor();
        assertTrue(DocResources.readToolDocumentation("zh_CN", descriptor).isPresent());
        SwingUtilities.invokeAndWait(() -> panel.openTool("fasta.stats"));
        assertEquals("tool:fasta.stats", panel.activeDocument());
        assertTrue(panel.viewerText().contains("CUSTOM-DOC: fasta.stats"), panel.viewerText());
        assertTrue(panel.viewerText().contains("<hr>"));
        assertTrue(panel.viewerText().contains("opentools-doc:README.md"));
    }

    @Test
    void allPublicMarkdownAndImagesArePackaged() throws Exception {
        List<String> documents = List.of(
                "README.md",
                "quick-start.md",
                "installation.md",
                "cli.md",
                "mcp.md",
                "tool-reference.md",
                "file-formats.md",
                "faq.md",
                "plugin-development.md",
                "errors-exit-codes.md",
                "external-tools.md",
                "release-packages.md",
                "ui-and-docs.md",
                "categories/sequence.md",
                "categories/annotation-bed.md",
                "categories/table.md",
                "categories/alignment-phylogeny.md",
                "categories/expression-enrichment.md",
                "categories/hts.md",
                "categories/visualization.md",
                "categories/plugin.md",
                "categories/sequence/fasta.stats.md"
        );
        for (String languageCode : List.of("en_US", "zh_CN")) {
            for (String document : documents) {
                assertFalse(DocResources.read(languageCode, document).isBlank(),
                        languageCode + "/" + document);
                assertTrue(DocResources.url(languageCode, document) != null,
                        languageCode + "/" + document);
            }
            assertTrue(DocResources.url(languageCode, "assets/screenshots/tools-catalog.jpg") != null);
            assertTrue(DocResources.url(languageCode, "assets/screenshots/docs-home.png") != null);
        }
        assertTrue(DocResources.url("en_US", "assets/screenshots/docs-small-window.png") != null);
    }

    @Test
    void publicMarkdownLocalLinksResolveWithoutTraversalOrNetworkImages() throws Exception {
        List<String> documents = List.of(
                "README.md", "quick-start.md", "installation.md", "cli.md", "mcp.md",
                "tool-reference.md", "file-formats.md", "faq.md", "plugin-development.md",
                "errors-exit-codes.md", "external-tools.md", "release-packages.md", "ui-and-docs.md",
                "categories/sequence.md", "categories/annotation-bed.md", "categories/table.md",
                "categories/alignment-phylogeny.md", "categories/expression-enrichment.md",
                "categories/hts.md", "categories/visualization.md", "categories/plugin.md",
                "categories/sequence/fasta.stats.md"
        );
        Pattern links = Pattern.compile("!?\\[[^]]*]\\(([^)]+)\\)");
        for (String languageCode : List.of("en_US", "zh_CN")) {
            Set<String> checked = new HashSet<>();
            for (String document : documents) {
                Matcher matcher = links.matcher(DocResources.read(languageCode, document));
                while (matcher.find()) {
                    String target = matcher.group(1).trim();
                    if (target.startsWith("http://") || target.startsWith("https://")
                            || target.startsWith("#") || target.startsWith("tool:")
                            || target.startsWith("opentools-doc:")) {
                        assertFalse(target.contains("]("), document);
                        continue;
                    }
                    assertFalse(target.startsWith("/"), document);
                    assertFalse(target.contains(".."), document + ": " + target);
                    String path = target.split("#", 2)[0];
                    Path resolved = Path.of(document).getParent() == null
                            ? Path.of(path)
                            : Path.of(document).getParent().resolve(path);
                    String normalized = resolved.normalize().toString().replace('\\', '/');
                    assertTrue(DocResources.url(languageCode, normalized) != null,
                            languageCode + "/" + document + ": " + target);
                    checked.add(normalized);
                }
            }
            assertTrue(checked.contains("quick-start.md"), languageCode);
            assertTrue(checked.contains("en_US".equals(languageCode)
                    ? "assets/screenshots/docs-small-window.png"
                    : "assets/screenshots/docs-wide.png"), languageCode);
            assertTrue(checked.contains("assets/screenshots/docs-task.png"), languageCode);
            if ("zh_CN".equals(languageCode)) {
                assertTrue(checked.contains("assets/screenshots/docs-scale150.png"), languageCode);
            }
        }
    }

    @Test
    void docsFollowTheActiveLanguageWithoutRestartingTheProcess() throws Exception {
        LanguageManager live = new LanguageManager(findProjectRoot(), "zh_CN");
        AtomicReference<DocsPanel> chinese = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> chinese.set(new DocsPanel(registry, live)));
        assertEquals("zh_CN", chinese.get().docsLanguage());
        assertTrue(chinese.get().viewerText().contains("OpenTools"));

        live.setLanguage("en_US");
        AtomicReference<DocsPanel> english = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> english.set(new DocsPanel(registry, live)));
        assertEquals("en_US", english.get().docsLanguage());
        assertTrue(english.get().viewerText().contains("OpenTools"));
        assertEquals("en_US", DocResources.docsLanguage(live.activeCode()));
    }

    @Test
    void englishTaskFallbackIsExplicitAndNeverBlank() {
        LanguageManager english = new LanguageManager(findProjectRoot(), "en_US");
        ToolDescriptor tool = registry.find("fasta.stats").orElseThrow().descriptor();
        String markdown = ToolDocumentation.render(tool, english);

        assertTrue(markdown.contains("## Purpose and use cases"));
        assertTrue(markdown.contains("## Key parameters, defaults, and ranges"));
        assertTrue(markdown.contains("## Example"));
        assertFalse(markdown.isBlank());
    }

    @Test
    void repositoryRootLinksEnglishAndChineseReadmesAndLanguageRoots() throws Exception {
        Path root = findProjectRoot();
        String english = Files.readString(root.resolve("README.md"));
        String chinese = Files.readString(root.resolve("README.zh-CN.md"));

        assertTrue(english.contains("[简体中文](README.zh-CN.md)"));
        assertTrue(chinese.contains("[English](README.md)"));
        assertTrue(english.contains("docs/en_US/README.md"));
        assertTrue(chinese.contains("docs/zh_CN/README.md"));
        assertTrue(Files.isRegularFile(root.resolve("docs/en_US/README.md")));
        assertTrue(Files.isRegularFile(root.resolve("docs/zh_CN/README.md")));
    }

    private static Path findProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("lang/zh_CN.properties"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate project root");
    }
}
