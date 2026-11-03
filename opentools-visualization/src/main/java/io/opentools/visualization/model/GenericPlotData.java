package io.opentools.visualization.model;

import java.util.List;

/**
 * Generic chart data.
 *
 * <p>{@code xMax} and {@code yMax} optionally pin the upper axis bounds; they
 * are used by segment plots such as BLAST dot plots, where the axis should span
 * the complete query or subject rather than only the observed hit coordinates.
 * Both values are {@code null} for ordinary charts.</p>
 */
public record GenericPlotData(
        String chart,
        String xLabel,
        String yLabel,
        List<String> categories,
        List<GenericSeries> series,
        Double xMax,
        Double yMax
) implements PlotData {
    public GenericPlotData {
        chart = chart == null || chart.isBlank() ? "SCATTER" : chart.toUpperCase(java.util.Locale.ROOT);
        xLabel = xLabel == null ? "" : xLabel;
        yLabel = yLabel == null ? "" : yLabel;
        categories = categories == null ? List.of() : List.copyOf(categories);
        series = series == null ? List.of() : List.copyOf(series);
        xMax = xMax != null && xMax > 0 && Double.isFinite(xMax) ? xMax : null;
        yMax = yMax != null && yMax > 0 && Double.isFinite(yMax) ? yMax : null;
    }

    /**
     * Creates data without explicit axis bounds.
     *
     * @param chart chart type
     * @param xLabel x-axis label
     * @param yLabel y-axis label
     * @param categories category labels
     * @param series data series
     */
    public GenericPlotData(
            String chart,
            String xLabel,
            String yLabel,
            List<String> categories,
            List<GenericSeries> series
    ) {
        this(chart, xLabel, yLabel, categories, series, null, null);
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
