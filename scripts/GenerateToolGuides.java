import io.opentools.analysis.BuiltInAnalysis;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.core.i18n.LanguageManager;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.external.ExternalTools;
import io.opentools.formats.BuiltInTools;
import io.opentools.plugin.host.ToolTextResolver;
import io.opentools.visualization.BuiltInVisualizations;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generates the checked-in beginner guides for built-in tools.
 *
 * <p>Run after packaging the application JAR:</p>
 *
 * <pre>
 * javac --release 21 -cp opentools-app/target/opentools.jar \
 *   -d target/tool-guide-generator scripts/GenerateToolGuides.java
 * java -cp "target/tool-guide-generator;opentools-app/target/opentools.jar" \
 *   GenerateToolGuides
 * </pre>
 */
public final class GenerateToolGuides {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"))
            .toAbsolutePath().normalize();

    private static final Map<String, String> RESULT_ZH = resultZh();
    private static final Map<String, String> RESULT_EN = resultEn();

    private GenerateToolGuides() {
    }

    public static void main(String[] args) throws Exception {
        ToolRegistry registry = new ToolRegistry();
        BuiltInTools.register(registry);
        BuiltInAnalysis.register(registry);
        ExternalTools.register(registry);
        BuiltInVisualizations.register(registry);

        Map<String, ToolDescriptor> descriptors = registry.list().stream()
                .collect(Collectors.toMap(ToolDescriptor::id, Function.identity()));
        for (String id : RESULT_ZH.keySet()) {
            if (!descriptors.containsKey(id)) {
                throw new IllegalStateException("Guide profile references unknown tool: " + id);
            }
        }
        // Newly added tools get a generic reading guide until a tailored profile is written.

        generateLanguage("zh_CN", registry, descriptors, true);
        generateLanguage("en_US", registry, descriptors, false);
    }

    private static void generateLanguage(
            String languageCode,
            ToolRegistry registry,
            Map<String, ToolDescriptor> descriptors,
            boolean chinese
    ) throws Exception {
        LanguageManager language = new LanguageManager(ROOT, languageCode);
        ToolTextResolver text = ToolTextResolver.hostOnly(language);
        Path output = ROOT.resolve("docs").resolve(languageCode).resolve("tool");
        Files.createDirectories(output);
        for (ToolDescriptor tool : registry.list()) {
            String markdown = render(tool, descriptors, text, chinese);
            Files.writeString(
                    output.resolve(tool.id() + ".md"),
                    markdown,
                    StandardCharsets.UTF_8
            );
        }
    }

    private static String render(
            ToolDescriptor tool,
            Map<String, ToolDescriptor> descriptors,
            ToolTextResolver text,
            boolean chinese
    ) {
        String name = text.name(tool);
        String description = text.description(tool).trim();
        String result = (chinese ? RESULT_ZH : RESULT_EN).getOrDefault(
                tool.id(),
                chinese
                        ? "结果取决于所选输入和参数；检查输出文件、摘要指标和诊断信息是否与实验设计一致。"
                        : "Results depend on the selected inputs and parameters; review output files, summary metrics, and diagnostics against the experimental design."
        );
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(name).append("\n\n");
        markdown.append('`').append(tool.id()).append("` · ")
                .append(text.category(tool.category())).append(" · v")
                .append(tool.version()).append("\n\n");

        markdown.append(chinese ? "## 一句话说明\n\n" : "## What this tool does\n\n");
        markdown.append(description).append("\n\n");
        markdown.append(chinese
                ? "它完成一个明确、可复现的分析步骤。运行记录会保存规范化参数、输入文件哈希和日志；正式发表前仍应检查原始数据、参数和生物学解释。\n\n"
                : "It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.\n\n");

        markdown.append(chinese ? "## 什么时候使用\n\n" : "## When to use it\n\n");
        for (String point : useCases(tool, chinese)) {
            markdown.append("- ").append(point).append('\n');
        }
        markdown.append('\n');

        markdown.append(chinese ? "## 输入准备\n\n" : "## Prepare the input\n\n");
        markdown.append("- ").append(chinese ? "声明格式：" : "Declared formats: ")
                .append(formats(tool.inputFormats(), chinese)).append('\n');
        List<ToolParameter> required = tool.parameters().stream()
                .filter(ToolParameter::required).toList();
        if (required.isEmpty()) {
            markdown.append("- ").append(chinese
                    ? "该工具没有必填参数；仍应确认数据集、路径和运行环境。"
                    : "No parameter is marked required; still confirm the dataset, paths, and runtime environment.").append('\n');
        } else {
            markdown.append("- ").append(chinese ? "必须提供：" : "Required parameters: ")
                    .append(required.stream()
                            .map(parameter -> "`" + parameter.id() + "`")
                            .collect(Collectors.joining(", ")))
                    .append('\n');
        }
        markdown.append("- ").append(inputPreparation(tool, chinese)).append('\n');
        markdown.append('\n');

        String sample = sampleInput(tool, chinese);
        if (!sample.isBlank()) {
            markdown.append(chinese ? "### 格式示意\n\n" : "### Input sketch\n\n");
            markdown.append(sample).append("\n\n");
        }

        markdown.append(chinese ? "## 推荐运行步骤\n\n" : "## Recommended workflow\n\n");
        markdown.append(chinese
                ? "1. 先确认文件格式、序列名/列名和坐标约定一致。\n"
                + "2. 使用默认值或下表中的推荐值运行校验，先排除路径、表头和类型错误。\n"
                + "3. 提交任务后同时检查摘要、结果表和输出文件；不要只看“成功”状态。\n"
                + "4. 如需复现，保留原始输入、参数、运行日志和输出哈希。\n\n"
                : "1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.\n"
                + "2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.\n"
                + "3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.\n"
                + "4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.\n\n");

        markdown.append(chinese ? "## 参数怎么选\n\n" : "## How to choose parameters\n\n");
        markdown.append(chinese ? "| 参数 | 必填 | 默认值 | 选择建议 |\n" : "| Parameter | Required | Default | Guidance |\n");
        markdown.append("| --- | --- | --- | --- |\n");
        for (ToolParameter parameter : tool.parameters()) {
            markdown.append("| `").append(parameter.id()).append("` ")
                    .append(escapeTable(text.parameterTitle(tool, parameter)))
                    .append(" | ").append(parameter.required()
                            ? (chinese ? "是" : "yes") : (chinese ? "否" : "no"))
                    .append(" | ").append(escapeTable(defaultValue(parameter, chinese)))
                    .append(" | ").append(escapeTable(parameterGuidance(tool, parameter, text, chinese)))
                    .append(" |\n");
        }
        markdown.append('\n');

        markdown.append(chinese ? "## 结果怎么读\n\n" : "## Read the result\n\n");
        markdown.append("- ").append(chinese ? "输出格式：" : "Output formats: ")
                .append(formats(tool.outputFormats(), chinese)).append('\n');
        markdown.append("- ").append(result).append('\n');
        markdown.append("- ").append(chinese
                ? "结果面板是快速检查入口；完整数据、运行清单和日志位于本次运行目录。"
                : "The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.").append('\n');
        String resultCheck = resultCheck(tool, chinese);
        if (!resultCheck.isBlank()) {
            markdown.append("- ").append(resultCheck).append('\n');
        }
        markdown.append('\n');

        markdown.append(chinese ? "## 最小运行示例\n\n" : "## Minimal command example\n\n");
        markdown.append("```powershell\n").append(example(tool, text, chinese)).append("\n```\n\n");
        markdown.append(chinese
                ? "把占位路径替换为工作区中的真实文件。参数名必须保持原样，布尔值使用 `true` 或 `false`。\n\n"
                : "Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.\n\n");

        markdown.append(chinese ? "## 检查清单与失败边界\n\n" : "## Checks and failure boundaries\n\n");
        for (String check : checks(tool, chinese)) {
            markdown.append("- ").append(check).append('\n');
        }
        markdown.append("- ").append(chinese
                ? "已有输出默认不会被覆盖；只有明确设置输出路径并接受覆盖时，才检查 `overwrite`。"
                : "Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.").append('\n');
        markdown.append('\n');

        markdown.append(chinese ? "## 相关下一步\n\n" : "## Related next steps\n\n");
        for (ToolDescriptor related : relatedTools(tool, descriptors)) {
            markdown.append("- [").append(text.name(related)).append("](tool:")
                    .append(related.id()).append(")\n");
        }
        markdown.append('\n');
        markdown.append(chinese
                ? "[返回文档首页](opentools-doc:README.md)\n"
                : "[Back to documentation home](opentools-doc:README.md)\n");
        return markdown.toString();
    }

    private static List<String> useCases(ToolDescriptor tool, boolean chinese) {
        String category = tool.category().toLowerCase(Locale.ROOT);
        List<String> cases = new ArrayList<>();
        if (category.equals("sequence")) {
            cases.add(chinese
                    ? "在组装、注释、比对或下游分析前检查、转换或整理 FASTA/FASTQ 数据。"
                    : "Inspect, convert, or clean FASTA/FASTQ data before assembly, annotation, alignment, or downstream analysis.");
        } else if (category.equals("table")) {
            cases.add(chinese
                    ? "整理实验表、注释表或统计结果，使列名和行结构适合后续分析。"
                    : "Organize experimental, annotation, or statistical tables so their headers and rows fit the next analysis.");
        } else if (category.contains("genome annotation")) {
            cases.add(chinese
                    ? "检查或改写基因组注释，并验证序列名、坐标和 feature 关系。"
                    : "Inspect or transform genome annotation while validating sequence names, coordinates, and feature relationships.");
        } else if (category.equals("hts")) {
            cases.add(chinese
                    ? "统计或筛选 SAM/BAM/CRAM、BED 与 VCF/BCF 数据，为质控和结果核查提供可追踪记录。"
                    : "Summarize or filter SAM/BAM/CRAM, BED, and VCF/BCF data with a traceable QC record.");
        } else if (category.equals("alignment") || category.equals("phylogeny")) {
            cases.add(chinese
                    ? "执行局部比对、多序列比对、系统发育建模或整理这些程序的输出。"
                    : "Run local alignment, multiple-sequence alignment, phylogenetic modeling, or normalize their outputs.");
        } else if (category.equals("expression") || category.equals("enrichment")) {
            cases.add(chinese
                    ? "对表达矩阵或功能注释做探索性统计，生成可继续绘图和人工审核的结果表。"
                    : "Explore expression matrices or functional annotations and produce tables for plotting and review.");
        } else if (category.equals("annotation")) {
            cases.add(chinese
                    ? "把外部注释结果规范化为稳定列名，便于筛选、合并和可视化。"
                    : "Normalize external annotation output into stable columns for filtering, joining, and visualization.");
        } else if (category.equals("comparative genomics")) {
            cases.add(chinese
                    ? "准备、运行或整理共线性与 Ka/Ks 等比较基因组分析。"
                    : "Prepare, run, or organize comparative-genomics analyses such as collinearity and Ka/Ks.");
        } else if (category.equals("visualization")) {
            cases.add(chinese
                ? "把已经校验的数值表转换为用于检查或发表的图像，同时保留可追踪的数据路径。"
                : "Turn validated numeric tables into figures for review or publication while retaining a traceable data path.");
        }
        cases.add(chinese
                ? "需要对单一步骤保留参数、日志和输出，便于重复运行或排查问题时。"
                : "Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.");
        return cases;
    }

    private static String inputPreparation(ToolDescriptor tool, boolean chinese) {
        String category = tool.category().toLowerCase(Locale.ROOT);
        if (category.equals("sequence")) {
            return chinese
                    ? "确认序列 ID 不重复、FASTA/FASTQ 记录完整；gzip 文件应保持原始记录格式。"
                    : "Check that identifiers are unambiguous and FASTA/FASTQ records are complete; gzip files must preserve the original record structure.";
        }
        if (category.equals("table")) {
            return chinese
                    ? "确认第一行是唯一表头，列名与参数一致；CSV 引号和分隔符必须合法。"
                    : "Confirm that the first row is a unique header matching the parameters and that CSV quoting and delimiters are valid.";
        }
        if (category.contains("genome annotation")) {
            return chinese
                    ? "确认序列名与基因组一致；GFF3/GTF 坐标按 1-based inclusive 理解，BED 按 0-based half-open 理解。"
                    : "Match sequence names to the genome; interpret GFF3/GTF coordinates as 1-based inclusive and BED as 0-based half-open.";
        }
        if (category.equals("hts")) {
            return chinese
                    ? "CRAM 可能还需要参考 FASTA；有索引的 BAM/CRAM 更适合区域访问，VCF/BCF 应保持排序和有效头信息。"
                    : "CRAM may also need a reference FASTA. Indexed BAM/CRAM is preferable for region access, and VCF/BCF should retain sorted records and a valid header.";
        }
        if (category.equals("visualization")) {
            return chinese
                    ? "确认表头、数值列和缺失值符合参数要求；先检查输入表，再为图像解释负责。"
                    : "Confirm that headers, numeric columns, and missing values match the parameters. Review the table before interpreting the figure.";
        }
        if (category.equals("alignment") || category.equals("phylogeny")) {
            return chinese
                    ? "确认查询/主体序列类型和格式正确；外部程序工具应检查可执行文件、版本、线程和超时。"
                    : "Confirm query and subject molecule types and formats; for external tools, check executable paths, versions, threads, and timeouts.";
        }
        return chinese
                ? "确认输入格式、ID、表头和坐标约定与参数描述一致，并保留原始文件。"
                : "Confirm that formats, identifiers, headers, and coordinate conventions match the parameter descriptions, and keep the source files.";
    }

    private static String resultCheck(ToolDescriptor tool, boolean chinese) {
        String id = tool.id();
        if (id.contains("plot") || id.startsWith("visual.")) {
            return chinese
                    ? "若输出了数据表，应同时核对图中点、组别、排序和筛选是否与数据一致。"
                    : "If a processed data table is exported, verify that plotted points, groups, ordering, and filters agree with it.";
        }
        if (tool.parameters().stream().anyMatch(parameter -> parameter.id().equals("threads"))) {
            return chinese
                    ? "线程数只影响执行路径和速度；改变线程不应改变科学结果。发现差异时应比较日志、输入和参数。"
                    : "Thread count affects execution path and speed, not the scientific result. Investigate differences by comparing logs, inputs, and parameters.";
        }
        return "";
    }

    private static List<String> checks(ToolDescriptor tool, boolean chinese) {
        List<String> checks = new ArrayList<>();
        String category = tool.category().toLowerCase(Locale.ROOT);
        if (category.equals("sequence")) {
            checks.add(chinese
                    ? "FASTA/FASTQ 记录、ID 和碱基/质量字符必须完整；损坏或截断的记录会使任务失败。"
                    : "FASTA/FASTQ records, IDs, and base or quality characters must be complete; damaged or truncated records fail the job.");
        } else if (category.equals("table")) {
            checks.add(chinese
                    ? "重复列名、缺失表头、空键和不可解析数值会导致校验或运行失败。"
                    : "Duplicate headers, missing headers, empty keys, and non-numeric values cause validation or execution failures.");
        } else if (category.contains("genome annotation")) {
            checks.add(chinese
                    ? "未知序列、反向坐标、超出基因组长度和父子 feature 缺失都应在下游解释前检查。"
                    : "Check unknown sequences, reversed coordinates, out-of-range positions, and missing parent-child features before downstream interpretation.");
        } else if (category.equals("hts")) {
            checks.add(chinese
                    ? "确认排序、索引、参考基因组版本和序列字典一致；未排序或版本不匹配会产生错误或误导性结果。"
                    : "Confirm sorted order, indexes, reference build, and sequence dictionary; unsorted or mismatched inputs can fail or mislead.");
        } else if (category.equals("visualization")) {
            checks.add(chinese
                    ? "空数据、全为缺失值、非数值列或阈值两侧没有数据时，图可能为空或无法解释。"
                    : "Empty data, all-missing values, non-numeric columns, or no observations on one side of a threshold can produce an empty or misleading figure.");
        } else if (category.equals("alignment") || category.equals("phylogeny")) {
            checks.add(chinese
                    ? "外部程序的版本和参数会改变命中、比对或树；发表时记录程序版本和完整命令。"
                    : "External program versions and arguments can change hits, alignments, or trees; record versions and complete commands for publication.");
        } else {
            checks.add(chinese
                    ? "格式、列名、坐标或资源版本不匹配时，任务会在发布输出前失败。"
                    : "Mismatched formats, columns, coordinates, or resource versions fail before outputs are published.");
        }
        if (tool.parameters().stream().anyMatch(parameter -> parameter.id().equals("extraArgs"))) {
            checks.add(chinese
                    ? "`extraArgs` 不能覆盖输入、输出、数据库、远程模式或线程数；只允许算法调优选项，并拒绝 `@` 响应文件和 `--`。"
                    : "`extraArgs` cannot override inputs, outputs, databases, remote mode, or thread counts; only algorithm-tuning options are accepted, and `@` response files and `--` are rejected.");
        }
        return checks;
    }

    private static List<ToolDescriptor> relatedTools(
            ToolDescriptor tool,
            Map<String, ToolDescriptor> descriptors
    ) {
        Map<String, List<String>> explicit = new LinkedHashMap<>();
        explicit.put("fasta.stats", List.of("fasta.filter", "fasta.extract", "sequence.orf"));
        explicit.put("fasta.filter", List.of("fasta.stats", "fasta.subsequence", "fasta.convert"));
        explicit.put("fastq.stats", List.of("fastq.filter", "fasta.convert"));
        explicit.put("fastq.filter", List.of("fastq.stats", "fasta.convert"));
        explicit.put("gxf.inspect", List.of("gxf.check-repair", "gxf.genome.match", "gxf.to-bed"));
        explicit.put("gxf.check-repair", List.of("gxf.inspect", "gxf.genome.match", "gxf.representative"));
        explicit.put("gxf.genome.match", List.of("gxf.inspect", "gxf.check-repair", "gxf.sequence.extract"));
        explicit.put("bed.stats", List.of("bed.to-gff", "bed.coverage", "gxf.inspect"));
        explicit.put("bed.to-gff", List.of("gxf.inspect", "gxf.to-bed"));
        explicit.put("gxf.to-bed", List.of("bed.stats", "gxf.inspect"));
        explicit.put("table.preview", List.of("table.filter", "table.select-columns", "table.deduplicate"));
        explicit.put("table.filter", List.of("table.select-columns", "table.deduplicate", "table.preview"));
        explicit.put("expression.normalize", List.of("expression.pca", "expression.correlation", "expression.differential-input"));
        explicit.put("expression.pca", List.of("plot.pca", "expression.correlation", "expression.normalize"));
        explicit.put("expression.correlation", List.of("plot.correlation", "expression.pca", "expression.normalize"));
        explicit.put("expression.differential-input", List.of("plot.volcano", "expression.normalize"));
        explicit.put("go.enrich", List.of("plot.enrichment-bar", "plot.enrichment-bubble", "go.level"));
        explicit.put("kegg.enrich", List.of("plot.enrichment-bar", "plot.enrichment-bubble"));
        explicit.put("annotation.eggnog.import", List.of("go.enrich", "kegg.enrich", "table.filter"));
        explicit.put("annotation.pfam.import", List.of("plot.domain", "table.filter"));
        explicit.put("mcscan.input", List.of("mcscan.run", "alignment.blast.local"));
        explicit.put("mcscan.run", List.of("mcscan.import", "plot.collinearity"));
        explicit.put("mcscan.import", List.of("plot.collinearity", "table.filter"));
        explicit.put("alignment.blast.local", List.of("alignment.blast.xml-to-table", "alignment.blast.reciprocal"));
        explicit.put("alignment.diamond", List.of("table.filter", "alignment.blast.reciprocal"));
        explicit.put("alignment.muscle", List.of("alignment.trim", "alignment.iqtree"));
        explicit.put("alignment.trim", List.of("alignment.iqtree", "tree.render"));
        explicit.put("alignment.iqtree", List.of("tree.newick.summary", "tree.render", "tree.newick.subset"));
        explicit.put("tree.newick.summary", List.of("tree.newick.subset", "tree.render"));
        explicit.put("tree.newick.subset", List.of("tree.render", "tree.newick.summary"));
        explicit.put("tree.render", List.of("tree.newick.summary", "tree.newick.subset"));
        explicit.put("visual.heatmap", List.of("plot.efp", "plot.correlation"));
        explicit.put("visual.venn", List.of("table.filter", "table.select-columns"));
        explicit.put("plot.volcano", List.of("expression.differential-input", "plot.enrichment-bubble"));
        explicit.put("plot.pca", List.of("expression.pca", "plot.correlation"));
        explicit.put("plot.correlation", List.of("expression.correlation", "plot.pca"));
        explicit.put("plot.bar", List.of("table.filter", "table.pivot"));
        explicit.put("plot.box", List.of("table.filter", "table.pivot"));
        explicit.put("plot.violin", List.of("table.filter", "table.pivot"));
        explicit.put("plot.dotplot", List.of("table.filter", "table.preview"));
        explicit.put("plot.seqlogo", List.of("alignment.muscle", "alignment.trim"));
        explicit.put("plot.gene-location", List.of("gxf.inspect", "visual.gene-structure"));
        explicit.put("visual.gene-structure", List.of("gxf.inspect", "gxf.representative"));
        explicit.put("plot.domain", List.of("annotation.pfam.import", "table.filter"));
        explicit.put("plot.motif", List.of("table.filter", "plot.domain"));
        explicit.put("plot.enrichment-bar", List.of("go.enrich", "kegg.enrich"));
        explicit.put("plot.enrichment-bubble", List.of("go.enrich", "kegg.enrich"));

        List<String> ids = explicit.get(tool.id());
        if (ids == null) {
            ids = descriptors.values().stream()
                    .filter(other -> !other.id().equals(tool.id()))
                    .filter(other -> other.category().equals(tool.category()))
                    .limit(3)
                    .map(ToolDescriptor::id)
                    .toList();
        }
        List<ToolDescriptor> related = new ArrayList<>();
        for (String id : ids) {
            ToolDescriptor descriptor = descriptors.get(id);
            if (descriptor != null && !descriptor.id().equals(tool.id())) {
                related.add(descriptor);
            }
        }
        return related.isEmpty() ? List.of(tool) : related;
    }

    private static String parameterGuidance(
            ToolDescriptor tool,
            ToolParameter parameter,
            ToolTextResolver text,
            boolean chinese
    ) {
        String description = text.parameterDescription(tool, parameter);
        String id = parameter.id();
        List<String> guidance = new ArrayList<>();
        if (id.equals("threads")) {
            if (parameter.defaultValue() != null
                    && String.valueOf(parameter.defaultValue()).equals("0")) {
                guidance.add(chinese
                        ? "大文件保留 `0` 自动选择；需要固定顺序或小文件时可用 `1`。"
                        : "Keep `0` for automatic selection on large files; use `1` for small inputs or fixed sequential processing.");
            } else {
                guidance.add(chinese
                        ? "默认值已按外部程序配置；仅在 CPU、内存或调度策略明确允许时增加。"
                        : "The default is aligned with the external program; increase it only when CPU, memory, and scheduling allow.");
            }
        } else if (id.equals("output")) {
            guidance.add(chinese
                    ? "留空时写入运行目录；自定义路径应位于工作区，并按格式填写扩展名。"
                    : "Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension.");
        } else if (id.equals("overwrite")) {
            guidance.add(chinese
                    ? "默认 `false`；只有确认目标文件可替换时才设为 `true`。"
                    : "Keep `false` unless the target is known to be safe to replace.");
        } else if (parameter.type().name().equals("FILE")) {
            guidance.add(chinese
                    ? "选择真实且完整的输入文件，不用示例占位路径替代。"
                    : "Select a complete real input file; do not submit the placeholder example path.");
        } else if (parameter.type().name().equals("OUTPUT_FILE")) {
            guidance.add(chinese
                    ? "可留空交给运行目录；自定义输出请确认父目录和扩展名。"
                    : "It may be left blank for the run directory; check the parent directory and extension for custom output.");
        } else if (parameter.type().name().equals("DIRECTORY")) {
            guidance.add(chinese
                    ? "选择可写目录；批量输出前确认磁盘空间和命名冲突。"
                    : "Choose a writable directory and check disk space and naming conflicts before batch output.");
        } else if (parameter.type().name().equals("BOOLEAN")) {
            guidance.add(chinese
                    ? "从默认值开始；只有在理解上方说明后才改变。"
                    : "Start with the default and change it only after understanding the explanation.");
        } else if (parameter.type().name().equals("ENUM")) {
            guidance.add(chinese
                    ? "优先使用默认模式；只有输入类型或分析目标明确要求时才切换。"
                    : "Prefer the default mode and switch only when the input type or analysis goal requires it.");
        } else if (parameter.type().name().equals("INTEGER")
                || parameter.type().name().equals("DECIMAL")) {
            guidance.add(chinese
                    ? "先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。"
                    : "Start from the default; document why a threshold changes and relate it to input scale or the statistical goal.");
        } else if (parameter.type().name().equals("TEXT")
                || parameter.type().name().equals("STRING")) {
            guidance.add(chinese
                    ? "可选项通常先留空；填写时保持 ID、列名或语法与输入一致。"
                    : "Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input.");
        }
        guidance.add(description);
        return String.join(" ", guidance).trim();
    }

    private static String defaultValue(ToolParameter parameter, boolean chinese) {
        Object value = parameter.defaultValue();
        if (value == null) {
            return chinese ? "空" : "empty";
        }
        if (value instanceof String string && string.isEmpty()) {
            return chinese ? "空字符串" : "empty string";
        }
        return String.valueOf(value);
    }

    private static String formats(List<String> formats, boolean chinese) {
        return formats.isEmpty()
                ? (chinese ? "由参数决定" : "defined by the parameters")
                : formats.stream().map(format -> "`" + format + "`")
                        .collect(Collectors.joining(chinese ? "、" : ", "));
    }

    private static String example(
            ToolDescriptor tool,
            ToolTextResolver text,
            boolean chinese
    ) {
        StringBuilder command = new StringBuilder("opentools run ").append(tool.id());
        for (ToolParameter parameter : tool.parameters()) {
            if (!parameter.required()) {
                continue;
            }
            command.append(" --param ").append(parameter.id()).append('=')
                    .append(exampleValue(parameter, text, tool, chinese));
        }
        return command.toString();
    }

    private static String exampleValue(
            ToolParameter parameter,
            ToolTextResolver text,
            ToolDescriptor tool,
            boolean chinese
    ) {
        if (parameter.type().name().equals("FILE")) {
            return "C:\\data\\" + parameter.id()
                    + inputExtension(tool, parameter);
        }
        if (parameter.type().name().equals("OUTPUT_FILE")) {
            return "C:\\data\\result-" + tool.id() + outputExtension(tool);
        }
        if (parameter.type().name().equals("DIRECTORY")) {
            return "C:\\data\\" + parameter.id();
        }
        if (parameter.defaultValue() != null) {
            return String.valueOf(parameter.defaultValue());
        }
        return switch (parameter.type()) {
            case INTEGER -> "1";
            case DECIMAL -> "1.0";
            case BOOLEAN -> "false";
            case ENUM -> parameter.options().isEmpty() ? "VALUE" : parameter.options().getFirst();
            case TEXT, STRING -> exampleText(parameter.id(), chinese);
            case FILE, OUTPUT_FILE, DIRECTORY -> "C:\\data\\" + parameter.id();
        };
    }

    private static String exampleText(String id, boolean chinese) {
        return switch (id) {
            case "inputs" -> "C:\\data\\input1.txt";
            case "mapping" -> "old_id=new_id";
            case "ids", "includeIds", "excludeIds" -> "seq1";
            case "taxa" -> "sampleA";
            case "regions" -> "chr1:10-100";
            case "columns", "keyColumns" -> "id";
            case "leftKey", "rightKey", "column", "numericColumn", "rowColumn",
                    "columnColumn", "groupColumn", "sequenceColumn", "startColumn",
                    "endColumn", "labelColumn", "proteinColumn", "domainColumn",
                    "motifColumn", "categoryColumn", "valueColumn" -> "value";
            case "search" -> "old";
            default -> chinese ? "示例值" : "example";
        };
    }

    private static String sampleInput(ToolDescriptor tool, boolean chinese) {
        String id = tool.id();
        String special = specialTableSample(id);
        if (!special.isBlank()) {
            return special;
        }

        List<String> blocks = new ArrayList<>();
        for (ToolParameter parameter : tool.parameters()) {
            if (!parameter.required() || blocks.size() >= 3) {
                continue;
            }
            String content;
            if (parameter.type().name().equals("FILE")) {
                content = fileSample(tool, parameter, chinese);
            } else if (parameter.type().name().equals("TEXT")) {
                content = textSample(parameter.id(), chinese);
            } else {
                content = "";
            }
            if (!content.isBlank()) {
                blocks.add("**" + parameter.id() + "**\n\n" + content);
            }
        }
        return String.join("\n\n", blocks);
    }

    private static String specialTableSample(String id) {
        return switch (id) {
            case "expression.correlation", "expression.pca", "expression.normalize",
                    "expression.differential-input", "plot.efp", "visual.heatmap" -> """
                    ```text
                    gene\tsample1\tsample2\tsample3
                    gene1\t12\t18\t15
                    gene2\t40\t36\t44
                    ```
                    """;
            case "plot.correlation" -> """
                    ```text
                    sample\tsample1\tsample2\tsample3
                    sample1\t1.00\t0.82\t0.15
                    sample2\t0.82\t1.00\t0.20
                    sample3\t0.15\t0.20\t1.00
                    ```
                    """;
            case "plot.volcano" -> """
                    ```text
                    gene\tlog2FoldChange\tpadj
                    gene1\t2.1\t0.001
                    gene2\t-1.8\t0.020
                    gene3\t0.2\t0.700
                    ```
                    """;
            case "plot.pca" -> """
                    ```text
                    sample\tPC1\tPC2\tgroup
                    sample1\t-1.2\t0.4\tcontrol
                    sample2\t-0.8\t-0.2\tcontrol
                    sample3\t1.1\t0.1\ttreatment
                    ```
                    """;
            case "plot.bar" -> """
                    ```text
                    category\tvalue
                    control\t1.2
                    treatment\t2.4
                    ```
                    """;
            case "plot.box", "plot.violin" -> """
                    ```text
                    group\tvalue
                    control\t1.1
                    control\t1.4
                    treatment\t2.2
                    treatment\t2.6
                    ```
                    """;
            case "plot.dotplot" -> """
                    ```text
                    x\ty\tgroup
                    1.0\t2.1\tcontrol
                    1.8\t3.0\tcontrol
                    2.2\t4.4\ttreatment
                    ```
                    """;
            case "plot.circos" -> """
                    ```text
                    seq_id\tstart\tend\tlabel
                    chr1\t1\t2000000\tgene-rich
                    chr2\t1\t1500000\trepeat-rich
                    ```
                    """;
            case "plot.domain" -> """
                    ```text
                    protein\tdomain\tstart\tend
                    protein1\tkinase\t12\t140
                    protein1\tsh2\t180\t240
                    ```
                    """;
            case "plot.motif" -> """
                    ```text
                    seq_id\tmotif\tstart
                    gene1\tE-box\t120
                    gene2\tE-box\t95
                    ```
                    """;
            case "plot.enrichment-bar" -> """
                    ```text
                    term\tadjusted_p_value
                    GO:0006950\t0.001
                    GO:0003674\t0.010
                    ```
                    """;
            case "plot.enrichment-bubble" -> """
                    ```text
                    term\tfold_enrichment\tadjusted_p_value\thits
                    GO:0006950\t2.4\t0.001\t18
                    GO:0003674\t1.7\t0.010\t31
                    ```
                    """;
            case "plot.collinearity" -> """
                    ```text
                    block\tpair_index\tquery_gene\tsubject_gene
                    block1\t1\tgeneA1\tgeneB1
                    block1\t2\tgeneA2\tgeneB2
                    ```
                    """;
            case "tree.render" -> """
                    ```text
                    ((A:0.1,B:0.2):0.3,C:0.4);
                    ```
                    """;
            case "plot.gene-location", "visual.gene-structure" -> gffSample();
            default -> "";
        };
    }

    private static String fileSample(
            ToolDescriptor tool,
            ToolParameter parameter,
            boolean chinese
    ) {
        String id = parameter.id().toLowerCase(Locale.ROOT);
        String description = parameter.description().toLowerCase(Locale.ROOT);
        if (id.equals("query") && tool.id().contains("blast.fastq")) {
            return fastqSample();
        }
        if (id.contains("genome") || id.contains("reference") || id.contains("subject")
                || description.contains("fasta")) {
            return fastaSample();
        }
        if (description.contains("gff3") || description.contains("gtf")
                || (id.contains("annotation") && tool.category().equals("Genome annotation"))) {
            return gffSample();
        }
        if (id.contains("regions") && (description.contains("bed")
                || tool.inputFormats().contains("BED"))) {
            return bedSample();
        }
        if (description.contains("fastq") || tool.inputFormats().stream()
                .anyMatch(format -> format.startsWith("FASTQ"))) {
            return fastqSample();
        }
        if (description.contains("vcf") || tool.inputFormats().stream()
                .anyMatch(format -> format.contains("VCF") || format.equals("BCF"))) {
            return vcfSample();
        }
        if (id.contains("alignment") || description.contains("sam")
                || description.contains("bam") || description.contains("cram")) {
            return samSample();
        }
        if (tool.inputFormats().contains("BED") || description.contains("bed")) {
            return bedSample();
        }
        if (tool.inputFormats().stream().anyMatch(format -> format.contains("Newick"))) {
            return newickSample();
        }
        if (description.contains("domtblout") || description.contains("hmm database")
                || description.contains("hmm query")) {
            return "";
        }
        if (tool.inputFormats().stream().anyMatch(format -> format.contains("FASTA"))) {
            return fastaSample();
        }
        if (tool.inputFormats().stream().anyMatch(format ->
                format.startsWith("TSV") || format.startsWith("CSV"))) {
            return chinese
                    ? "```text\ngroup\tvalue\ncontrol\t1.2\ntreatment\t2.4\n```"
                    : "```text\ngroup\tvalue\ncontrol\t1.2\ntreatment\t2.4\n```";
        }
        return "";
    }

    private static String textSample(String parameterId, boolean chinese) {
        return switch (parameterId) {
            case "regions" -> """
                    ```text
                    chr1:10-100
                    chr2:250-400
                    ```
                    """;
            case "inputs" -> """
                    ```text
                    C:\\data\\part1.gff3
                    C:\\data\\part2.gff3
                    ```
                    """;
            case "taxa" -> """
                    ```text
                    sampleA
                    sampleB
                    ```
                    """;
            case "mapping" -> """
                    ```text
                    old_gene_id\tnew_gene_id
                    gene1\tgeneA
                    gene2\tgeneB
                    ```
                    """;
            case "ids", "includeIds", "excludeIds" -> """
                    ```text
                    seq1
                    seq2
                    ```
                    """;
            default -> "";
        };
    }

    private static String inputExtension(ToolDescriptor tool, ToolParameter parameter) {
        String id = parameter.id().toLowerCase(Locale.ROOT);
        String description = parameter.description().toLowerCase(Locale.ROOT);
        if (id.contains("genome") || id.contains("reference") || id.contains("subject")
                || description.contains("fasta")) {
            return ".fasta";
        }
        if (description.contains("gff3") || description.contains("gtf")
                || (id.contains("annotation") && tool.category().equals("Genome annotation"))) {
            return ".gff3";
        }
        if (id.contains("regions") && (description.contains("bed")
                || tool.inputFormats().contains("BED"))) {
            return ".bed";
        }
        if (description.contains("fastq")) {
            return ".fastq";
        }
        if (description.contains("vcf") || description.contains("bcf")) {
            return ".vcf";
        }
        if (id.contains("alignment") || description.contains("sam")
                || description.contains("bam") || description.contains("cram")) {
            return ".bam";
        }
        if (tool.inputFormats().contains("BED") || description.contains("bed")) {
            return ".bed";
        }
        if (tool.inputFormats().stream().anyMatch(format -> format.contains("Newick"))) {
            return ".nwk";
        }
        if (tool.inputFormats().stream().anyMatch(format -> format.contains("FASTA"))) {
            return ".fasta";
        }
        return ".tsv";
    }

    private static String outputExtension(ToolDescriptor tool) {
        String format = tool.outputFormats().stream().findFirst().orElse("TSV");
        return switch (format) {
            case "BEDGraph" -> ".bedgraph";
            case "BED" -> ".bed";
            case "GFF3" -> ".gff3";
            case "GTF" -> ".gtf";
            case "FASTA", "FASTA.gz", "FASTA alignment" -> ".fasta";
            case "FASTQ", "FASTQ.gz" -> ".fastq";
            case "Newick" -> ".nwk";
            case "VCF" -> ".vcf";
            case "VCF.gz" -> ".vcf.gz";
            case "BCF" -> ".bcf";
            case "PNG", "SVG", "PDF" -> "." + format.toLowerCase(Locale.ROOT);
            case "SAM" -> ".sam";
            case "BAM" -> ".bam";
            default -> ".tsv";
        };
    }

    private static String fastaSample() {
        return """
                ```text
                >seq1
                ATGCGTACGT
                >seq2
                ATGCGTTCGT
                ```
                """;
    }

    private static String fastqSample() {
        return """
                ```text
                @read1
                ACGTACGT
                +
                IIIIIIII
                ```
                """;
    }

    private static String bedSample() {
        return """
                ```text
                chr1\t0\t100\tfeature1\t0\t+
                chr1\t50\t150\tfeature2\t0\t-
                ```
                """;
    }

    private static String gffSample() {
        return """
                ```text
                ##gff-version 3
                chr1\tOpenTools\tgene\t1\t100\t.\t+\t.\tID=gene1
                chr1\tOpenTools\tmRNA\t1\t100\t.\t+\t.\tID=tx1;Parent=gene1
                ```
                """;
    }

    private static String samSample() {
        return """
                ```text
                @HD\tVN:1.6\tSO:coordinate
                @SQ\tSN:chr1\tLN:1000
                read1\t0\tchr1\t10\t60\t8M\t*\t0\t0\tACGTACGT\tIIIIIIII
                ```
                """;
    }

    private static String vcfSample() {
        return """
                ```text
                ##fileformat=VCFv4.2
                #CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO
                chr1\t10\t.\tA\tG\t50\tPASS\t.
                ```
                """;
    }

    private static String newickSample() {
        return """
                ```text
                ((A:0.1,B:0.2):0.3,C:0.4);
                ```
                """;
    }

    private static String escapeTable(String value) {
        return value.replace("|", "\\|")
                .replace("\r", " ")
                .replace("\n", " ")
                .trim();
    }

    private static Map<String, String> resultZh() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("hts.bam.coverage", "BEDGraph 给出每个参考位置的覆盖深度；重点检查目标区间、极端高深度、零覆盖片段和读段对重叠处理是否符合实验设计。");
        values.put("plot.bar", "每个条形对应一个类别的数值；检查类别顺序、零值/负值、数值单位，以及条形长度是否被图表范围截断。");
        values.put("plot.box", "箱线图概括各组中位数、四分位数和离群值；必须结合每组样本量阅读，不能只比较箱体高度。");
        values.put("plot.circos", "环形图中每个标记对应一条序列区间；它适合总体概览，精确坐标和数量仍应回到输入表与导出数据核对。");
        values.put("bed.coverage", "结果表为每个 BED 区间给出平均覆盖深度；应结合区间长度、读段分布和目标捕获/测序策略解释。");
        values.put("bed.stats", "摘要包含区间数、总跨度、长度分布、链方向与序列 ID；用于发现空区间、异常长度和命名错误。");
        values.put("alignment.blast.fastq", "输出为 BLAST 表格；先核对 query/subject ID、E-value、覆盖度和命中数量，再判断同源关系。");
        values.put("alignment.blast.regions", "输出按 BED 区域给出 BLAST 命中；区域坐标、查询方向和 subject 版本是解释结果的关键。");
        values.put("gxf.check-repair", "诊断表指出缺失或重复 ID、父子关系与 CDS phase 问题；修复输出应再次运行检查确认。");
        values.put("gxf.genome.match", "不匹配报告会列出序列名、长度或坐标问题；先修复注释与基因组版本不一致，再进行区间分析。");
        values.put("bed.to-gff", "GFF3 使用 1-based inclusive 坐标；转换后应先做几条记录的人工边界核对，再批量下游使用。");
        values.put("plot.collinearity", "图中每个点对应一条共线性基因对；按 block 检查顺序和异常离群点，组合 `mcscan.import` 的表格复核。");
        values.put("alignment.blast.xml-to-table", "输出固定为 12 列 BLAST 表格字段；行对应 HSP，不是独立的 query-subject 组合。");
        values.put("fasta.convert", "转换结果应保持序列 ID、序列内容、大小写和记录顺序；TSV 往返转换后应抽查一致性。");
        values.put("gxf.to-bed", "BED 起点会从 GFF3/GTF 的 1-based 起点转换为 0-based；终点保持半开区间的边界语义。");
        values.put("plot.correlation", "相关系数越接近 1 或 -1 表示线性/秩相关越强；颜色只能辅助观察，不能替代样本量和显著性判断。");
        values.put("table.deduplicate", "每个 key 只保留第一次出现的行；被删除的行需要从原始表追溯，不能把去重表当作完整数据。");
        values.put("alignment.diamond", "输出为 DIAMOND 表格命中；E-value、bit score、覆盖度和数据库版本共同决定解释，不能只看一条阈值。");
        values.put("plot.domain", "每条蛋白的一行显示结构域区间；检查坐标是否相对蛋白长度，并区分重叠结构域与相邻结构域。");
        values.put("plot.dotplot", "每个点来自表中的一对 x/y 数值；先确认坐标单位和点的分组，再寻找趋势或异常。");
        values.put("plot.efp", "热图颜色表示表达值或处理后的表达矩阵；检查样本顺序、归一化方式，并导出 matrix 复核。");
        values.put("plot.enrichment-bar", "Y 轴是 `-log10(adjusted p-value)`；条形越高表示校正后显著性越强，但效应大小仍需结合 hits 和输入基因集。");
        values.put("plot.enrichment-bubble", "横轴表示 fold enrichment，气泡大小表示命中数，颜色/显著性反映 adjusted P-value；应同时观察效应与证据量。");
        values.put("expression.correlation", "输出为样本两两相关系数矩阵；检查样本名、相关系数、样本量和方法（Pearson/Spearman）是否一致。");
        values.put("expression.pca", "输出解释方差和样本坐标；PC1/PC2 通常用于探索分组，但不能单独证明因果关系或显著差异。");
        values.put("fasta.extract", "输出只包含匹配的完整 FASTA 记录；报告中未命中的 ID 应被视为请求失败或命名不一致。");
        values.put("fasta.subsequence", "输出包含按 ID/坐标/方向提取的序列及诊断；负链坐标和 1-based inclusive 边界必须逐条确认。");
        values.put("gxf.sequence.extract", "FASTA 每条记录对应一个 feature/组，诊断表保留提取状态；负链注释的上游/下游方向必须按链解释。");
        values.put("fasta.stats", "先看序列数、总长度、N50、GC、N 和 gap；再检查单条长度分布，避免只用一个汇总值判断整体质量。");
        values.put("fastq.stats", "摘要用于检查读段长度、质量编码、Q20/Q30 和碱基组成；AUTO 质量编码有歧义时应显式指定。");
        values.put("fasta.filter", "输出保留满足全部筛选条件的记录，报告解释每条被过滤的原因；阈值变化应记录在运行参数中。");
        values.put("fastq.filter", "输出为修剪后的 FASTQ，报告说明长度、质量和 N 含量过滤原因；修剪会改变坐标和下游映射，应保留原文件。");
        values.put("gxf.filter", "输出保留通过类型、来源、序列、链、坐标和属性条件的记录；过滤后的注释可能缺少父子关系，需复核。");
        values.put("table.filter", "输出保留满足文本或数值条件的行；检查是否遗漏空值、列类型是否正确，以及 `invert` 是否反了筛选意图。");
        values.put("hts.vcf.filter", "输出为筛选后的 VCF/BCF；检查记录数、INFO/FORMAT 字段、压缩格式和索引是否与下游工具匹配。");
        values.put("gxf.overlap", "输出为重叠区间对；检查最小重叠长度、feature type、方向与报告上限，避免把截断当成完整结果。");
        values.put("gxf.gene-density", "每个窗口报告 gene start 数和每 Mb 密度；窗口/步长决定分辨率，必须与所用基因组版本一起解释。");
        values.put("plot.gene-location", "图中位置来自注释坐标；检查显示范围、基因方向和裁剪边界，避免把不可见记录误认为缺失。");
        values.put("visual.gene-structure", "图展示所选基因的转录本与外显子/CDS 结构；先确认代表转录本和坐标范围，再比较异构体。");
        values.put("go.enrich", "结果包含 GO term、命中数、背景、fold enrichment、P 值和 BH 校正值；先确认背景与基因 ID 映射。");
        values.put("go.level", "结果给出每个 GO term 到根的最短距离；层级越深通常越具体，但不能把层级当作质量或显著性的替代指标。");
        values.put("visual.heatmap", "热图展示处理后的矩阵；检查行/列排序、聚类、缺失值和色标范围，并用导出数据解释颜色。");
        values.put("alignment.hmmer.search", "domtblout 记录蛋白序列的 HMM 命中与 domain 区间；结合 E-value、覆盖度、模型版本和 profile 数据库解释。");
        values.put("alignment.hmmer.hmmsearch", "domtblout 记录 HMM 与蛋白库的 domain 命中；注意查询/目标方向与 hmmscan 不同。");
        values.put("annotation.eggnog.import", "规范化表保留基因、GO、KEGG、COG 和描述；检查 eggNOG 数据库版本与基因 ID 映射。");
        values.put("mcscan.import", "每行表示一个共线性基因对及其 block/顺序；block 内的缺失或乱序可能提示输入格式问题。");
        values.put("annotation.pfam.import", "规范化表将 HMMER domain 命中转为蛋白、domain、区间和显著性字段；检查坐标与重复命中。");
        values.put("gxf.inspect", "摘要按记录、序列和 feature type 汇总；先发现未知序列、极端长度和异常 feature 类型。");
        values.put("alignment.iqtree", "Newick 树给出拓扑和分支信息；若启用 bootstrap，应结合支持率解释，而不是只看单个分支。");
        values.put("table.join", "输出列由左右表拼接生成；检查 join type、键重复导致的行数扩张、空键和缺失值。");
        values.put("kaks.calculate", "每对 CDS 输出 Ka/Ks 估计；比对质量、密码子对齐、序列差异和零替换情况都会影响解释。");
        values.put("kegg.enrich", "结果按 pathway 报告命中、背景、fold enrichment 和 BH 校正值；反应注释版本与基因 ID 体系可能不同。");
        values.put("alignment.blast.local", "输出以 BLAST XML 为主，并派生表格和 alignment TSV；先核对数据库缓存、E-value、覆盖度与 program 类型，结果面板可直接打开 BLAST viewer。");
        values.put("gxf.id-map", "输出重写指定属性的 ID；必须检查重复新 ID、父子引用是否一致以及未映射项。");
        values.put("fasta.merge", "输出按输入列表顺序拼接 FASTA；检查重复 ID、文件顺序和来源记录，必要时先统计各输入。");
        values.put("gxf.merge-split", "合并结果保留输入记录但可能产生重复 ID；拆分结果按序列或类型生成多个文件，应检查目录和命名冲突。");
        values.put("plot.motif", "每个点表示指定序列上的 motif 起点；核对坐标基准、motif 标签和序列行顺序。");
        values.put("alignment.muscle", "输出为多序列比对；检查行数、序列名和对齐长度，错误输入字母或过短序列会影响结果。");
        values.put("tree.newick.summary", "摘要包含叶子、节点、深度和分支长度；树语法错误或支持率字段不影响拓扑时仍可能影响解释。");
        values.put("expression.normalize", "输出为指定方法的归一化表达矩阵；RPKM/FPKM/TPM 依赖有效基因长度，TAU 解释组织特异性。");
        values.put("plot.pca", "图中每个点对应一个样本的 PC 坐标；结合 `expression.pca` 的解释方差和分组标签阅读。");
        values.put("table.pivot", "宽表单元格由指定聚合方式生成；检查重复 cell、缺失组合和 column/row 顺序。");
        values.put("sequence.orf", "输出为预测 ORF 的核苷酸序列；检查遗传密码、起始密码子、最小长度、链方向和部分 ORF 选项。");
        values.put("expression.differential-input", "输出为适合差异分析的长表；检查样本分组完整、表达矩阵列名与分组文件一一对应。");
        values.put("mcscan.input", "生成 `.gff` 与 `.blast` 输入；检查基因 ID、染色体命名、BLAST 方向和文件前缀是否一致。");
        values.put("table.preview", "预览表和行列计数用于快速检查文件结构；`countRows=false` 时总行数可能只是预览范围。");
        values.put("alignment.blast.reciprocal", "只保留正向和反向相互最佳命中的 pair；检查每个 query 的最佳命中规则与方向命名。");
        values.put("fasta.rename", "输出为改 ID 后的 FASTA 与重命名报告；应检查重复 ID、map 未命中和正则替换意外扩大。");
        values.put("table.replace", "输出只修改指定列的正则匹配文本；检查替换组、空匹配和是否误改未预期行。");
        values.put("mcscan.run", "输出 MCScanX 共线性结果；检查 .gff/.blast 配对、程序版本、内存、超时和 block 结果。");
        values.put("hts.sam-stats", "统计摘要用于检查比对率、读段数、长度和参考序列；结合排序、dup/mapq 过滤策略解释。");
        values.put("gxf.representative", "每个基因只保留一个代表转录本；选择依据（转录本长度、CDS 长度或首条）必须与生物学问题匹配。");
        values.put("table.select-columns", "输出列顺序严格按请求列名排列；缺失列、重复列名和空表头会失败。");
        values.put("plot.seqlogo", "logo 展示每个位置的 A/C/G/T 偏好；检查比对是否等长、伪计数和是否同时统计反向互补。");
        values.put("fasta.split", "输出目录包含按记录数切分的多个 FASTA；记录数、文件命名和最后一部分大小应与参数一致。");
        values.put("tree.newick.subset", "输出只保留指定 taxa 并剪除空分支；检查未找到的 taxa、树根和分支长度是否保持预期。");
        values.put("hts.bam.subset", "输出为指定区域的 SAM/BAM；区域是 1-based inclusive，BAM 默认还会生成 BAI 索引。");
        values.put("fasta.transform", "输出为转换后的序列和报告；检查核酸/蛋白类型、反向互补、大小写以及被清除的非法字符。");
        values.put("fasta.translate-cds", "输出为蛋白 FASTA；先确认 CDS 起始帧、遗传密码、终止密码子和长度是否为 3 的倍数。");
        values.put("table.transpose", "输出把原列变成行、原行变成列；检查首列身份是否丢失，以及大表是否走磁盘路径。");
        values.put("tree.render", "输出为矩形树的线段模型与图像；检查根、分支长度比例、叶标签和图的裁剪。");
        values.put("alignment.trim", "输出为修剪后的多序列比对；修剪会删除列，检查保留位点是否与系统发育目标兼容。");
        values.put("hts.vcf.stats", "摘要报告变异、样本、SNP、indel、转换和颠换；先检查参考版本、样本列和过滤状态。");
        values.put("visual.venn", "集合交集的精确计数在导出表中；Venn 适合少量集合，UpSet 更适合多集合和交集比较。");
        values.put("plot.violin", "小提琴图展示分组数值分布；检查每组样本量，不能把形状差异直接解释为统计显著。");
        values.put("plot.volcano", "每个点是一个基因；横轴为 log2 fold change，纵轴为显著性，阈值分类必须在图中和导出表中一致。");
        values.put("fastq.split", "输出目录包含按记录数切分的 FASTQ；检查记录总数、header、序列和质量值的完整性。");
        values.put("sanger.assemble", "先看 overlap 与 reads 表，再看 assembly.fa 的完整组装；corrected.fa 是参考锚定结果，provenance.bed 标记 READ_CONSENSUS、LOW_CONFIDENCE 和 REFERENCE_FILL，不能把参考填充区当作测序证据。");
        values.put("bed.blocks", "输出逐 block 和 intron 的区间；检查 blockStarts 是否相对 chromStart、blockSizes 是否与 blockCount 一致。");
        values.put("gxf.edit", "编辑会保留注释头并改写数据行；检查 source/type/strand、属性增删和 GTF 引号格式。");
        values.put("expression.differential", "输出每个基因的组均值、log2 fold change、检验统计量、原始 P 值和校正 P 值；两组至少各需两个重复。");
        values.put("go.gsea", "NES 和置换 P 值依赖排序方向和基因集大小；检查 GMT 标识符与排序文件一致，并用固定 seed 复现。");
        values.put("tree.newick.metadata", "输出每个节点的分支长度、支持率、深度和后代叶数；非数值内部标签仍作为 label 保留。");
        values.put("plot.synteny", "多物种共线性图按参考基因组和比较对象排列染色体；检查物种顺序、基因对方向、颜色和标签重叠。");
        values.put("alignment.blast.alignments", "输出每个 HSP 的 query/subject 坐标和成对对齐序列；检查 qseq/hseq 是否完整、midline 与坐标方向一致。");
        values.put("annotation.meme.results", "输出 MEME motif 表和 MAST hit 表；检查 motif ID、命中的 1-based 坐标、链方向和 match 序列。");
        return values;
    }

    private static Map<String, String> resultEn() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("hts.bam.coverage", "The BEDGraph reports covered depth at reference positions; review target intervals, extreme depth, zero-coverage stretches, and overlapping-mate handling against the experimental design.");
        values.put("plot.bar", "Each bar represents one category value; verify category order, zero or negative values, units, and whether the axis clips any bar.");
        values.put("plot.box", "The box plot summarizes medians, quartiles, and outliers by group; interpret it with each group's sample size rather than comparing box height alone.");
        values.put("plot.circos", "Each mark represents a sequence interval in a circular overview; use the source table and exported data for exact coordinates and counts.");
        values.put("bed.coverage", "The table reports mean depth for each BED interval; interpret it together with interval size, read distribution, and the capture or sequencing strategy.");
        values.put("bed.stats", "The summary reports interval count, span, length distribution, strands, and sequence IDs, helping expose empty intervals, unusual lengths, and naming errors.");
        values.put("alignment.blast.fastq", "The BLAST table should be reviewed by query/subject identity, E-value, coverage, and hit count before inferring homology.");
        values.put("alignment.blast.regions", "BLAST hits are reported for BED-defined regions; region coordinates, query orientation, and subject version are essential for interpretation.");
        values.put("gxf.check-repair", "The diagnostics identify missing or duplicate IDs, parent-child problems, and CDS phase issues; rerun validation after writing repaired output.");
        values.put("gxf.genome.match", "The mismatch report lists sequence-name, length, or coordinate problems; resolve annotation and genome-version disagreements before interval analysis.");
        values.put("bed.to-gff", "GFF3 coordinates are 1-based inclusive; inspect boundary values for a few records before using the converted file in bulk.");
        values.put("plot.collinearity", "Each point represents a collinear gene pair; review block order, outliers, and the imported table from `mcscan.import`.");
        values.put("alignment.blast.xml-to-table", "The output uses the stable 12-column BLAST table layout; rows represent HSPs, not one row per query-subject pair.");
        values.put("fasta.convert", "Conversion should preserve identifiers, sequence content, case, and record order; spot-check round trips through sequence TSV.");
        values.put("gxf.to-bed", "BED starts are converted from 1-based GFF3/GTF starts to 0-based coordinates while ends retain half-open boundary semantics.");
        values.put("plot.correlation", "Correlations near 1 or -1 indicate stronger linear or rank association; color does not replace sample size or significance assessment.");
        values.put("table.deduplicate", "The first row for each key is retained; rejected rows remain in the source table and must not be treated as absent observations.");
        values.put("alignment.diamond", "DIAMOND hits depend on E-value, bit score, coverage, and database version; a single threshold is not a complete homology conclusion.");
        values.put("plot.domain", "Each protein row shows domain intervals; verify coordinates against protein length and distinguish overlapping from adjacent domains.");
        values.put("plot.dotplot", "Each point is an x/y numeric pair from the table; confirm units and grouping before interpreting trends or outliers.");
        values.put("plot.efp", "Color represents expression values or a processed matrix; check sample order and normalization, and review the exported matrix.");
        values.put("plot.enrichment-bar", "Bars show `-log10(adjusted p-value)`; stronger bars indicate greater adjusted significance, while effect size still requires fold enrichment and hit counts.");
        values.put("plot.enrichment-bubble", "Position shows fold enrichment, bubble size shows hit count, and color or significance shows the adjusted P-value; inspect evidence size as well as significance.");
        values.put("expression.correlation", "The output is a sample-by-sample correlation matrix; verify sample labels, coefficients, sample count, and the Pearson/Spearman choice.");
        values.put("expression.pca", "The output contains explained variance and sample coordinates; PC axes support exploration but do not alone prove causal or significant separation.");
        values.put("fasta.extract", "Only matching complete FASTA records are emitted; requested IDs absent from the report indicate naming mismatches or missing records.");
        values.put("fasta.subsequence", "The output contains sequences requested by ID, coordinates, and strand, plus diagnostics; verify negative-strand and 1-based inclusive boundaries.");
        values.put("gxf.sequence.extract", "Each FASTA record represents a feature or group, with diagnostics retained; upstream and downstream directions must follow annotation strand.");
        values.put("fasta.stats", "Review sequence count, total length, N50, GC, N, and gaps, then inspect record-length distribution instead of relying on one summary value.");
        values.put("fastq.stats", "The summary supports checks of read length, quality encoding, Q20/Q30, and base composition; choose encoding explicitly when AUTO is ambiguous.");
        values.put("fasta.filter", "The output retains records satisfying all active filters, with reasons in the report; document threshold changes in run parameters.");
        values.put("fastq.filter", "Output reads are trimmed and filtered by length, quality, and N content; trimming changes downstream coordinates, so preserve the source FASTQ.");
        values.put("gxf.filter", "Records passing type, source, sequence, strand, coordinate, and attribute filters are retained; filtered annotation may lose parent-child context.");
        values.put("table.filter", "The output retains rows satisfying text or numeric predicates; check missing values, inferred types, and whether `invert` reverses the intended selection.");
        values.put("hts.vcf.filter", "The result is filtered VCF/BCF; verify record count, INFO/FORMAT fields, compression, and index compatibility with downstream tools.");
        values.put("gxf.overlap", "The output contains overlapping interval pairs; check minimum overlap, feature type, strand, and report caps so truncation is not mistaken for completion.");
        values.put("gxf.gene-density", "Each window reports gene starts and genes per megabase; window and step sizes determine resolution and must be interpreted with the genome build.");
        values.put("plot.gene-location", "Gene positions come from annotation coordinates; confirm displayed range, strand, and clipping boundaries before assuming records are absent.");
        values.put("visual.gene-structure", "The figure shows transcripts and exon/CDS structure for selected genes; verify representative transcripts and coordinate ranges before comparing isoforms.");
        values.put("go.enrich", "Results report GO term, hits, background, fold enrichment, P-values, and BH-adjusted values; verify the background and gene-ID mapping first.");
        values.put("go.level", "The output gives each GO term's shortest distance from the root; deeper terms tend to be more specific, but depth is not a quality or significance score.");
        values.put("visual.heatmap", "The heatmap displays a processed matrix; review row/column order, clustering, missing values, and color limits against the exported data.");
        values.put("alignment.hmmer.search", "domtblout records protein-to-HMM domain hits; interpret E-values, coverage, model versions, and the profile database together.");
        values.put("alignment.hmmer.hmmsearch", "domtblout records HMM-to-protein-domain hits; note that query and target orientation differs from hmmscan.");
        values.put("annotation.eggnog.import", "The normalized table preserves gene, GO, KEGG, COG, and description fields; verify eggNOG database version and gene-ID mapping.");
        values.put("mcscan.import", "Each row is a collinear gene pair with block and order fields; missing or out-of-order rows may indicate an input format problem.");
        values.put("annotation.pfam.import", "The normalized table converts HMMER domain hits into protein, domain, interval, and significance fields; inspect coordinates and duplicate hits.");
        values.put("gxf.inspect", "The summary aggregates records, sequences, and feature types; look first for unknown sequences, extreme lengths, and unexpected feature classes.");
        values.put("alignment.iqtree", "The Newick tree reports topology and branch information; when enabled, interpret branches together with bootstrap support.");
        values.put("table.join", "The output concatenates left and right columns; inspect join type, row expansion from duplicate keys, empty keys, and missing values.");
        values.put("kaks.calculate", "Each adjacent CDS pair has a Ka/Ks estimate; alignment quality, codon alignment, sequence divergence, and zero substitutions all affect interpretation.");
        values.put("kegg.enrich", "Each pathway row reports hits, background, fold enrichment, and BH-adjusted values; pathway annotation versions and gene-ID systems may differ.");
        values.put("alignment.blast.local", "The primary output is BLAST XML with derived tabular and alignment TSVs; verify cache identity, E-value, coverage, and program type, then open the BLAST viewer in the Results panel.");
        values.put("gxf.id-map", "The output rewrites selected attribute IDs; check duplicate new IDs, parent-child consistency, and unmapped values.");
        values.put("fasta.merge", "FASTA records are concatenated in the listed order; check duplicate identifiers, file order, and per-input counts.");
        values.put("gxf.merge-split", "Merged output preserves input records and may contain duplicate IDs; split output creates files by sequence or type, so inspect naming and directories.");
        values.put("plot.motif", "Each point marks a motif start on a sequence; verify coordinate basis, motif labels, and sequence-row order.");
        values.put("alignment.muscle", "The output is a multiple-sequence alignment; verify record count, names, and alignment length, especially after invalid or short inputs.");
        values.put("tree.newick.summary", "The summary reports leaves, nodes, depth, and branch lengths; malformed syntax or support-value fields can still affect interpretation.");
        values.put("expression.normalize", "The output is a matrix transformed by the selected method; RPKM/FPKM/TPM depend on effective gene lengths, while TAU summarizes tissue specificity.");
        values.put("plot.pca", "Each point is a sample in PC coordinates; read it with the explained variance and grouping produced by `expression.pca`.");
        values.put("table.pivot", "Wide-table cells use the selected aggregation; check duplicate cells, missing row-column combinations, and output ordering.");
        values.put("sequence.orf", "The output contains predicted ORF nucleotide sequences; inspect genetic code, start codons, minimum length, strand, and partial-ORF behavior.");
        values.put("expression.differential-input", "The output is a long table for differential analysis; verify that sample columns match the grouping file and no group assignment is missing.");
        values.put("mcscan.input", "The tool creates `.gff` and `.blast` inputs; check gene IDs, chromosome naming, BLAST orientation, and the generated file prefix.");
        values.put("table.preview", "The preview and row/column counts support a quick structural check; when `countRows=false`, the reported count may cover only the preview.");
        values.put("alignment.blast.reciprocal", "Only mutual best-hit pairs from forward and reverse searches are retained; verify best-hit rules and query orientation.");
        values.put("fasta.rename", "The output contains renamed FASTA plus a report; check duplicate IDs, unmapped entries, and unintended regex replacements.");
        values.put("table.replace", "Only regex matches in the chosen column are replaced; verify capture groups, empty matches, and rows modified unintentionally.");
        values.put("mcscan.run", "The output is MCScanX collinearity; check the paired `.gff`/`.blast` files, program version, memory, timeout, and blocks.");
        values.put("hts.sam-stats", "The summary supports checks of alignment rate, read count, length, and references; interpret it with sorting and duplicate/mapq filtering.");
        values.put("gxf.representative", "One representative transcript is retained per gene; the selection rule must match the biological question and should be recorded.");
        values.put("table.select-columns", "Output columns follow the requested names and order; missing or duplicate names and empty headers fail validation.");
        values.put("plot.seqlogo", "The logo shows positional A/C/G/T preference; verify equal alignment length, pseudocount behavior, and whether reverse complements were counted.");
        values.put("fasta.split", "The output directory contains FASTA parts split by record count; verify record totals, file names, and the final partial part.");
        values.put("tree.newick.subset", "Selected taxa are retained and empty branches pruned; check missing taxa, rooting, and branch lengths.");
        values.put("hts.bam.subset", "The output contains alignments from 1-based inclusive regions; BAM output also creates a BAI index by default.");
        values.put("fasta.transform", "The output contains transformed sequences and a report; verify molecule type, reverse complement, case, and removed invalid characters.");
        values.put("fasta.translate-cds", "The output is protein FASTA; confirm CDS frame, genetic code, stop-codon handling, and whether input length is divisible by three.");
        values.put("table.transpose", "Original columns become rows and original rows become columns; check preservation of the first-column identity and disk-backed path.");
        values.put("tree.render", "The output is a rectangular tree model and image; verify rooting, branch-length scaling, leaf labels, and image clipping.");
        values.put("alignment.trim", "The output is a trimmed multiple-sequence alignment; trimming removes columns, so confirm retained sites match the phylogenetic goal.");
        values.put("hts.vcf.stats", "The summary reports variants, samples, SNPs, indels, transitions, and transversions; check reference build, sample columns, and FILTER status.");
        values.put("visual.venn", "Exact intersection counts are exported when `exportData=true`; Venn is compact for a few sets, while UpSet scales better for many sets.");
        values.put("plot.violin", "Violin plots show grouped numeric distributions; inspect group sample sizes and avoid interpreting shape alone as statistical significance.");
        values.put("plot.volcano", "Each point is a gene; horizontal position is log2 fold change and vertical position is significance, and thresholds must agree with exported classifications.");
        values.put("fastq.split", "The output directory contains FASTQ parts split by record count; verify total records, headers, sequences, and qualities.");
        values.put("sanger.assemble", "Review the read/overlap table first, then assembly.fa for the full de novo contig; corrected.fa is reference-anchored, and provenance.bed marks READ_CONSENSUS, LOW_CONFIDENCE, and REFERENCE_FILL. Never treat reference-filled positions as sequencing evidence.");
        values.put("bed.blocks", "The output lists block and intron intervals; verify that blockStarts are relative to chromStart and blockSizes match blockCount.");
        values.put("gxf.edit", "Editing preserves annotation headers while rewriting data rows; verify source/type/strand, attribute changes, and GTF quoting.");
        values.put("expression.differential", "Each gene reports group means, log2 fold change, test statistic, raw p-value, and adjusted p-value; each group needs at least two replicates.");
        values.put("go.gsea", "NES and permutation p-values depend on ranking direction and set size; check GMT identifiers against the ranking and reproduce with a fixed seed.");
        values.put("tree.newick.metadata", "Each node reports branch length, support, depth, and descendant leaves; non-numeric internal labels remain labels.");
        values.put("plot.synteny", "The multi-species synteny plot orders chromosomes by reference and comparison species; verify species order, gene-pair direction, colors, and label overlap.");
        values.put("alignment.blast.alignments", "Each HSP reports query/subject coordinates and paired alignment strings; verify qseq/hseq completeness, midline, and coordinate orientation.");
        values.put("annotation.meme.results", "The output contains MEME motif and MAST hit tables; verify motif IDs, 1-based hit coordinates, strand, and match strings.");
        return values;
    }
}
