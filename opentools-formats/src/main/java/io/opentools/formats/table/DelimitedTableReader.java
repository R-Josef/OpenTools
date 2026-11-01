package io.opentools.formats.table;

import io.opentools.api.tool.OpenToolException;
import io.opentools.formats.Compression;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * RFC 4180-style delimited reader with support for quoted delimiters and
 * embedded physical newlines.
 */
public final class DelimitedTableReader implements AutoCloseable {
    private final PushbackReader reader;
    private final char delimiter;
    private final boolean skipBlankRows;
    private final boolean skipCommentRows;
    private long physicalLine = 1;
    private boolean firstCharacter = true;

    public DelimitedTableReader(Path path, char delimiter) throws IOException {
        this(path, delimiter, true, false);
    }

    public DelimitedTableReader(
            Path path,
            char delimiter,
            boolean skipBlankRows,
            boolean skipCommentRows
    ) throws IOException {
        this.reader = new PushbackReader(new BufferedReader(new InputStreamReader(
                Compression.open(path), StandardCharsets.UTF_8)), 2);
        this.delimiter = delimiter;
        this.skipBlankRows = skipBlankRows;
        this.skipCommentRows = skipCommentRows;
    }

    public List<String> nextRow() throws IOException {
        while (true) {
            int first = readCharacter();
            if (first == -1) {
                return null;
            }
            if (firstCharacter) {
                firstCharacter = false;
                if (first == '\ufeff') {
                    continue;
                }
            }
            if (skipCommentRows && first == '#') {
                consumeToPhysicalLineEnd();
                continue;
            }
            if (first == '\r' || first == '\n') {
                consumeLineBreak(first);
                if (skipBlankRows) {
                    continue;
                }
                return List.of("");
            }
            List<String> row = parseRow(first);
            if (skipBlankRows && row.size() == 1 && row.getFirst().isEmpty()) {
                continue;
            }
            return row;
        }
    }

    private List<String> parseRow(int first) throws IOException {
        long recordLine = physicalLine;
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean afterClosingQuote = false;
        int character = first;
        while (true) {
            if (character == -1) {
                if (quoted) {
                    throw malformed("Unclosed quoted field", recordLine);
                }
                fields.add(current.toString());
                return List.copyOf(fields);
            }
            if (quoted) {
                if (character == '"') {
                    int next = readCharacter();
                    if (next == '"') {
                        current.append('"');
                    } else {
                        quoted = false;
                        afterClosingQuote = true;
                        unread(next);
                    }
                } else if (character == '\r' || character == '\n') {
                    consumeLineBreak(character);
                    current.append('\n');
                } else {
                    current.append((char) character);
                }
            } else if (character == delimiter) {
                fields.add(current.toString());
                current.setLength(0);
                afterClosingQuote = false;
            } else if (character == '\r' || character == '\n') {
                consumeLineBreak(character);
                fields.add(current.toString());
                return List.copyOf(fields);
            } else if (character == '"' && current.isEmpty() && !afterClosingQuote) {
                quoted = true;
            } else if (afterClosingQuote) {
                if (!Character.isWhitespace(character)) {
                    throw malformed(
                            "Unexpected character after a closing quote",
                            physicalLine
                    );
                }
            } else {
                current.append((char) character);
            }
            character = readCharacter();
        }
    }

    private void consumeToPhysicalLineEnd() throws IOException {
        int character;
        while ((character = readCharacter()) != -1) {
            if (character == '\r' || character == '\n') {
                consumeLineBreak(character);
                return;
            }
        }
    }

    private void consumeLineBreak(int first) throws IOException {
        if (first == '\r') {
            int next = readCharacter();
            if (next != '\n') {
                unread(next);
            }
        }
        physicalLine++;
    }

    private int readCharacter() throws IOException {
        return reader.read();
    }

    private void unread(int character) throws IOException {
        if (character != -1) {
            reader.unread(character);
        }
    }

    private static OpenToolException malformed(String message, long line) {
        return new OpenToolException(
                "DELIMITED_TABLE_MALFORMED",
                message + " near physical line " + line + ".",
                "Use doubled quotes for literal quotes and close every quoted field."
        );
    }

    public static char detectDelimiter(Path path) throws IOException {
        long[] counts = new long[3];
        boolean quoted = false;
        boolean first = true;
        boolean rowStarted = false;
        try (PushbackReader reader = new PushbackReader(new BufferedReader(new InputStreamReader(
                Compression.open(path), StandardCharsets.UTF_8)), 2)) {
            int character;
            while ((character = reader.read()) != -1) {
                if (first) {
                    first = false;
                    if (character == '\ufeff') {
                        continue;
                    }
                }
                if (!quoted && !rowStarted && character == '#') {
                    while ((character = reader.read()) != -1
                            && character != '\r' && character != '\n') {
                    }
                    if (character == -1) {
                        break;
                    }
                    continue;
                }
                if (character == '"') {
                    if (quoted) {
                        int next = reader.read();
                        if (next == '"') {
                            continue;
                        }
                        quoted = false;
                        if (next != -1) {
                            reader.unread(next);
                        }
                    } else {
                        quoted = true;
                    }
                    rowStarted = true;
                    continue;
                }
                if (!quoted) {
                    if (character == '\t') {
                        counts[0]++;
                        rowStarted = true;
                    } else if (character == ',') {
                        counts[1]++;
                        rowStarted = true;
                    } else if (character == ';') {
                        counts[2]++;
                        rowStarted = true;
                    } else if (character == '\r' || character == '\n') {
                        if (rowStarted) {
                            break;
                        }
                    } else if (!Character.isWhitespace(character)) {
                        rowStarted = true;
                    }
                }
            }
        }
        if (counts[0] >= counts[1] && counts[0] >= counts[2] && counts[0] > 0) {
            return '\t';
        }
        if (counts[1] >= counts[2] && counts[1] > 0) {
            return ',';
        }
        if (counts[2] > 0) {
            return ';';
        }
        return '\t';
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
