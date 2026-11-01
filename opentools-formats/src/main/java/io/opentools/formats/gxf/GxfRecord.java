package io.opentools.formats.gxf;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

public record GxfRecord(
        String seqId,
        String source,
        String type,
        long start,
        long end,
        String score,
        char strand,
        String phase,
        Map<String, List<String>> attributes,
        String rawAttributes,
        long lineNumber
) {
    public GxfRecord {
        if (attributes == null || attributes.isEmpty()) {
            attributes = Map.of();
        } else {
            Map<String, List<String>> copy = new LinkedHashMap<>();
            attributes.forEach((key, values) -> copy.put(key, List.copyOf(values)));
            attributes = Collections.unmodifiableMap(copy);
        }
    }

    public String firstAttribute(String... names) {
        for (String name : names) {
            List<String> values = attributes.get(name);
            if (values != null && !values.isEmpty() && !values.getFirst().isBlank()) {
                return values.getFirst();
            }
        }
        return null;
    }

    public long length() {
        return end - start + 1;
    }
}
