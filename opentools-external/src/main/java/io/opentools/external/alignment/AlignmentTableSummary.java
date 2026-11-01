package io.opentools.external.alignment;

import io.opentools.api.tool.ResultTable;

import java.util.List;
import java.util.Map;

public record AlignmentTableSummary(
        long totalHits,
        long queryCount,
        List<BlastHit> bestHits,
        ResultTable resultTable,
        Map<String, Object> metrics
) {
}
