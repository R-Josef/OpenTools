package io.opentools.ui;

import io.opentools.core.i18n.LanguageManager;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.plugin.host.ToolTextResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ToolDocumentation {
    private ToolDocumentation() {
    }

    static String render(ToolDescriptor tool, LanguageManager language) {
        return render(tool, ToolTextResolver.hostOnly(language));
    }

    static String render(ToolDescriptor tool, ToolTextResolver toolText) {
        LanguageManager language = toolText.language();
        boolean chinese = language != null && language.activeCode().startsWith("zh");
        String name = toolText.name(tool);
        String description = toolText.description(tool);
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(name).append("\n\n");
        markdown.append('`').append(tool.id()).append("` · ")
                .append(toolText.category(tool.category())).append(" · v")
                .append(tool.version()).append("\n\n");

        markdown.append(chinese ? "## 用途与适用场景\n\n" : "## Purpose and use cases\n\n");
        markdown.append(description).append(chinese
                ? " 该工具适合在 OpenTools 项目中执行可复现、可追踪的单步骤分析；结果会与输入哈希、参数和运行日志一起保存。\n\n"
                : " Use this tool for a reproducible, traceable single-step analysis in an OpenTools project. Results are stored with input hashes, normalized parameters, and run logs.\n\n");

        markdown.append(chinese ? "## 输入格式\n\n" : "## Input formats\n\n");
        markdown.append(formats(tool.inputFormats(), chinese, "该工具不声明固定输入格式，请在参数说明中确认输入。"))
                .append("\n\n");
        markdown.append(chinese ? "## 输出格式\n\n" : "## Output formats\n\n");
        markdown.append(formats(tool.outputFormats(), chinese, "输出由参数与运行目录决定。"))
                .append("\n\n");

        markdown.append(chinese ? "## 关键参数、默认值与范围\n\n" : "## Key parameters, defaults, and ranges\n\n");
        if (tool.parameters().isEmpty()) {
            markdown.append(chinese ? "该工具没有参数。\n\n" : "This tool has no parameters.\n\n");
        } else {
            for (ToolParameter parameter : tool.parameters()) {
                markdown.append("### ")
                        .append(toolText.parameterTitle(tool, parameter))
                        .append(" (`").append(parameter.id()).append("`)\n\n");
                markdown.append("- ").append(chinese ? "类型：" : "Type: ")
                        .append('`').append(parameter.type()).append("`\n");
                markdown.append("- ").append(chinese ? "必填：" : "Required: ")
                        .append(parameter.required() ? (chinese ? "是" : "yes") : (chinese ? "否" : "no"))
                        .append('\n');
                markdown.append("- ").append(chinese ? "默认值：" : "Default: ")
                        .append(defaultValue(parameter, chinese)).append('\n');
                markdown.append("- ").append(chinese ? "范围/选项：" : "Range/options: ")
                        .append(constraints(parameter, chinese)).append('\n');
                markdown.append("- ").append(chinese ? "说明：" : "Description: ")
                        .append(singleLine(toolText.parameterDescription(tool, parameter))).append("\n\n");
            }
        }

        markdown.append(chinese ? "## 典型示例\n\n" : "## Example\n\n");
        markdown.append("```powershell\n").append(example(tool, chinese)).append("\n```\n\n");
        if (chinese) {
            markdown.append("将示例输入替换为工作区中的真实路径；建议先运行 **校验**，再提交作业。\n\n");
        } else {
            markdown.append("Replace the placeholder paths with files in your workspace and validate before submitting the job.\n\n");
        }

        markdown.append(chinese ? "## 注意事项和失败边界\n\n" : "## Notes and failure boundaries\n\n");
        for (String note : notes(tool, chinese)) {
            markdown.append("- ").append(note).append('\n');
        }
        markdown.append("- ")
                .append(chinese
                        ? "默认拒绝覆盖已有输出；自动化工作区不允许项目外路径。"
                        : "Existing outputs are not overwritten by default, and automation paths must remain inside an allowed workspace.")
                .append('\n');
        markdown.append('\n');
        markdown.append(chinese
                ? "[返回文档首页](opentools-doc:README.md)"
                : "[Back to documentation home](opentools-doc:README.md)")
                .append('\n');
        return markdown.toString();
    }

    private static String example(ToolDescriptor tool, boolean chinese) {
        StringBuilder command = new StringBuilder("opentools run ").append(tool.id());
        List<ToolParameter> values = new ArrayList<>(tool.parameters().stream()
                .filter(ToolParameter::required)
                .toList());
        boolean hasFile = values.stream().anyMatch(parameter -> parameter.type().name().equals("FILE"));
        for (ToolParameter parameter : values) {
            command.append(" --param ").append(parameter.id()).append('=')
                    .append(exampleValue(parameter, chinese));
        }
        if (!values.isEmpty() && !hasFile) {
            command.append(" --param input=C:\\data\\input.dat");
        }
        return command.toString();
    }

    private static String exampleValue(ToolParameter parameter, boolean chinese) {
        return switch (parameter.type()) {
            case FILE -> "C:\\data\\" + parameter.id() + ".dat";
            case FILES -> "C:\\data\\" + parameter.id() + "-1.dat,C:\\data\\"
                    + parameter.id() + "-2.dat";
            case OUTPUT_FILE -> "C:\\data\\output-" + parameter.id() + ".dat";
            case DIRECTORY -> "C:\\data\\" + parameter.id();
            case INTEGER -> String.valueOf(parameter.defaultValue() == null ? 1 : parameter.defaultValue());
            case DECIMAL -> String.valueOf(parameter.defaultValue() == null ? 1.0 : parameter.defaultValue());
            case BOOLEAN -> String.valueOf(parameter.defaultValue() == null || Boolean.parseBoolean(
                    String.valueOf(parameter.defaultValue())));
            case ENUM -> parameter.options().isEmpty()
                    ? "VALUE"
                    : String.valueOf(parameter.defaultValue() == null
                            ? parameter.options().getFirst()
                            : parameter.defaultValue());
            case TEXT -> chinese ? "文本值" : "value";
            case STRING -> parameter.defaultValue() == null
                    ? "value"
                    : String.valueOf(parameter.defaultValue());
        };
    }

    private static List<String> notes(ToolDescriptor tool, boolean chinese) {
        List<String> notes = new ArrayList<>();
        String category = tool.category().toLowerCase(Locale.ROOT);
        if (category.contains("sequence")) {
            notes.add(chinese
                    ? "FASTA/FASTQ 按记录读取；单条超大记录仍可能占用较多内存。坐标工具使用 1-based inclusive。"
                    : "FASTA/FASTQ are processed record by record; one very large record may still require substantial memory. Coordinate tools use 1-based inclusive positions.");
        } else if (category.contains("genome annotation") || category.equals("annotation")) {
            notes.add(chinese
                    ? "GFF3/GTF 使用 1-based inclusive，BED 使用 0-based half-open；基因组和注释的序列名、长度及方向必须兼容。"
                    : "GFF3/GTF uses 1-based inclusive coordinates while BED uses 0-based half-open coordinates. Sequence names, lengths, and strands must agree.");
        } else if (category.equals("table")) {
            notes.add(chinese
                    ? "输入应包含唯一表头。引号、跨行字段和列名不匹配会导致解析失败或校验错误。"
                    : "The input needs a unique header. Invalid quoting, multiline fields, or missing columns cause parse or validation failures.");
        } else if (category.equals("hts")) {
            notes.add(chinese
                    ? "CRAM 可能需要匹配的 reference；有索引的 BAM/CRAM 查询通常更快。VCF 索引要求序列字典和排序。"
                    : "CRAM may require a matching reference. Indexed BAM/CRAM queries are usually faster. VCF indexes require a sequence dictionary and sorted records.");
        } else if (category.equals("visualization")) {
            notes.add(chinese
                    ? "输入列名和数值类型必须匹配参数；超大矩阵可能进行有界显示抽样，但完整导出使用工具数据路径。"
                    : "Input column names and numeric types must match the parameters. Very large matrices may use bounded display sampling while complete exports use tool data.");
        } else if (category.equals("expression") || category.equals("enrichment")) {
            notes.add(chinese
                    ? "行 ID、样本列和数值解析必须完整；统计结果依赖输入注释版本和实验设计。"
                    : "Row IDs, sample columns, and numeric values must be valid. Statistical interpretation depends on annotation versions and study design.");
        } else if (category.contains("alignment") || category.contains("phylogeny")
                || category.contains("comparative")) {
            notes.add(chinese
                    ? "外部程序版本、内存、线程和超时会影响结果；运行前检查工具路径并保留完整日志。"
                    : "External tool versions, memory, threads, and timeouts affect results. Check tool paths before running and retain the complete log.");
        } else {
            notes.add(chinese
                    ? "输入格式、列名、坐标或外部资源不匹配时工具会在发布输出前失败。"
                    : "The tool fails before publishing outputs when formats, columns, coordinates, or external resources do not match.");
        }

        if (tool.id().startsWith("alignment.blast") || tool.id().equals("alignment.diamond")
                || tool.id().startsWith("alignment.hmmer") || tool.id().equals("alignment.muscle")
                || tool.id().equals("alignment.trim") || tool.id().equals("alignment.iqtree")
                || tool.id().equals("mcscan.run")) {
            notes.add(chinese
                    ? "该工具依赖外部程序。请使用便携包内固定版本，或通过显式路径、OPENTOOLS_* 环境变量或 PATH 提供兼容程序。"
                    : "This tool depends on an external executable. Use the pinned portable-package version, an explicit path, an OPENTOOLS_* variable, or a compatible executable on PATH.");
        }
        if (tool.id().contains("overlap") || tool.id().contains("transpose")
                || tool.id().contains("heatmap")) {
            notes.add(chinese
                    ? "AUTO/DISK 路径可能需要临时磁盘空间，并受 max* 安全上限约束；磁盘满或上限不足会以结构化错误失败。"
                    : "AUTO/DISK paths may require temporary disk space and are bounded by max* limits. Full disks or exceeded limits fail with structured errors.");
        }
        if (tool.parameters().stream().anyMatch(parameter -> parameter.id().equals("threads"))) {
            notes.add(chinese
                    ? "`threads=0` 是保守 AUTO；`1` 强制顺序路径，显式值受 Schema 上限约束。小输入不保证加速。"
                    : "`threads=0` is conservative AUTO; `1` forces the sequential path, and explicit values are bounded by the schema. Small inputs may not benefit.");
        }
        return notes;
    }

    private static String formats(List<String> formats, boolean chinese, String fallback) {
        if (formats.isEmpty()) {
            return chinese ? fallback : "The tool does not declare a fixed format; confirm the inputs in the parameter descriptions.";
        }
        return formats.stream().map(format -> "`" + format + "`")
                .reduce((left, right) -> left + (chinese ? "、" : ", ") + right)
                .orElse(fallback);
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

    private static String constraints(ToolParameter parameter, boolean chinese) {
        List<String> constraints = new ArrayList<>();
        if (parameter.min() != null || parameter.max() != null) {
            constraints.add((parameter.min() == null ? (chinese ? "不限" : "unbounded") : numeric(parameter.min()))
                    + " .. "
                    + (parameter.max() == null ? (chinese ? "不限" : "unbounded") : numeric(parameter.max())));
        }
        if (!parameter.options().isEmpty()) {
            constraints.add(String.join(", ", parameter.options()));
        }
        return constraints.isEmpty() ? (chinese ? "按类型校验" : "type validation") : String.join("; ", constraints);
    }

    private static String numeric(Double value) {
        return value == Math.rint(value) ? String.valueOf(value.longValue()) : String.valueOf(value);
    }

    private static String singleLine(String value) {
        return value.replace("\r", " ").replace("\n", " ").trim();
    }
}
