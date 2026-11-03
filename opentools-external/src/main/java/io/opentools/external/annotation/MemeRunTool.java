package io.opentools.external.annotation;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;
import io.opentools.external.ExternalRuntime;
import io.opentools.external.process.ExternalArguments;
import io.opentools.external.process.ExternalProcessException;
import io.opentools.external.process.ProcessResult;
import io.opentools.external.process.ProcessRunner;
import io.opentools.external.process.TemporaryFiles;
import io.opentools.external.tool.ExecutableLocator;
import io.opentools.external.tool.ExternalToolSpecs;
import io.opentools.external.tool.ToolStatus;
import io.opentools.formats.FormatToolSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MemeRunTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "meme.run",
            "MEME Motif Discovery",
            "Run MEME on a FASTA file, import motif XML, and write a motif summary table.",
            "Sequence",
            List.of("MEME", "motif discovery", "motif", "基序发现"),
            List.of("FASTA"),
            List.of("MEME XML", "TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input sequences", "DNA or protein FASTA."),
                    new ToolParameter("molecule", "Molecule type", "Select DNA or protein mode.", ParameterType.ENUM,
                            false, "DNA", List.of("DNA", "PROTEIN"), null, null, false, null),
                    ToolParameter.integer("motifs", "Motif count", "Maximum motifs to discover.", 10, 1, 100),
                    ToolParameter.integer("minMotifWidth", "Minimum motif width", "Minimum motif width.", 6, 2, 100),
                    ToolParameter.integer("maxMotifWidth", "Maximum motif width", "Maximum motif width.", 50, 2, 300),
                    ToolParameter.integer("threads", "Threads", "MEME worker threads.", 4, 1, 128),
                    ToolParameter.integer("timeoutMinutes", "Timeout minutes", "Maximum command runtime.", 60, 1, 1440),
                    ToolParameter.optionalFile("executablePath", "MEME executable", "Optional explicit executable path."),
                    ToolParameter.optionalOutputFile("output", "Output XML", "Leave blank to save meme.xml in the run directory."),
                    new ToolParameter("extraArgs", "Extra MEME arguments", "Quote-aware extra CLI arguments.",
                            ParameterType.TEXT, false, "", List.of(), null, null, true, null),
                    ToolParameter.bool("overwrite", "Overwrite outputs", "Allow replacing existing outputs.", false)
            )
    );

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request)
                .merge(FormatToolSupport.validateOutput(request, request.path("input")));
        if (request.integer("minMotifWidth") > request.integer("maxMotifWidth")) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "maxMotifWidth", "Maximum motif width must be at least the minimum width.",
                    "Raise maxMotifWidth or lower minMotifWidth.")));
        }
        try {
            ExternalArguments.parseRestricted(request.string("extraArgs"), ExternalArguments.RESERVED_OPTIONS);
        } catch (IllegalArgumentException ex) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "extraArgs", ex.getMessage(), "Remove the conflicting option.")));
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path output = FormatToolSupport.outputPath(request, context, "meme.xml");
        ToolStatus meme = new ExecutableLocator(ExternalRuntime.appHome()).resolve(
                ExternalToolSpecs.MEME, null, request.path("executablePath"));
        if (!meme.available() || meme.executable() == null) {
            throw new OpenToolException("EXTERNAL_TOOL_MISSING",
                    "MEME was not found. " + meme.diagnostic(),
                    "Install the MEME Suite, provide executablePath, or set OPENTOOLS_MEME.");
        }
        Path work = Files.createTempDirectory("opentools-meme-");
        try {
            Path input = work.resolve("sequences.fa");
            Files.copy(request.path("input"), input, StandardCopyOption.REPLACE_EXISTING);
            Path outDir = work.resolve("out");
            Files.createDirectories(outDir);
            List<String> command = new ArrayList<>();
            command.add(meme.executable().toString());
            command.add(input.getFileName().toString());
            command.addAll(List.of("-oc", outDir.getFileName().toString(), "-nostatus",
                    "-mod", "zoops",
                    "-nmotifs", Integer.toString(Math.max(1, request.integer("motifs"))),
                    "-minw", Integer.toString(request.integer("minMotifWidth")),
                    "-maxw", Integer.toString(request.integer("maxMotifWidth"))));
            command.add("PROTEIN".equalsIgnoreCase(request.string("molecule")) ? "-protein" : "-dna");
            command.addAll(ExternalArguments.parseRestricted(request.string("extraArgs"), ExternalArguments.RESERVED_OPTIONS));
            ProcessResult process = new ProcessRunner().run(
                    command, work, Map.of(),
                    Duration.ofMinutes(Math.max(1, request.integer("timeoutMinutes"))),
                    context.cancellationToken(), context::log);
            if (!process.succeeded()) {
                throw new ExternalProcessException("MEME_FAILED",
                        "MEME exited with code " + process.exitCode() + ": " + tail(process.output()),
                        "Inspect run.log and verify MEME is installed and the sequence alphabet is correct.");
            }
            Path xml = outDir.resolve("meme.xml");
            if (!Files.isRegularFile(xml)) {
                throw new ExternalProcessException("MEME_OUTPUT_MISSING",
                        "MEME did not create meme.xml.", "Check the MEME output directory and command syntax.");
            }
            FormatToolSupport.createParent(output);
            Files.copy(xml, output, StandardCopyOption.REPLACE_EXISTING);
            List<MemeResults.Motif> motifs = MemeResults.readMotifs(output);
            Path table = output.resolveSibling(stripXml(output.getFileName().toString()) + ".motifs.tsv");
            FormatToolSupport.writeAtomically(table, writer -> {
                writer.write("motif	name	width	sites	evalue\n");
                for (MemeResults.Motif motif : motifs) {
                    writer.write(motif.id());
                    writer.write('\t'); writer.write(nullToEmpty(motif.name()));
                    writer.write('\t'); writer.write(Integer.toString(motif.width()));
                    writer.write('\t'); writer.write(Integer.toString(motif.sites()));
                    writer.write('\t'); writer.write(Double.toString(motif.evalue()));
                    writer.newLine();
                }
            });
            context.progress(1.0, "MEME motif discovery completed");
            long sites = motifs.stream().mapToLong(MemeResults.Motif::sites).sum();
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("motifs", motifs.size());
            metrics.put("sites", sites);
            metrics.put("memeVersion", meme.version());
            metrics.put("molecule", request.string("molecule"));
            return new ToolResult(
                    "MEME discovered " + motifs.size() + " motifs",
                    List.of(motifs.size()),
                    "Using " + meme.version() + "; " + sites + " contributing sites.",
                    List.of(sites),
                    new ResultTable(List.of("Motif", "Width", "Sites", "E-value"),
                            motifs.stream().limit(50).map(motif -> List.of(
                                    motif.id(), Integer.toString(motif.width()),
                                    Integer.toString(motif.sites()), Double.toString(motif.evalue())
                            )).toList()),
                    List.of(new OutputArtifact(output, "MEME XML", "application/xml"),
                            new OutputArtifact(table, "Motif summary", "text/tab-separated-values")),
                    metrics
            );
        } finally {
            TemporaryFiles.deleteRecursively(work);
        }
    }

    private static String stripXml(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".xml")
                ? name.substring(0, name.length() - 4) : name;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String tail(String output) {
        return output == null ? "" : output.length() <= 3000 ? output : output.substring(output.length() - 3000);
    }
}
