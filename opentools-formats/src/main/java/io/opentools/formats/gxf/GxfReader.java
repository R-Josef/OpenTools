package io.opentools.formats.gxf;

import io.opentools.api.tool.OpenToolException;
import io.opentools.formats.Compression;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GxfReader implements AutoCloseable {
    private static final Pattern GTF_ATTRIBUTE = Pattern.compile("(\\S+)\\s+\"([^\"]*)\"");
    private final BufferedReader reader;
    private long lineNumber;

    public GxfReader(Path path) throws IOException {
        this.reader = new BufferedReader(new InputStreamReader(Compression.open(path), StandardCharsets.UTF_8));
    }

    public GxfRecord next() throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (lineNumber == 1 && !line.isEmpty() && line.charAt(0) == '\ufeff') {
                line = line.substring(1);
            }
            if (line.isBlank() || line.startsWith("#")) {
                if (line.equals("##FASTA")) {
                    return null;
                }
                continue;
            }
            return parse(line);
        }
        return null;
    }

    private GxfRecord parse(String line) {
        String[] columns = line.split("\t", -1);
        if (columns.length != 9) {
            throw error("Expected 9 tab-separated columns, found " + columns.length);
        }
        long start;
        long end;
        try {
            start = Long.parseLong(columns[3]);
            end = Long.parseLong(columns[4]);
        } catch (NumberFormatException ex) {
            throw error("Start and end coordinates must be integers.");
        }
        if (start < 1 || end < start) {
            throw error("Invalid 1-based inclusive coordinates: " + start + ".." + end);
        }
        char strand = columns[6].isEmpty() ? '.' : columns[6].charAt(0);
        if (strand != '+' && strand != '-' && strand != '.') {
            throw error("Invalid strand: " + columns[6]);
        }
        return new GxfRecord(
                columns[0],
                columns[1],
                columns[2],
                start,
                end,
                columns[5],
                strand,
                columns[7],
                parseAttributes(columns[8]),
                columns[8],
                lineNumber
        );
    }

    private Map<String, List<String>> parseAttributes(String text) {
        Map<String, List<String>> attributes = new LinkedHashMap<>();
        if (text == null || text.isBlank() || text.equals(".")) {
            return attributes;
        }
        if (text.contains("=")) {
            parseGff3Attributes(text, attributes);
        } else {
            parseGtfAttributes(text, attributes);
        }
        return attributes;
    }

    private void parseGff3Attributes(String text, Map<String, List<String>> attributes) {
        for (String part : text.split(";")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = trimmed.substring(0, separator).trim();
            String value = decode(trimmed.substring(separator + 1).trim());
            for (String item : value.split(",")) {
                if (!item.isBlank()) {
                    attributes.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
                }
            }
        }
    }

    private void parseGtfAttributes(String text, Map<String, List<String>> attributes) {
        Matcher matcher = GTF_ATTRIBUTE.matcher(text);
        while (matcher.find()) {
            attributes.computeIfAbsent(matcher.group(1), ignored -> new ArrayList<>())
                    .add(decode(matcher.group(2)));
        }
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }

    private OpenToolException error(String message) {
        return new OpenToolException(
                "GXF_FORMAT_ERROR",
                message + " Near line " + lineNumber + ".",
                "Check that the file is GFF3 or GTF with 1-based inclusive coordinates."
        );
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
