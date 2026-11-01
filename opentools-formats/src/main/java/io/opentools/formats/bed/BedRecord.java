package io.opentools.formats.bed;

import java.util.List;

public record BedRecord(
        String seqId,
        long start,
        long end,
        String name,
        String score,
        char strand,
        List<String> extraFields,
        long lineNumber
) {
    public BedRecord {
        extraFields = extraFields == null ? List.of() : List.copyOf(extraFields);
    }

    public long length() {
        return end - start;
    }
}
