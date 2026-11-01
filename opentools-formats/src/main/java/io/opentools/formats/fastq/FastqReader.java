package io.opentools.formats.fastq;

import io.opentools.api.tool.OpenToolException;
import io.opentools.formats.Compression;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class FastqReader implements AutoCloseable {
    private final BufferedReader reader;
    private long lineNumber;

    public FastqReader(Path path) throws IOException {
        reader = new BufferedReader(new InputStreamReader(Compression.open(path), StandardCharsets.UTF_8));
    }

    public FastqRecord next() throws IOException {
        String headerLine = reader.readLine();
        if (headerLine == null) {
            return null;
        }
        long recordLine = ++lineNumber;
        if (recordLine == 1 && !headerLine.isEmpty() && headerLine.charAt(0) == '\ufeff') {
            headerLine = headerLine.substring(1);
        }
        if (!headerLine.startsWith("@")) {
            throw error("Expected a FASTQ header beginning with '@'.", recordLine);
        }
        String sequence = readRequired("sequence", recordLine);
        String plus = readRequired("'+' separator", recordLine);
        if (!plus.startsWith("+")) {
            throw error("Expected a FASTQ '+' separator.", lineNumber);
        }
        String quality = readRequired("quality", recordLine);
        if (sequence.length() != quality.length()) {
            throw error("Sequence and quality lengths differ: " + sequence.length()
                    + " vs " + quality.length() + ".", recordLine);
        }
        String header = headerLine.substring(1).trim();
        String id = header.isBlank() ? "" : header.split("\\s+", 2)[0];
        return new FastqRecord(id, header, sequence, quality, recordLine);
    }

    private String readRequired(String part, long recordLine) throws IOException {
        String line = reader.readLine();
        lineNumber++;
        if (line == null) {
            throw error("Unexpected end of file while reading " + part + ".", recordLine);
        }
        return line;
    }

    private OpenToolException error(String message, long line) {
        return new OpenToolException(
                "FASTQ_FORMAT_ERROR",
                message + " Near line " + line + ".",
                "Provide four-line FASTQ records with equal sequence and quality lengths."
        );
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
