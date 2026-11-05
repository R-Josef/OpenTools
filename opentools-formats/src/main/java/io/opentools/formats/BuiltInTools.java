package io.opentools.formats;

import io.opentools.core.tool.ToolRegistry;
import io.opentools.formats.fasta.FastaExtractTool;
import io.opentools.formats.fasta.FastaMergeTool;
import io.opentools.formats.fasta.FastaSplitTool;
import io.opentools.formats.fasta.FastaStatsTool;
import io.opentools.formats.fasta.CdsTranslateTool;
import io.opentools.formats.bed.BedBlocksTool;
import io.opentools.formats.bed.BedStatsTool;
import io.opentools.formats.bed.BedToGffTool;
import io.opentools.formats.gxf.GxfCheckRepairTool;
import io.opentools.formats.gxf.GxfEditTool;
import io.opentools.formats.gxf.GxfFilterTool;
import io.opentools.formats.gxf.GxfGeneDensityTool;
import io.opentools.formats.gxf.GxfGenomeMatchTool;
import io.opentools.formats.gxf.GxfIdMapTool;
import io.opentools.formats.gxf.GxfInspectTool;
import io.opentools.formats.gxf.GxfMergeSplitTool;
import io.opentools.formats.gxf.GxfOverlapTool;
import io.opentools.formats.gxf.GxfRepresentativeTool;
import io.opentools.formats.gxf.GxfSequenceExtractTool;
import io.opentools.formats.gxf.GxfToBedTool;
import io.opentools.formats.table.TablePreviewTool;

public final class BuiltInTools {
    private BuiltInTools() {
    }

    public static void register(ToolRegistry registry) {
        registry.register(new FastaStatsTool());
        registry.register(new FastaExtractTool());
        registry.register(new FastaMergeTool());
        registry.register(new FastaSplitTool());
        registry.register(new CdsTranslateTool());
        registry.register(new GxfInspectTool());
        registry.register(new GxfGenomeMatchTool());
        registry.register(new GxfSequenceExtractTool());
        registry.register(new GxfEditTool());
        registry.register(new GxfFilterTool());
        registry.register(new GxfToBedTool());
        registry.register(new GxfOverlapTool());
        registry.register(new GxfRepresentativeTool());
        registry.register(new GxfIdMapTool());
        registry.register(new GxfMergeSplitTool());
        registry.register(new GxfCheckRepairTool());
        registry.register(new GxfGeneDensityTool());
        registry.register(new BedStatsTool());
        registry.register(new BedBlocksTool());
        registry.register(new BedToGffTool());
        registry.register(new TablePreviewTool());
    }
}
