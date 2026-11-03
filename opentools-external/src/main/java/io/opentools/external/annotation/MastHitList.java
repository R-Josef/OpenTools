package io.opentools.external.annotation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses MAST's {@code -hit_list} output when the platform's MAST build cannot
 * emit mast.xml, and writes a minimal MAST-compatible XML document.
 */
final class MastHitList {
    private MastHitList() {
    }

    static List<MemeResults.MastHit> parse(String output) {
        List<MemeResults.MastHit> hits = new ArrayList<>();
        if (output == null) {
            return hits;
        }
        for (String line : output.split("\\R")) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.trim().split("\\s+");
            if (fields.length < 8) {
                continue;
            }
            try {
                hits.add(new MemeResults.MastHit(
                        fields[0],
                        fields[2],
                        Integer.parseInt(fields[4]),
                        Integer.parseInt(fields[5]),
                        fields[1],
                        Double.parseDouble(fields[7]),
                        fields[3]
                ));
            } catch (NumberFormatException ignored) {
                // Skip non-tabular diagnostic lines.
            }
        }
        return List.copyOf(hits);
    }

    static void writeXml(Path output, List<MemeResults.MastHit> hits) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<mast>\n<sequences>\n");
        String currentSequence = null;
        for (MemeResults.MastHit hit : hits) {
            if (!hit.sequence().equals(currentSequence)) {
                if (currentSequence != null) {
                    xml.append("</sequence>\n");
                }
                currentSequence = hit.sequence();
                xml.append("<sequence id=\"").append(escape(currentSequence)).append("\">\n");
            }
            xml.append("<seg start=\"").append(hit.start())
                    .append("\" end=\"").append(hit.end())
                    .append("\" strand=\"").append(escape(hit.strand())).append("\">\n");
            xml.append("<hit motiff=\"").append(escape(hit.motif()))
                    .append("\" position=\"").append(Math.max(0, hit.start() - 1))
                    .append("\" start=\"").append(hit.start())
                    .append("\" end=\"").append(hit.end())
                    .append("\" strand=\"").append(escape(hit.strand()))
                    .append("\" pvalue=\"").append(hit.pvalue())
                    .append("\" match=\"").append(escape(hit.match()))
                    .append("\"/>\n</seg>\n");
        }
        if (currentSequence != null) {
            xml.append("</sequence>\n");
        }
        xml.append("</sequences>\n</mast>\n");
        Files.writeString(output, xml, StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
