package io.opentools.external.alignment;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AlignmentSummaryWriter {
    private AlignmentSummaryWriter() {
    }

    public static void write(Path output, AlignmentTableSummary summary) throws Exception {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("query\thits\tbest_subject\tpercent_identity\talignment_length\tevalue\tbitscore");
            writer.newLine();
            for (BlastHit hit : summary.bestHits()) {
                writer.write(hit.query());
                writer.write('\t');
                writer.write(Long.toString(summary.resultTable().rows().stream()
                        .filter(row -> row.getFirst().equals(hit.query()))
                        .map(row -> Long.parseLong(row.get(1)))
                        .findFirst().orElse(0L)));
                writer.write('\t');
                writer.write(hit.subject());
                writer.write('\t');
                writer.write(Double.toString(hit.percentIdentity()));
                writer.write('\t');
                writer.write(Long.toString(hit.alignmentLength()));
                writer.write('\t');
                writer.write(Double.toString(hit.evalue()));
                writer.write('\t');
                writer.write(Double.toString(hit.bitscore()));
                writer.newLine();
            }
        }
    }
}
