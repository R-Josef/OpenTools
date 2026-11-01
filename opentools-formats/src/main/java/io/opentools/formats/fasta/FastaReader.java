package io.opentools.formats.fasta;

import io.opentools.api.tool.OpenToolException;
import io.opentools.formats.Compression;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class FastaReader implements AutoCloseable {
    private final BufferedReader reader;
    private String pendingHeader;
    private long lineNumber;

    public FastaReader(Path path) throws IOException {
        this.reader = new BufferedReader(new InputStreamReader(Compression.open(path), StandardCharsets.UTF_8));
    }

    public FastaRecord next() throws IOException {
        String header = pendingHeader;
        pendingHeader = null;
        String line;
        while (header == null && (line = readLine()) != null) {
            String normalized = normalize(line);
            if (normalized.isBlank() || normalized.startsWith(";")) {
                continue;
            }
            if (!normalized.startsWith(">")) {
                throw new OpenToolException(
                        "FASTA_HEADER_EXPECTED",
                        "Expected a FASTA header near line " + lineNumber,
                        "The file must begin each record with '>'."
                );
            }
            header = normalized.substring(1).trim();
        }
        if (header == null) {
            return null;
        }

        StringBuilder sequence = new StringBuilder();
        while ((line = readLine()) != null) {
            String normalized = normalize(line);
            if (normalized.startsWith(">")) {
                pendingHeader = normalized.substring(1).trim();
                break;
            }
            if (normalized.startsWith(";")) {
                continue;
            }
            sequence.append(normalized.replaceAll("\\s+", ""));
        }
        String id = header.isBlank() ? "" : header.split("\\s+", 2)[0];
        return new FastaRecord(id, header, sequence.toString());
    }

    private String readLine() throws IOException {
        String line = reader.readLine();
        if (line != null) {
            lineNumber++;
        }
        return line;
    }

    private static String normalize(String line) {
        return !line.isEmpty() && line.charAt(0) == '\ufeff' ? line.substring(1) : line;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
