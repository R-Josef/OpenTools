package io.opentools.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SangerDisplayAlignmentTest {
    @Test
    void noIndelsKeepsReferenceCoordinatesAndAssemblyOffset() {
        String reference = "ACGTACGTAC";
        SangerDisplayAlignment display = SangerDisplayAlignment.build(
                reference, reference, reference, List.of(), 1,
                "TT" + reference + "GG", "", 0);

        assertTrue(display.assemblyMapped());
        assertEquals(10, display.coreColumns());
        assertEquals(2, display.prefixColumns());
        assertEquals(14, display.columnCount());
        assertEquals(2, display.columnForReference(0));
        assertEquals(11, display.columnForReference(9));
        assertEquals(2, display.columnForConsensus(0));
        assertEquals(11, display.columnForConsensus(9));
        assertEquals(2, display.columnForAssembly(2));
        assertEquals(11, display.columnForAssembly(11));
        assertEquals(2, display.assemblyIndexForReference(0));
        assertEquals(2, display.assemblyBoundaryColumn(2));
        assertEquals(2, display.referenceSpanStart(0));
        assertEquals(12, display.referenceSpanEndExclusive(10));
    }

    @Test
    void insertionAddsColumnAndRealignsDownstreamBases() {
        String reference = "ACGTACGT";
        String expanded = "ACTGTACGT";
        SangerDisplayAlignment display = SangerDisplayAlignment.build(
                reference, reference, expanded,
                List.of(new SangerDisplayAlignment.Indel(3, "INSERTION", "T", 1, 1)),
                1, reference, "", 0);

        assertEquals(9, display.coreColumns());
        assertEquals(expanded, display.consensus());
        assertEquals(0, display.columnForReference(0));
        assertEquals(1, display.columnForReference(1));
        assertEquals(3, display.columnForReference(2));
        assertEquals(8, display.columnForReference(7));
        assertEquals(2, display.columnForConsensus(2));
        assertEquals(3, display.columnForConsensus(3));
        assertEquals(3, display.columnForAssembly(2));
        assertEquals(2, display.assemblyIndexForReference(2));
        assertEquals(1, display.assemblyBoundaryColumn(1));
        assertEquals(3, display.assemblyBoundaryColumn(2));
        assertEquals(0, display.referenceSpanStart(0));
        assertEquals(6, display.referenceSpanEndExclusive(5));
        int insertionX = display.referenceColumnX(display.columnForConsensus(2), 0, 7);
        assertEquals(2, display.referenceAtX(insertionX, 0, 7));
    }

    @Test
    void deletionLeavesReferenceColumnWithoutConsensusBase() {
        String reference = "ACGTACGT";
        String expanded = "ACACGT";
        SangerDisplayAlignment display = SangerDisplayAlignment.build(
                reference, reference, expanded,
                List.of(new SangerDisplayAlignment.Indel(3, "DELETION", "-", 2, 1)),
                1, reference, "", 0);

        assertEquals(8, display.coreColumns());
        assertEquals(2, display.columnForReference(2));
        assertEquals(3, display.columnForReference(3));
        assertEquals(4, display.columnForReference(4));
        assertEquals(4, display.columnForConsensus(2));
        assertEquals(-1, display.column(2).consensusIndex());
        assertEquals(-1, display.column(3).consensusIndex());
        assertEquals(2, display.columnForAssembly(2));
        assertEquals(4, display.columnForAssembly(4));
    }

    @Test
    void containsExpandedConsensusMapsInsertionToAssembly() {
        String reference = "ACGT";
        String expanded = "ACTGT";
        SangerDisplayAlignment display = SangerDisplayAlignment.build(
                reference, reference, expanded,
                List.of(new SangerDisplayAlignment.Indel(3, "INSERTION", "T", 1, 1)),
                1, "GG" + expanded + "CC", "", 0);

        assertEquals(2, display.prefixColumns());
        assertEquals(9, display.columnCount());
        assertEquals(4, display.columnForConsensus(2));
        assertEquals(5, display.columnForReference(2));
        assertEquals(2, display.columnForAssembly(2));
        assertEquals(4, display.columnForAssembly(4));
        assertEquals(6, display.columnForAssembly(6));
        assertEquals(7, display.columnForAssembly(7));
    }

    @Test
    void mismatchedExpandedSequenceFallsBackToCorrectedConsensus() {
        String reference = "ACGT";
        String corrected = "ACGT";
        SangerDisplayAlignment display = SangerDisplayAlignment.build(
                reference, corrected, "ACGGT", List.of(), 1,
                reference, "", 0);

        assertEquals(corrected, display.consensus());
        assertEquals(4, display.coreColumns());
        assertEquals(4, display.columnCount());
        assertFalse(display.column(0).insertion());
    }

    @Test
    void minimumSupportFiltersIndelAndFallsBackToCorrected() {
        String reference = "ACGT";
        String expanded = "ACTGT";
        SangerDisplayAlignment display = SangerDisplayAlignment.build(
                reference, reference, expanded,
                List.of(new SangerDisplayAlignment.Indel(3, "INSERTION", "T", 1, 1)),
                2, reference, "", 0);

        assertEquals(reference, display.consensus());
        assertEquals(4, display.coreColumns());
    }

    @Test
    void unmappedAssemblyKeepsLegacyFallbackOffset() {
        String reference = "ACGTACGT";
        SangerDisplayAlignment display = SangerDisplayAlignment.build(
                reference, reference, reference, List.of(), 1,
                "TTTT", "", 3);

        assertFalse(display.assemblyMapped());
        assertEquals(29, display.referenceLeft(8, 7));
        assertEquals(29, display.referenceX(0, 8, 7));
        assertEquals(29, display.referenceColumnX(0, 8, 7));
    }
}
