package io.opentools.cli;

import io.opentools.analysis.BuiltInAnalysis;
import io.opentools.api.tool.ToolParameter;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.formats.BuiltInTools;
import io.opentools.external.ExternalTools;
import io.opentools.visualization.BuiltInVisualizations;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LanguageFilesTest {
    @Test
    void englishAndChineseLanguageKeySetsStayIdentical() throws Exception {
        Path languageDirectory = locateLanguageDirectory();
        Properties english = load(languageDirectory.resolve("en_US.properties"));
        Properties chinese = load(languageDirectory.resolve("zh_CN.properties"));

        assertEquals(
                new TreeSet<>(english.stringPropertyNames()),
                new TreeSet<>(chinese.stringPropertyNames())
        );
        assertFalse(english.stringPropertyNames().stream()
                .filter(key -> key.startsWith("tool.visual."))
                .toList()
                .isEmpty());
    }

    @Test
    void visualizationTasksAndUiKeysExistInBothLanguages() throws Exception {
        Path languageDirectory = locateLanguageDirectory();
        Properties english = load(languageDirectory.resolve("en_US.properties"));
        Properties chinese = load(languageDirectory.resolve("zh_CN.properties"));
        ToolRegistry registry = new ToolRegistry();
        BuiltInVisualizations.register(registry);

        for (var tool : registry.list()) {
            assertKey(english, chinese, "category." + tool.category().toLowerCase(java.util.Locale.ROOT));
            assertKey(english, chinese, "tool." + tool.id() + ".name");
            assertKey(english, chinese, "tool." + tool.id() + ".description");
            for (ToolParameter parameter : tool.parameters()) {
                assertKey(english, chinese, "tool." + tool.id() + ".param." + parameter.id() + ".title");
                assertKey(english, chinese, "tool." + tool.id() + ".param." + parameter.id() + ".description");
            }
        }
        for (String key : java.util.List.of(
                "result.visual.heatmap.summary",
                "result.visual.venn.summary",
                "result.visual.gene.summary",
                "result.plot.pca.summary",
                "result.plot.correlation.summary",
                "result.plot.volcano.summary",
                "result.plot.box.summary",
                "result.plot.violin.summary",
                "result.plot.bar.summary",
                "result.plot.enrichment_bar.summary",
                "result.plot.enrichment_bubble.summary",
                "result.tree.render.summary",
                "result.plot.collinearity.summary",
                "result.plot.dotplot.summary",
                "result.plot.seqlogo.summary",
                "result.plot.gene_location.summary",
                "result.plot.domain.summary",
                "result.plot.motif.summary",
                "result.plot.circos.summary",
                "result.plot.efp.summary",
                "results.tab.data",
                "results.tab.plot",
                "plot.zoom_in",
                "plot.zoom_out",
                "plot.export",
                "plugins.enable",
                "plugins.disable",
                "plugins.column.api",
                "plugins.column.dependencies",
                "category.plugin"
        )) {
            assertKey(english, chinese, key);
        }
    }

    @Test
    void hostLanguageFilesDoNotContainPluginPrivateKeys() throws Exception {
        Path languageDirectory = locateLanguageDirectory();
        for (String language : java.util.List.of("en_US", "zh_CN")) {
            Properties properties = load(languageDirectory.resolve(language + ".properties"));
            assertTrue(properties.stringPropertyNames().stream()
                    .noneMatch(key -> key.startsWith("tool.plugin.")
                            || key.startsWith("result.plugin.")
                            || key.startsWith("page.plugin.")));
        }
    }

    @Test
    void analysisTasksAndResultKeysExistInBothLanguages() throws Exception {
        Path languageDirectory = locateLanguageDirectory();
        Properties english = load(languageDirectory.resolve("en_US.properties"));
        Properties chinese = load(languageDirectory.resolve("zh_CN.properties"));
        ToolRegistry registry = new ToolRegistry();
        BuiltInAnalysis.register(registry);

        for (var tool : registry.list()) {
            assertKey(english, chinese, "category." + tool.category().toLowerCase(java.util.Locale.ROOT));
            assertKey(english, chinese, "tool." + tool.id() + ".name");
            assertKey(english, chinese, "tool." + tool.id() + ".description");
            for (ToolParameter parameter : tool.parameters()) {
                assertKey(english, chinese, "tool." + tool.id() + ".param." + parameter.id() + ".title");
                assertKey(english, chinese, "tool." + tool.id() + ".param." + parameter.id() + ".description");
            }
        }
        for (String key : java.util.List.of(
                "result.fasta.filter.summary",
                "result.fasta.filter.detail",
                "result.fasta.transform.summary",
                "result.fasta.transform.detail",
                "result.fasta.subsequence.summary",
                "result.fasta.subsequence.detail",
                "result.fasta.rename.summary",
                "result.fasta.convert.summary",
                "result.sequence.orf.summary",
                "result.fastq.stats.summary",
                "result.fastq.filter.summary",
                "result.expression.normalize.summary",
                "result.expression.correlation.summary",
                "result.expression.pca.summary",
                "result.expression.differential.summary"
                ,"result.go.enrich.summary"
                ,"result.go.level.summary"
                ,"result.kegg.enrich.summary"
                ,"result.tree.summary.summary"
                ,"result.tree.subset.summary"
                ,"result.hts.sam.summary"
                ,"result.hts.bam_subset.summary"
                ,"result.hts.bam_coverage.summary"
                ,"result.hts.vcf.summary"
                ,"result.hts.vcf_filter.summary"
                ,"result.bed.coverage.summary"
                ,"result.table_filter.summary"
                ,"result.table_select_columns.summary"
                ,"result.table_join.summary"
                ,"result.table_transpose.summary"
                ,"result.table_deduplicate.summary"
                ,"result.table_pivot.summary"
                ,"result.table_replace.summary"
        )) {
            assertKey(english, chinese, key);
        }
    }

    @Test
    void formatTasksAndF2ResultKeysExistInBothLanguages() throws Exception {
        Path languageDirectory = locateLanguageDirectory();
        Properties english = load(languageDirectory.resolve("en_US.properties"));
        Properties chinese = load(languageDirectory.resolve("zh_CN.properties"));
        ToolRegistry registry = new ToolRegistry();
        BuiltInTools.register(registry);

        for (var tool : registry.list()) {
            assertKey(english, chinese, "category." + tool.category().toLowerCase(java.util.Locale.ROOT));
            assertKey(english, chinese, "tool." + tool.id() + ".name");
            assertKey(english, chinese, "tool." + tool.id() + ".description");
            for (ToolParameter parameter : tool.parameters()) {
                assertKey(english, chinese, "tool." + tool.id() + ".param." + parameter.id() + ".title");
                assertKey(english, chinese, "tool." + tool.id() + ".param." + parameter.id() + ".description");
            }
        }
        for (String key : java.util.List.of(
                "result.gxf.filter.summary",
                "result.gxf.to_bed.summary",
                "result.gxf.overlap.summary",
                "result.gxf.representative.summary",
                "result.gxf.id_map.summary",
                "result.gxf.merge.summary",
                "result.gxf.split.summary",
                "result.gxf.check_repair.summary",
                "result.gxf.gene_density.summary",
                "result.bed.stats.summary",
                "result.bed.to_gff.summary"
        )) {
            assertKey(english, chinese, key);
        }
    }

    @Test
    void externalTasksAndF3ResultKeysExistInBothLanguages() throws Exception {
        Path languageDirectory = locateLanguageDirectory();
        Properties english = load(languageDirectory.resolve("en_US.properties"));
        Properties chinese = load(languageDirectory.resolve("zh_CN.properties"));
        ToolRegistry registry = new ToolRegistry();
        ExternalTools.register(registry);

        for (var tool : registry.list()) {
            assertKey(english, chinese, "category." + tool.category().toLowerCase(java.util.Locale.ROOT));
            assertKey(english, chinese, "tool." + tool.id() + ".name");
            assertKey(english, chinese, "tool." + tool.id() + ".description");
            for (ToolParameter parameter : tool.parameters()) {
                assertKey(english, chinese, "tool." + tool.id() + ".param." + parameter.id() + ".title");
                assertKey(english, chinese, "tool." + tool.id() + ".param." + parameter.id() + ".description");
            }
        }
        for (String key : java.util.List.of(
                "result.blast.xml.summary",
                "result.blast.reciprocal.summary",
                "result.blast.fastq.summary",
                "result.blast.regions.summary",
                "result.hmmer.summary",
                "result.pfam.import.summary",
                "result.eggnog.import.summary",
                "result.muscle.summary",
                "result.trimal.summary"
                ,"result.iqtree.summary"
                ,"result.mcscan.input.summary"
                ,"result.mcscan.run.summary"
                ,"result.mcscan.import.summary"
                ,"result.kaks.summary"
        )) {
            assertKey(english, chinese, key);
        }
    }

    private static Properties load(Path path) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Path locateLanguageDirectory() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path candidate = current.resolve("lang");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        candidate = current.getParent().resolve("lang");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        throw new IllegalStateException("Could not locate external lang/ directory");
    }

    private static void assertKey(Properties english, Properties chinese, String key) {
        assertTrue(english.containsKey(key), "Missing English key: " + key);
        assertTrue(chinese.containsKey(key), "Missing Chinese key: " + key);
    }
}
