package io.opentools.formats.bed;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.FormatToolSupport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BedBlocksTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "bed.blocks",
            "BED12 Block Analysis",
            "Parse BED12 blocks, emit block and intron intervals, and validate block coordinates.",
            "Genome annotation",
            List.of("BED12", "BED blocks", "BED12 区块", "BED 外显子"),
            List.of("BED12", "BED12.gz"),
            List.of("TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input BED12",
                            "BED12 with blockCount, blockSizes, and blockStarts columns."),
                    ToolParameter.optionalOutputFile("output", "Output TSV",
                            "Leave blank to save in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output",
                            "Allow replacing an existing output.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        return validateParameters(request)
                .merge(FormatToolSupport.validateOutput(request, request.path("input")));
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path output = FormatToolSupport.outputPath(request, context, "bed-blocks.tsv");
        long[] counters = new long[6];
        try (BedReader reader = new BedReader(request.path("input"))) {
            FormatToolSupport.writeAtomically(output, writer -> {
                writer.write("seq_id\tname\tstrand\trecord_start\trecord_end"
                        + "\tkind\tindex\tstart\tend\tlength\n");
                BedRecord record;
                while ((record = reader.next()) != null) {
                    context.cancellationToken().throwIfCancelled();
                    List<BlockInterval> intervals = parseBlocks(record);
                    counters[0]++;
                    counters[1] += intervals.size() / 2 + (intervals.size() % 2 == 0 ? 0 : 1);
                    for (int index = 0; index < intervals.size(); index++) {
                        BlockInterval interval = intervals.get(index);
                        writer.write(record.seqId() + "\t" + escape(record.name()) + "\t"
                                + record.strand() + "\t" + record.start() + "\t" + record.end()
                                + "\t" + interval.kind() + "\t" + interval.index() + "\t"
                                + interval.start() + "\t" + interval.end() + "\t"
                                + interval.length() + "\n");
                        if ("BLOCK".equals(interval.kind())) {
                            counters[2]++;
                            counters[3] += interval.length();
                        } else {
                            counters[4]++;
                            counters[5] += interval.length();
                        }
                    }
                }
            });
        }
        if (counters[0] == 0) {
            throw new OpenToolException("EMPTY_BED", "No BED12 records were found.", "Check the input format.");
        }
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("records", counters[0]);
        metrics.put("blocks", counters[2]);
        metrics.put("introns", counters[4]);
        metrics.put("blockBases", counters[3]);
        metrics.put("intronBases", counters[5]);
        context.progress(1.0, "BED12 blocks parsed");
        return new ToolResult(
                "Parsed " + counters[2] + " blocks in " + counters[0] + " BED12 records",
                List.of(counters[0], counters[2]),
                counters[4] + " introns; " + counters[3] + " block bases.",
                List.of(counters[4], counters[3]),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("BED12 records", Long.toString(counters[0])),
                        List.of("Blocks", Long.toString(counters[2])),
                        List.of("Introns", Long.toString(counters[4])),
                        List.of("Block bases", Long.toString(counters[3])),
                        List.of("Intron bases", Long.toString(counters[5]))
                )),
                List.of(new OutputArtifact(output, "BED12 block and intron intervals",
                        "text/tab-separated-values")),
                metrics
        );
    }

    private static List<BlockInterval> parseBlocks(BedRecord record) {
        List<String> extra = record.extraFields();
        if (extra.size() < 6) {
            throw formatError(record, "BED12 requires thickStart, thickEnd, itemRgb, blockCount, blockSizes, and blockStarts.");
        }
        int count;
        try {
            count = Integer.parseInt(extra.get(3).trim());
        } catch (NumberFormatException ex) {
            throw formatError(record, "BED12 blockCount must be an integer.");
        }
        if (count <= 0 || count > 10_000) {
            throw formatError(record, "BED12 blockCount must be between 1 and 10000.");
        }
        int[] sizes = parseInts(extra.get(4), record, "blockSizes");
        int[] starts = parseInts(extra.get(5), record, "blockStarts");
        if (sizes.length != count || starts.length != count) {
            throw formatError(record, "BED12 blockCount must match blockSizes and blockStarts.");
        }
        List<BlockInterval> result = new ArrayList<>();
        int previousEnd = -1;
        for (int index = 0; index < count; index++) {
            int size = sizes[index];
            int start = starts[index];
            if (size <= 0 || start < 0) {
                throw formatError(record, "BED12 block sizes and starts must be non-negative, and sizes must be positive.");
            }
            long absoluteStart = record.start() + start;
            long absoluteEnd = absoluteStart + size;
            if (absoluteEnd > record.end() || absoluteStart < record.start()) {
                throw formatError(record, "BED12 block falls outside the record interval.");
            }
            if (start < previousEnd) {
                throw formatError(record, "BED12 blocks must be sorted and non-overlapping.");
            }
            result.add(new BlockInterval("BLOCK", index + 1, absoluteStart, absoluteEnd));
            if (previousEnd >= 0 && start > previousEnd) {
                result.add(new BlockInterval(
                        "INTRON",
                        index,
                        record.start() + previousEnd,
                        record.start() + start
                ));
            }
            previousEnd = start + size;
        }
        return result;
    }

    private static int[] parseInts(String value, BedRecord record, String field) {
        String[] parts = value.split(",", -1);
        List<Integer> parsed = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            try {
                parsed.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ex) {
                throw formatError(record, "BED12 " + field + " must contain integers.");
            }
        }
        return parsed.stream().mapToInt(Integer::intValue).toArray();
    }

    private static OpenToolException formatError(BedRecord record, String message) {
        return new OpenToolException(
                "BED12_FORMAT_ERROR",
                message + " Near line " + record.lineNumber() + ".",
                "Use BED12 with blockCount, comma-separated blockSizes, and relative blockStarts."
        );
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ');
    }

    private record BlockInterval(String kind, int index, long start, long end) {
        long length() {
            return end - start;
        }
    }
}
