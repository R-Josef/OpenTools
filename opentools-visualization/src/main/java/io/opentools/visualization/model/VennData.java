package io.opentools.visualization.model;

import java.util.List;

public record VennData(
        List<String> setNames,
        List<Integer> setSizes,
        List<Intersection> intersections,
        String displayMode,
        int maximumIntersections
) implements PlotData {
    public VennData {
        setNames = setNames == null ? List.of() : List.copyOf(setNames);
        setSizes = setSizes == null ? List.of() : List.copyOf(setSizes);
        intersections = intersections == null ? List.of() : List.copyOf(intersections);
        if (setNames.size() != setSizes.size()) {
            throw new IllegalArgumentException("Set names and set sizes must have equal length");
        }
        if (setNames.size() < 2) {
            throw new IllegalArgumentException("At least two sets are required");
        }
        displayMode = displayMode == null || displayMode.isBlank() ? "UPSET" : displayMode;
        if (maximumIntersections < 1) {
            throw new IllegalArgumentException("maximumIntersections must be positive");
        }
    }

    public record Intersection(List<String> sets, int count, List<String> examples) {
        public Intersection {
            sets = sets == null ? List.of() : List.copyOf(sets);
            examples = examples == null ? List.of() : List.copyOf(examples);
            if (sets.isEmpty()) {
                throw new IllegalArgumentException("An intersection must contain at least one set");
            }
            if (count < 0) {
                throw new IllegalArgumentException("Intersection count cannot be negative");
            }
        }
    }
}
