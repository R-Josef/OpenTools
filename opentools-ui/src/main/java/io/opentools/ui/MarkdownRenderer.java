package io.opentools.ui;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Small, deliberately limited Markdown renderer for bundled documentation.
 * Source HTML, scripts, remote images, absolute paths, and traversal are never
 * emitted into the Swing document.
 */
final class MarkdownRenderer {
    private static final Pattern ORDERED_LIST = Pattern.compile("^\\s*\\d+\\.\\s+.*");
    private static final Pattern UNORDERED_LIST = Pattern.compile("^\\s*[-*+]\\s+.*");
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".svg"
    );

    private final String sourcePath;
    private final Set<String> anchors = new HashSet<>();

    private MarkdownRenderer(String sourcePath) {
        this.sourcePath = normalizeSourcePath(sourcePath);
    }

    static String render(String markdown, String sourcePath) {
        return new MarkdownRenderer(sourcePath).render(markdown == null ? "" : markdown);
    }

    static String classifyLink(String target) {
        if (target == null || target.isBlank()) {
            return "unsafe";
        }
        String trimmed = target.trim();
        if (trimmed.startsWith("#")) {
            return "anchor";
        }
        if (trimmed.regionMatches(true, 0, "https://", 0, 8)
                || trimmed.regionMatches(true, 0, "http://", 0, 7)) {
            return "external";
        }
        if (isTaskTarget(trimmed)) {
            return "tool";
        }
        return "local";
    }

    private String render(String markdown) {
        List<String> lines = markdown.replace("\r\n", "\n").replace('\r', '\n').lines().toList();
        StringBuilder html = new StringBuilder(4096);
        html.append("""
                <html><head><style>
                body { font-family: sans-serif; color: #263238; margin: 18px 20px 36px 20px;
                       word-wrap: break-word; }
                h1 { font-size: 24px; margin: 8px 0 18px 0; color: #17324d; }
                h2 { font-size: 19px; margin: 24px 0 9px 0; color: #234e70; }
                h3 { font-size: 16px; margin: 18px 0 7px 0; color: #2d5d7b; }
                p { margin: 7px 0 12px 0; line-height: 1.45; }
                li { margin: 4px 0; line-height: 1.4; }
                code { font-family: monospace; background: #eef2f4; color: #263238;
                       white-space: pre-wrap; word-wrap: break-word; }
                pre { font-family: monospace; background: #f3f5f6; border: 1px solid #d7dde1;
                      padding: 10px; margin: 10px 0 15px 0; white-space: pre-wrap;
                      word-wrap: break-word; }
                table { border-collapse: collapse; margin: 10px 0 18px 0; width: 100%;
                        table-layout: fixed; }
                th { background: #e8eef2; text-align: left; }
                th, td { border: 1px solid #cbd4da; padding: 6px 8px; vertical-align: top;
                         white-space: normal; word-wrap: break-word; }
                a { color: #0b6396; }
                blockquote { margin: 10px 0; padding: 4px 12px; border-left: 4px solid #9eb3c2;
                             color: #40515c; background: #f5f8f9; }
                hr { border: 0; border-top: 1px solid #cbd4da; margin: 18px 0; }
                img { max-width: 100%; }
                </style></head><body>
                """);

        for (int index = 0; index < lines.size(); ) {
            String line = lines.get(index);
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                index++;
                continue;
            }
            if (trimmed.startsWith("```")) {
                StringBuilder code = new StringBuilder();
                index++;
                while (index < lines.size() && !lines.get(index).trim().startsWith("```")) {
                    code.append(lines.get(index++)).append('\n');
                }
                if (index < lines.size()) {
                    index++;
                }
                html.append("<pre><code>").append(escape(code.toString())).append("</code></pre>\n");
                continue;
            }
            if (index + 1 < lines.size() && isTableHeader(line, lines.get(index + 1))) {
                List<String> headers = splitTableRow(line);
                html.append("<table><thead><tr>");
                for (String header : headers) {
                    html.append("<th>").append(renderInline(header)).append("</th>");
                }
                html.append("</tr></thead><tbody>");
                index += 2;
                while (index < lines.size() && lines.get(index).contains("|")
                        && !lines.get(index).trim().isEmpty()) {
                    html.append("<tr>");
                    List<String> cells = splitTableRow(lines.get(index++));
                    for (int column = 0; column < headers.size(); column++) {
                        String value = column < cells.size() ? cells.get(column) : "";
                        html.append("<td>").append(renderInline(value)).append("</td>");
                    }
                    html.append("</tr>");
                }
                html.append("</tbody></table>\n");
                continue;
            }
            var heading = HEADING.matcher(trimmed);
            if (heading.matches()) {
                int level = heading.group(1).length();
                String title = heading.group(2);
                String anchor = uniqueAnchor(slug(title));
                html.append("<h").append(level)
                        .append(" id=\"").append(anchor).append("\">")
                        .append(renderInline(title))
                        .append("</h").append(level).append(">\n");
                index++;
                continue;
            }
            if (isHorizontalRule(trimmed)) {
                html.append("<hr>\n");
                index++;
                continue;
            }
            if (ORDERED_LIST.matcher(line).matches() || UNORDERED_LIST.matcher(line).matches()) {
                boolean ordered = ORDERED_LIST.matcher(line).matches();
                html.append(ordered ? "<ol>\n" : "<ul>\n");
                Pattern pattern = ordered ? ORDERED_LIST : UNORDERED_LIST;
                while (index < lines.size() && pattern.matcher(lines.get(index)).matches()) {
                    String value = lines.get(index++)
                            .replaceFirst(ordered ? "^\\s*\\d+\\.\\s+" : "^\\s*[-*+]\\s+", "");
                    html.append("<li>").append(renderInline(value)).append("</li>\n");
                }
                html.append(ordered ? "</ol>\n" : "</ul>\n");
                continue;
            }
            if (trimmed.startsWith(">")) {
                StringBuilder quote = new StringBuilder();
                while (index < lines.size() && lines.get(index).trim().startsWith(">")) {
                    String value = lines.get(index++).trim().substring(1).trim();
                    if (!value.isEmpty()) {
                        if (!quote.isEmpty()) {
                            quote.append(' ');
                        }
                        quote.append(value);
                    }
                }
                html.append("<blockquote>").append(renderInline(quote.toString()))
                        .append("</blockquote>\n");
                continue;
            }

            StringBuilder paragraph = new StringBuilder();
            while (index < lines.size() && !isBlockStart(lines, index)) {
                if (!paragraph.isEmpty()) {
                    paragraph.append(' ');
                }
                paragraph.append(lines.get(index++).trim());
            }
            if (paragraph.isEmpty() && index < lines.size()) {
                paragraph.append(lines.get(index++).trim());
            }
            html.append("<p>").append(renderInline(paragraph.toString())).append("</p>\n");
        }
        html.append("</body></html>");
        return html.toString();
    }

    private String renderInline(String markdown) {
        StringBuilder output = new StringBuilder();
        int index = 0;
        while (index < markdown.length()) {
            if (markdown.startsWith("![", index)) {
                Link parsed = parseLink(markdown, index, true);
                if (parsed != null) {
                    String source = localImageSource(parsed.target());
                    if (source != null) {
                        output.append("<img src=\"").append(attribute(source))
                                .append("\" alt=\"").append(attribute(plainText(parsed.label())))
                                .append("\">");
                    } else {
                        output.append(escape(parsed.label()));
                    }
                    index = parsed.end();
                    continue;
                }
            }
            if (markdown.charAt(index) == '[') {
                Link parsed = parseLink(markdown, index, false);
                if (parsed != null) {
                    String resolved = resolveLink(parsed.target());
                    if (resolved == null) {
                        output.append(escape(parsed.label()));
                    } else {
                        output.append("<a href=\"").append(attribute(resolved)).append("\">")
                                .append(renderInline(parsed.label()))
                                .append("</a>");
                    }
                    index = parsed.end();
                    continue;
                }
            }
            if (markdown.startsWith("**", index)) {
                int close = markdown.indexOf("**", index + 2);
                if (close > index + 2) {
                    output.append("<b>").append(renderInline(markdown.substring(index + 2, close)))
                            .append("</b>");
                    index = close + 2;
                    continue;
                }
            }
            if (markdown.charAt(index) == '`') {
                int close = markdown.indexOf('`', index + 1);
                if (close > index + 1) {
                    output.append("<code>")
                            .append(escape(markdown.substring(index + 1, close)))
                            .append("</code>");
                    index = close + 1;
                    continue;
                }
            }
            output.append(escape(String.valueOf(markdown.charAt(index))));
            index++;
        }
        return output.toString();
    }

    private String resolveLink(String rawTarget) {
        String target = rawTarget == null ? "" : rawTarget.trim();
        String type = classifyLink(target);
        if ("external".equals(type)) {
            return target;
        }
        if ("tool".equals(type)) {
            return "opentools-tool:" + target.substring("tool:".length());
        }
        if ("anchor".equals(type)) {
            String anchor = target.substring(1);
            return isSafeAnchor(anchor) ? "#" + anchor : null;
        }
        String safe = safeRelativeTarget(target, false);
        return safe == null ? null : safe;
    }

    private String localImageSource(String rawTarget) {
        String target = rawTarget == null ? "" : rawTarget.trim();
        if (!"local".equals(classifyLink(target))) {
            return null;
        }
        return safeRelativeTarget(target, true);
    }

    private String safeRelativeTarget(String target, boolean image) {
        String pathPart = target;
        String anchor = "";
        int hash = target.indexOf('#');
        if (hash >= 0) {
            pathPart = target.substring(0, hash);
            anchor = target.substring(hash + 1);
            if (!anchor.isEmpty() && !isSafeAnchor(anchor)) {
                return null;
            }
        }
        if (pathPart.isEmpty()) {
            return anchor.isEmpty() ? null : "#" + anchor;
        }
        String decoded;
        try {
            decoded = URLDecoder.decode(pathPart.replace("+", "%2B"), StandardCharsets.UTF_8)
                    .replace('\\', '/');
        } catch (IllegalArgumentException ex) {
            return null;
        }
        if (decoded.startsWith("/") || decoded.startsWith("//")
                || decoded.matches("^[A-Za-z]:.*")
                || decoded.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*")
                || decoded.contains("\0")) {
            return null;
        }
        for (String segment : decoded.split("/")) {
            String normalized = segment.toLowerCase(Locale.ROOT);
            if (normalized.equals("..") || normalized.equals("%2e%2e")) {
                return null;
            }
        }
        Path source;
        Path resolved;
        try {
            source = Path.of(sourcePath).normalize();
            Path parent = source.getParent();
            resolved = (parent == null ? Path.of(decoded) : parent.resolve(decoded)).normalize();
        } catch (InvalidPathException ex) {
            return null;
        }
        String normalized = resolved.toString().replace('\\', '/');
        if (normalized.startsWith("../") || normalized.startsWith("/") || normalized.isBlank()) {
            return null;
        }
        if (image) {
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (IMAGE_EXTENSIONS.stream().noneMatch(lower::endsWith)) {
                return null;
            }
        }
        return normalized + (anchor.isEmpty() ? "" : "#" + anchor);
    }

    private static boolean isTableHeader(String line, String separator) {
        return line.contains("|") && separator.contains("-")
                && separator.matches("^\\s*\\|?\\s*:?-{3,}:?.*");
    }

    private static List<String> splitTableRow(String line) {
        String value = line.trim();
        if (value.startsWith("|")) {
            value = value.substring(1);
        }
        if (value.endsWith("|")) {
            value = value.substring(0, value.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' && !escaped) {
                escaped = true;
                continue;
            }
            if (character == '|' && !escaped) {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(character);
            }
            escaped = false;
        }
        cells.add(current.toString().trim());
        return cells;
    }

    private static boolean isBlockStart(List<String> lines, int index) {
        String trimmed = lines.get(index).trim();
        if (trimmed.isEmpty() || trimmed.startsWith("```") || HEADING.matcher(trimmed).matches()
                || trimmed.startsWith(">") || isHorizontalRule(trimmed)
                || ORDERED_LIST.matcher(lines.get(index)).matches()
                || UNORDERED_LIST.matcher(lines.get(index)).matches()) {
            return true;
        }
        return index + 1 < lines.size() && isTableHeader(lines.get(index), lines.get(index + 1));
    }

    private static boolean isHorizontalRule(String value) {
        return value.matches("^\\s*([-*_])(\\s*\\1){2,}\\s*$");
    }

    private static Link parseLink(String markdown, int start, boolean image) {
        int labelStart = start + (image ? 2 : 1);
        int labelEnd = markdown.indexOf(']', labelStart);
        if (labelEnd < 0 || labelEnd + 1 >= markdown.length()
                || markdown.charAt(labelEnd + 1) != '(') {
            return null;
        }
        int targetStart = labelEnd + 2;
        int targetEnd = markdown.indexOf(')', targetStart);
        if (targetEnd < 0) {
            return null;
        }
        String target = markdown.substring(targetStart, targetEnd).trim();
        int titleMarker = target.lastIndexOf(" \"");
        if (titleMarker > 0 && target.endsWith("\"")) {
            target = target.substring(0, titleMarker).trim();
        }
        return new Link(markdown.substring(labelStart, labelEnd), target, targetEnd + 1);
    }

    private String uniqueAnchor(String base) {
        String candidate = base.isBlank() ? "section" : base;
        int suffix = 2;
        while (!anchors.add(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static String slug(String raw) {
        String plain = plainText(raw).toLowerCase(Locale.ROOT).trim();
        String normalized = Normalizer.normalize(plain, Normalizer.Form.NFKC)
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized;
    }

    private static String plainText(String raw) {
        return raw.replace("**", "").replace("`", "")
                .replaceAll("!?\\[([^]]+)]\\([^)]+\\)", "$1");
    }

    private static boolean isTaskTarget(String target) {
        return target.startsWith("tool:") && target.matches("^tool:[a-z0-9][a-z0-9._-]*$");
    }

    private static boolean isSafeAnchor(String anchor) {
        return anchor.matches("^[\\p{L}\\p{N}_-]+$");
    }

    private static String normalizeSourcePath(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return "README.md";
        }
        Path path = Path.of(sourcePath).normalize();
        String normalized = path.toString().replace('\\', '/');
        if (normalized.startsWith("../") || normalized.startsWith("/")
                || normalized.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Unsafe documentation source path: " + sourcePath);
        }
        return normalized;
    }

    private static String attribute(String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private record Link(String label, String target, int end) {
    }
}
