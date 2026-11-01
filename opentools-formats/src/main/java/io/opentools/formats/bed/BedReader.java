package io.opentools.formats.bed;

import io.opentools.api.tool.OpenToolException;
import io.opentools.formats.Compression;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BedReader implements AutoCloseable {
    private final BufferedReader reader;
    private long lineNumber;

    public BedReader(Path path) throws IOException {
        reader = new BufferedReader(new InputStreamReader(Compression.open(path), StandardCharsets.UTF_8));
    }

    public BedRecord next() throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (lineNumber == 1 && !line.isEmpty() && line.charAt(0) == '\ufeff') {
                line = line.substring(1);
            }
            if (line.isBlank() || line.startsWith("#") || line.startsWith("track")
                    || line.startsWith("browser")) {
                continue;
            }
            String[] fields = line.split("\\t", -1);
            if (fields.length < 3) {
                throw error("Expected at least three tab-separated BED columns.");
            }
            long start;
            long end;
            try {
                start = Long.parseLong(fields[1].trim());
                end = Long.parseLong(fields[2].trim());
            } catch (NumberFormatException ex) {
                throw error("BED start and end must be integers.");
            }
            if (start < 0 || end <= start) {
                throw error("Invalid BED 0-based half-open interval: " + start + ".." + end);
            }
            String name = fields.length >= 4 && !fields[3].isBlank() ? fields[3] : ".";
            String score = fields.length >= 5 && !fields[4].isBlank() ? fields[4] : ".";
            char strand = '.';
            if (fields.length >= 6 && !fields[5].isBlank()) {
                strand = fields[5].charAt(0);
                if (strand != '+' && strand != '-' && strand != '.') {
                    throw error("BED strand must be +, -, or .");
                }
            }
            List<String> extra = new ArrayList<>();
            for (int index = 6; index < fields.length; index++) {
                extra.add(fields[index]);
            }
            return new BedRecord(
                    fields[0].trim(),
                    start,
                    end,
                    name,
                    score,
                    strand,
                    extra,
                    lineNumber
            );
        }
        return null;
    }

    private OpenToolException error(String message) {
        return new OpenToolException(
                "BED_FORMAT_ERROR",
                message + " Near line " + lineNumber + ".",
                "Use BED with 0-based start, exclusive end, and tab-separated columns."
        );
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
