package io.opentools.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MarkdownRendererTest {
    @Test
    void rendersSupportedBlocksAndStableAnchors() {
        String markdown = """
                # Quick start

                A paragraph with **bold**, `code`, and [CLI](cli.md#usage).

                ## Usage

                - first
                - second

                1. one
                2. two

                | Name | Value |
                | --- | --- |
                | A | 1 |

                ```text
                <not-html>
                ```

                ## Usage
                """;

        String html = MarkdownRenderer.render(markdown, "README.md");

        assertTrue(html.contains("<h1 id=\"quick-start\">"));
        assertTrue(html.contains("<h2 id=\"usage\">"));
        assertTrue(html.contains("<h2 id=\"usage-2\">"));
        assertTrue(html.contains("<ul>"));
        assertTrue(html.contains("<ol>"));
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("href=\"cli.md#usage\""));
        assertTrue(html.contains("&lt;not-html&gt;"));
        assertTrue(html.contains("table-layout: fixed"));
        assertTrue(html.contains("white-space: pre-wrap"));
    }

    @Test
    void neverExecutesRawHtmlScriptsOrUnsafeSchemes() {
        String markdown = """
                <script>alert('x')</script>

                [bad](javascript:alert(1))
                [absolute](C:\\data\\secret.txt)
                [traversal](../secret.md)
                [encoded](%2e%2e/secret.md)
                """;

        String html = MarkdownRenderer.render(markdown, "categories/sequence.md");

        assertFalse(html.toLowerCase().contains("<script"));
        assertFalse(html.contains("javascript:"));
        assertFalse(html.contains("secret.txt</a>"));
        assertFalse(html.contains("../secret.md"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    void permitsLocalAssetsExternalPagesAndInternalTaskRoutes() {
        String markdown = """
                ![local](assets/screenshots/tools-catalog.jpg)
                ![remote](https://example.org/image.png)
                [external](https://example.org/help)
                [tool](tool:fasta.stats)
                [guide](opentools-doc:README.md)
                """;

        String html = MarkdownRenderer.render(markdown, "README.md");

        assertTrue(html.contains("src=\"assets/screenshots/tools-catalog.jpg\""));
        assertFalse(html.contains("example.org/image.png"));
        assertTrue(html.contains("href=\"https://example.org/help\""));
        assertTrue(html.contains("href=\"opentools-tool:fasta.stats\""));
        assertTrue(html.contains("href=\"opentools-doc:README.md\""));
    }
}
