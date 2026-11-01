package io.opentools.formats.gxf;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class AnnotationTaskSupport {
    private AnnotationTaskSupport() {
    }

    static Set<String> tokens(String text, boolean ignoreCase) {
        Set<String> result = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        for (String line : text.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            for (String token : trimmed.split("[,;\\s]+")) {
                if (!token.isBlank()) {
                    result.add(ignoreCase ? token.toLowerCase(Locale.ROOT) : token);
                }
            }
        }
        return result;
    }

    static boolean matches(Set<String> values, String actual, boolean ignoreCase) {
        return values.isEmpty()
                || values.contains(ignoreCase ? actual.toLowerCase(Locale.ROOT) : actual);
    }

    static String firstId(GxfRecord record) {
        String id = record.firstAttribute("ID", "transcript_id", "gene_id", "Name");
        return id == null ? record.seqId() + ":" + record.start() + "-" + record.end() + ":" + record.type() : id;
    }

    static String attribute(GxfRecord record, String key) {
        if (key == null || key.isBlank() || key.equalsIgnoreCase("AUTO")) {
            return firstId(record);
        }
        String value = record.firstAttribute(key);
        return value == null ? firstId(record) : value;
    }

    static List<String> values(GxfRecord record, String key) {
        return record.attributes().getOrDefault(key, List.of());
    }
}
