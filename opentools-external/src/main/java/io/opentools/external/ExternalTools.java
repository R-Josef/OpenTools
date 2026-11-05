package io.opentools.external;

import io.opentools.core.tool.ToolRegistry;
import io.opentools.external.alignment.BlastLocalTool;
import io.opentools.external.alignment.RemoteBlastTool;
import io.opentools.external.alignment.BlastFastqTool;
import io.opentools.external.alignment.BlastReciprocalTool;
import io.opentools.external.alignment.BlastRegionsTool;
import io.opentools.external.alignment.BlastAlignmentTool;
import io.opentools.external.alignment.BlastXmlToTableTool;
import io.opentools.external.alignment.DiamondTool;
import io.opentools.external.alignment.MuscleTool;
import io.opentools.external.alignment.TrimalTool;
import io.opentools.external.alignment.IqTreeTool;
import io.opentools.external.comparative.KaksCalculateTool;
import io.opentools.external.comparative.McscanImportTool;
import io.opentools.external.comparative.McscanInputTool;
import io.opentools.external.comparative.McscanRunTool;
import io.opentools.external.annotation.EggnogImportTool;
import io.opentools.external.annotation.HmmerSearchTool;
import io.opentools.external.annotation.HmmsearchTool;
import io.opentools.external.annotation.MastRunTool;
import io.opentools.external.annotation.MemeResultTool;
import io.opentools.external.annotation.MemeRunTool;
import io.opentools.external.annotation.PfamImportTool;
import io.opentools.external.tool.ExecutableLocator;
import io.opentools.external.tool.ExternalToolSpecs;
import io.opentools.external.tool.ToolStatus;

import java.util.List;

public final class ExternalTools {
    private ExternalTools() {
    }

    public static void register(ToolRegistry registry) {
        registry.register(new BlastLocalTool());
        registry.register(new RemoteBlastTool());
        registry.register(new DiamondTool());
        registry.register(new BlastXmlToTableTool());
        registry.register(new BlastAlignmentTool());
        registry.register(new BlastReciprocalTool());
        registry.register(new BlastFastqTool());
        registry.register(new BlastRegionsTool());
        registry.register(new HmmerSearchTool());
        registry.register(new HmmsearchTool());
        registry.register(new MemeRunTool());
        registry.register(new MemeResultTool());
        registry.register(new MastRunTool());
        registry.register(new PfamImportTool());
        registry.register(new EggnogImportTool());
        registry.register(new MuscleTool());
        registry.register(new TrimalTool());
        registry.register(new IqTreeTool());
        registry.register(new McscanInputTool());
        registry.register(new McscanRunTool());
        registry.register(new McscanImportTool());
        registry.register(new KaksCalculateTool());
    }

    public static List<ToolStatus> diagnostics() {
        return new ExecutableLocator(ExternalRuntime.appHome()).diagnose(List.of(
                ExternalToolSpecs.MAKEBLASTDB,
                ExternalToolSpecs.blast("blastn"),
                ExternalToolSpecs.blast("blastp"),
                ExternalToolSpecs.DIAMOND,
                ExternalToolSpecs.HMMSCAN,
                ExternalToolSpecs.HMMSEARCH,
                ExternalToolSpecs.MEME,
                ExternalToolSpecs.MAST,
                ExternalToolSpecs.MUSCLE,
                ExternalToolSpecs.TRIMAL,
                ExternalToolSpecs.IQTREE,
                ExternalToolSpecs.MCSCANX
        ));
    }
}
