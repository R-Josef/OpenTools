package io.opentools.visualization.model;

import java.util.List;

public record GenericPlotData(
        String chart,
        String xLabel,
        String yLabel,
        List<String> categories,
        List<GenericSeries> series
) implements PlotData {
    public GenericPlotData {
        chart = chart == null || chart.isBlank() ? "SCATTER" : chart.toUpperCase(java.util.Locale.ROOT);
        xLabel = xLabel == null ? "" : xLabel;
        yLabel = yLabel == null ? "" : yLabel;
        categories = categories == null ? List.of() : List.copyOf(categories);
        series = series == null ? List.of() : List.copyOf(series);
    }

    public record GenericSeries(String name, List<Double> x, List<Double> y, List<Double> sizes) {
        public GenericSeries {
            name = name == null || name.isBlank() ? "series" : name;
            x = x == null ? List.of() : List.copyOf(x);
            y = y == null ? List.of() : List.copyOf(y);
            sizes = sizes == null || sizes.isEmpty()
                    ? java.util.Collections.nCopies(x.size(), 1.0)
                    : List.copyOf(sizes);
            if (x.size() != y.size()) {
                throw new IllegalArgumentException("Generic plot x/y series lengths differ");
            }
            if (sizes.size() != x.size()) {
                throw new IllegalArgumentException("Generic plot size series length differs");
            }
        }

        public GenericSeries(String name, List<Double> x, List<Double> y) {
            this(name, x, y, List.of());
        }

        public static final class Builder {
            private final String name;
            private final List<Double> x = new java.util.ArrayList<>();
            private final List<Double> y = new java.util.ArrayList<>();
            private final List<Double> sizes = new java.util.ArrayList<>();

            public Builder(String name) {
                this.name = name;
            }

            public Builder add(double xValue, double yValue) {
                return add(xValue, yValue, 1.0);
            }

            public Builder add(double xValue, double yValue, double size) {
                x.add(xValue);
                y.add(yValue);
                sizes.add(size);
                return this;
            }

            public GenericSeries build() {
                return new GenericSeries(name, x, y, sizes);
            }
        }
    }
}
