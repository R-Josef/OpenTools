package io.opentools.external.annotation;

import io.opentools.api.tool.OpenToolException;
import io.opentools.formats.Compression;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class HmmerDomTableParser {
    public List<HmmerDomainHit> parse(Path input) throws Exception {
        List<HmmerDomainHit> hits = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(input), StandardCharsets.UTF_8))) {
            String line;
            long lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.trim().split("\\s+", 23);
                if (fields.length < 22) {
                    throw new OpenToolException(
                            "HMMER_DOMTBL_FORMAT_ERROR",
                            "Expected at least 22 domtblout columns near line " + lineNumber + ".",
                            "Use HMMER --domtblout output."
                    );
                }
                try {
                    hits.add(new HmmerDomainHit(
                            fields[0], fields[1], fields[3],
                            Double.parseDouble(fields[6]), Double.parseDouble(fields[7]),
                            Double.parseDouble(fields[12]),
                            Long.parseLong(fields[17]), Long.parseLong(fields[18]),
                            Long.parseLong(fields[15]), Long.parseLong(fields[16]),
                            Double.parseDouble(fields[21]),
                            fields.length > 22 ? fields[22] : ""
                    ));
                } catch (NumberFormatException ex) {
                    throw new OpenToolException(
                            "HMMER_DOMTBL_FORMAT_ERROR",
                            "Numeric domtblout fields are invalid near line " + lineNumber + ".",
                            "Regenerate the domtblout file with compatible HMMER output."
                    );
                }
            }
        }
        return hits;
    }
}
