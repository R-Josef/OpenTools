package io.opentools.analysis;

import io.opentools.analysis.sequence.FastaFilterTool;
import io.opentools.analysis.sequence.FastaConvertTool;
import io.opentools.analysis.sequence.FastaRenameTool;
import io.opentools.analysis.sequence.FastaSubsequenceTool;
import io.opentools.analysis.sequence.FastaTransformTool;
import io.opentools.analysis.sequence.FastqFilterTool;
import io.opentools.analysis.sequence.FastqStatsTool;
import io.opentools.analysis.sequence.SequenceOrfTool;
import io.opentools.analysis.sequence.SsrMiningTool;
import io.opentools.analysis.expression.ExpressionCorrelationTool;
import io.opentools.analysis.expression.ExpressionDifferentialInputTool;
import io.opentools.analysis.expression.ExpressionNormalizeTool;
import io.opentools.analysis.expression.ExpressionPcaTool;
import io.opentools.analysis.enrichment.GoEnrichTool;
import io.opentools.analysis.enrichment.GoLevelTool;
import io.opentools.analysis.enrichment.KeggEnrichTool;
import io.opentools.analysis.enrichment.ResourceManageTool;
import io.opentools.analysis.phylogeny.NewickTools;
import io.opentools.analysis.hts.HtsTools;
import io.opentools.analysis.table.TableTools;
import io.opentools.core.tool.ToolRegistry;

public final class BuiltInAnalysis {
    private BuiltInAnalysis() {
    }

    public static void register(ToolRegistry registry) {
        registry.register(new FastaFilterTool());
        registry.register(new FastaTransformTool());
        registry.register(new FastaSubsequenceTool());
        registry.register(new FastaRenameTool());
        registry.register(new FastaConvertTool());
        registry.register(new SequenceOrfTool());
        registry.register(new SsrMiningTool());
        registry.register(new FastqStatsTool());
        registry.register(new FastqFilterTool());
        registry.register(new ExpressionNormalizeTool());
        registry.register(new ExpressionCorrelationTool());
        registry.register(new ExpressionPcaTool());
        registry.register(new ExpressionDifferentialInputTool());
        registry.register(new GoEnrichTool());
        registry.register(new GoLevelTool());
        registry.register(new KeggEnrichTool());
        registry.register(new ResourceManageTool());
        registry.register(new NewickTools.SummaryTool());
        registry.register(new NewickTools.SubsetTool());
        HtsTools.tools().forEach(registry::register);
        TableTools.tools().forEach(registry::register);
    }
}
