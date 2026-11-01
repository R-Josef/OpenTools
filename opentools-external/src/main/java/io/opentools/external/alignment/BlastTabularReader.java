package io.opentools.external.alignment;

import io.opentools.api.tool.OpenToolException;
import io.opentools.formats.Compression;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BlastTabularReader {
    public List<BlastTabularHit> read(Path input) throws Exception {
        List<BlastTabularHit> hits = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(input), StandardCharsets.UTF_8))) {
            String line;
            long lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\t", -1);
                if (fields.length < 12) {
                    throw error(lineNumber, "Expected at least 12 BLAST outfmt 6 columns.");
                }
                try {
                    hits.add(new BlastTabularHit(
                            fields[0],
                            fields[1],
                            Double.parseDouble(fields[2]),
                            Long.parseLong(fields[3]),
                            Long.parseLong(fields[4]),
                            Long.parseLong(fields[5]),
                            Long.parseLong(fields[6]),
                            Long.parseLong(fields[7]),
                            Long.parseLong(fields[8]),
                            Long.parseLong(fields[9]),
                            Double.parseDouble(fields[10]),
                            Double.parseDouble(fields[11])
                    ));
                } catch (NumberFormatException ex) {
                    throw error(lineNumber, "One or more numeric BLAST fields are invalid.");
                }
            }
        }
        return hits;
    }

    private static OpenToolException error(long line, String message) {
        return new OpenToolException(
                "BLAST_TABLE_FORMAT_ERROR",
                message + " Near line " + line + ".",
                "Use BLAST outfmt 6 with qseqid sseqid pident length mismatch gapopen qstart qend sstart send evalue bitscore."
        );
    }
}
