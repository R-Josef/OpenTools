package io.opentools.visualization.model;

import java.util.List;

public record GeneStructureData(List<GeneLocus> loci) implements PlotData {
    public GeneStructureData {
        loci = loci == null ? List.of() : List.copyOf(loci);
        if (loci.isEmpty()) {
            throw new IllegalArgumentException("At least one gene locus is required");
        }
    }

    public record GeneLocus(
            String id,
            String sequenceId,
            long start,
            long end,
            char strand,
            List<Transcript> transcripts
    ) {
        public GeneLocus {
            id = requireText(id, "Gene locus id");
            sequenceId = sequenceId == null ? "" : sequenceId;
            if (start < 1 || end < start) {
                throw new IllegalArgumentException("Gene locus coordinates are invalid: " + start + ".." + end);
            }
            transcripts = transcripts == null ? List.of() : List.copyOf(transcripts);
        }
    }

    public record Transcript(
            String id,
            long start,
            long end,
            char strand,
            List<Feature> features
    ) {
        public Transcript {
            id = requireText(id, "Transcript id");
            if (start < 1 || end < start) {
                throw new IllegalArgumentException("Transcript coordinates are invalid: " + start + ".." + end);
            }
            features = features == null ? List.of() : List.copyOf(features);
        }
    }

    public record Feature(String type, long start, long end, char strand) {
        public Feature {
            type = requireText(type, "Feature type");
            if (start < 1 || end < start) {
                throw new IllegalArgumentException("Feature coordinates are invalid: " + start + ".." + end);
            }
        }
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return value;
    }
}
