package io.opentools.visualization.render;

import io.opentools.visualization.model.GeneStructureData;
import io.opentools.visualization.model.PlotSpec;
import io.opentools.visualization.scene.PlotScene;
import io.opentools.visualization.scene.PlotSceneBuilder;
import io.opentools.visualization.scene.ScenePoint;
import io.opentools.visualization.scene.SceneText;

import java.util.ArrayList;
import java.util.List;

public final class GeneStructureRenderer {
    private GeneStructureRenderer() {
    }

    public static PlotScene render(PlotSpec spec, GeneStructureData data) {
        int width = Math.max(760, spec.export().width());
        double fontSize = spec.style().baseFontSize();
        double left = 245;
        double right = 36;
        double top = spec.title().isBlank() ? 36 : 84;
        double locusGap = 26;
        List<Double> locusHeights = new ArrayList<>();
        double contentHeight = top;
        for (GeneStructureData.GeneLocus locus : data.loci()) {
            int tracks = Math.max(1, locus.transcripts().size());
            double height = 76 + tracks * 34;
            locusHeights.add(height);
            contentHeight += height + locusGap;
        }
        int height = (int) Math.ceil(Math.max(spec.export().height(), contentHeight + 20));
        PlotSceneBuilder scene = new PlotSceneBuilder(width, height, spec.style().backgroundColor());
        title(scene, spec, fontSize);
        double plotRight = width - right;
        double plotWidth = plotRight - left;
        double y = top;
        for (int locusIndex = 0; locusIndex < data.loci().size(); locusIndex++) {
            GeneStructureData.GeneLocus locus = data.loci().get(locusIndex);
            double locusHeight = locusHeights.get(locusIndex);
            double centerY = y + locusHeight / 2;
            scene.rect(
                    14,
                    y,
                    width - 28,
                    locusHeight,
                    "#FAFBFC",
                    "#DCE2E6",
                    0.8
            );
            List<String> titleLines = TextMetrics.wrap(
                    locus.id() + "  " + locus.strand(),
                    205,
                    fontSize + 1
            );
            for (int line = 0; line < titleLines.size(); line++) {
                scene.text(
                        left - 20,
                        y + 22 + fontSize + line * (fontSize + 2),
                        titleLines.get(line),
                        spec.style().foregroundColor(),
                        fontSize + 1,
                        true,
                        SceneText.TextAnchor.RIGHT,
                        0,
                        locus.id()
                );
            }
            scene.text(
                    left - 20,
                    y + 22 + (titleLines.size() + 1) * (fontSize + 2),
                    locus.sequenceId() + ":" + locus.start() + "-" + locus.end(),
                    spec.style().mutedColor(),
                    fontSize - 2,
                    false,
                    SceneText.TextAnchor.RIGHT,
                    0,
                    ""
            );

            List<GeneStructureData.Transcript> transcripts = locus.transcripts();
            if (transcripts.isEmpty()) {
                transcripts = List.of(new GeneStructureData.Transcript(
                        locus.id(),
                        locus.start(),
                        locus.end(),
                        locus.strand(),
                        List.of()
                ));
            }
            for (int track = 0; track < transcripts.size(); track++) {
                GeneStructureData.Transcript transcript = transcripts.get(track);
                double baselineY = y + 78 + track * 34;
                double startX = scale(locus.start(), locus.end(), transcript.start(), left, plotRight);
                double endX = scale(locus.start(), locus.end(), transcript.end(), left, plotRight);
                String trackId = transcript.id();
                scene.text(
                        left - 20,
                        baselineY + 4,
                        trackId,
                        spec.style().mutedColor(),
                        fontSize - 2,
                        false,
                        SceneText.TextAnchor.RIGHT,
                        0,
                        trackId
                );
                scene.line(startX, baselineY, endX, baselineY, spec.style().foregroundColor(), 1.8);
                double arrowX = transcript.strand() == '-' ? startX : endX;
                double direction = transcript.strand() == '-' ? 1 : -1;
                scene.polygon(
                        List.of(
                                new ScenePoint(arrowX, baselineY),
                                new ScenePoint(arrowX + direction * 10, baselineY - 6),
                                new ScenePoint(arrowX + direction * 10, baselineY + 6)
                        ),
                        spec.style().foregroundColor(),
                        "",
                        0,
                        trackId + " " + transcript.strand()
                );
                for (GeneStructureData.Feature feature : transcript.features()) {
                    double featureStart = scale(locus.start(), locus.end(), feature.start(), left, plotRight);
                    double featureEnd = scale(locus.start(), locus.end(), feature.end() + 1, left, plotRight);
                    double boxHeight = switch (feature.type().toLowerCase(java.util.Locale.ROOT)) {
                        case "cds" -> 18;
                        case "exon" -> 15;
                        default -> 11;
                    };
                    String fill = featureColor(feature.type(), spec);
                    scene.rect(
                            featureStart,
                            baselineY - boxHeight / 2,
                            Math.max(2, featureEnd - featureStart),
                            boxHeight,
                            fill,
                            PlotColors.contrast(fill),
                            0.6,
                            1,
                            locus.id() + " | " + trackId + " | " + feature.type()
                                    + " " + feature.start() + "-" + feature.end()
                    );
                }
            }
            scene.text(
                    left,
                    y + locusHeight - 10,
                    Long.toString(locus.start()),
                    spec.style().mutedColor(),
                    fontSize - 3,
                    false,
                    SceneText.TextAnchor.LEFT,
                    0,
                    ""
            );
            scene.text(
                    plotRight,
                    y + locusHeight - 10,
                    Long.toString(locus.end()),
                    spec.style().mutedColor(),
                    fontSize - 3,
                    false,
                    SceneText.TextAnchor.RIGHT,
                    0,
                    ""
            );
            y += locusHeight + locusGap;
        }
        return scene.build();
    }

    private static void title(PlotSceneBuilder scene, PlotSpec spec, double fontSize) {
        if (!spec.title().isBlank()) {
            scene.text(
                    24,
                    24 + fontSize + 7,
                    spec.title(),
                    spec.style().foregroundColor(),
                    fontSize + 7,
                    true,
                    SceneText.TextAnchor.LEFT,
                    0,
                    ""
            );
        }
        if (!spec.subtitle().isBlank()) {
            scene.text(
                    24,
                    24 + fontSize + 29,
                    spec.subtitle(),
                    spec.style().mutedColor(),
                    fontSize - 1
            );
        }
    }

    private static String featureColor(String type, PlotSpec spec) {
        return switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "cds" -> spec.style().accentColor();
            case "exon" -> spec.style().palette().get(
                    Math.min(spec.style().palette().size() - 1, spec.style().palette().size() / 2)
            );
            case "mrna", "transcript" -> spec.style().mutedColor();
            default -> spec.style().palette().getLast();
        };
    }

    private static double scale(long locusStart, long locusEnd, long value, double left, double right) {
        if (locusEnd == locusStart) {
            return left;
        }
        double fraction = (value - locusStart) / (double) (locusEnd - locusStart);
        return left + Math.max(0, Math.min(1, fraction)) * (right - left);
    }
}
