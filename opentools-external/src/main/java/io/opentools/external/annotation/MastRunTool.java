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

public final class MastRunTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "mast.run",
            "MAST Motif Scanning",
            "Run MAST with a MEME XML motif file against FASTA sequences and import hit coordinates.",
            "Sequence",
            List.of("MAST", "motif scanning", "motif", "基序扫描"),
            List.of("MEME XML", "FASTA"),
            List.of("MAST XML", "TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("motifs", "MEME motif XML", "meme.xml from MEME or another MEME XML source."),
                    ToolParameter.requiredFile("sequences", "Sequences", "DNA or protein FASTA to scan."),
                    ToolParameter.integer("timeoutMinutes", "Timeout minutes", "Maximum command runtime.", 60, 1, 1440),
                    ToolParameter.optionalFile("executablePath", "MAST executable", "Optional explicit executable path."),
                    ToolParameter.optionalOutputFile("output", "Output XML", "Leave blank to save mast.xml in the run directory."),
                    new ToolParameter("extraArgs", "Extra MAST arguments", "Quote-aware extra CLI arguments.",
                            ParameterType.TEXT, false, "", List.of(), null, null, true, null),
                    ToolParameter.bool("overwrite", "Overwrite outputs", "Allow replacing existing outputs.", false)
            )
    );

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request)
                .merge(FormatToolSupport.validateOutput(request, request.path("sequences")));
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
        Path output = FormatToolSupport.outputPath(request, context, "mast.xml");
        ToolStatus mast = new ExecutableLocator(ExternalRuntime.appHome()).resolve(
                ExternalToolSpecs.MAST, null, request.path("executablePath"));
        if (!mast.available() || mast.executable() == null) {
            throw new OpenToolException("EXTERNAL_TOOL_MISSING",
                    "MAST was not found. " + mast.diagnostic(),
                    "Install the MEME Suite, provide executablePath, or set OPENTOOLS_MAST.");
        }
        Path work = Files.createTempDirectory("opentools-mast-");
        try {
            Path motifs = work.resolve("motifs.xml");
            Path sequences = work.resolve("sequences.fa");
            Files.copy(request.path("motifs"), motifs, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(request.path("sequences"), sequences, StandardCopyOption.REPLACE_EXISTING);
            Path outDir = work.resolve("out");
            Files.createDirectories(outDir);
            MemeRuntimeSupport.copyTemplateIfAvailable(mast.executable(), work);
            List<String> command = new ArrayList<>();
            command.add(mast.executable().toString());
            command.add(motifs.getFileName().toString());
            command.add(sequences.getFileName().toString());
            command.addAll(List.of("-oc", outDir.getFileName().toString(), "-nostatus"));
            command.addAll(ExternalArguments.parseRestricted(request.string("extraArgs"), ExternalArguments.RESERVED_OPTIONS));
            ProcessResult process = new ProcessRunner().run(
                    command, work, Map.of(),
                    Duration.ofMinutes(Math.max(1, request.integer("timeoutMinutes"))),
                    context.cancellationToken(), context::log);
            Path xml = outDir.resolve("mast.xml");
            boolean usableXml = Files.isRegularFile(xml) && Files.size(xml) > 0;
            if (!usableXml) {
                context.log("MAST did not produce usable mast.xml; falling back to -hit_list.");
                List<String> fallback = new ArrayList<>(command);
                int outputOption = fallback.indexOf("-oc");
                if (outputOption >= 0 && outputOption + 1 < fallback.size()) {
                    fallback.remove(outputOption + 1);
                    fallback.remove(outputOption);
                }
                fallback.add("-hit_list");
                ProcessResult hitList = new ProcessRunner().run(
                        fallback, work, Map.of(),
                        Duration.ofMinutes(Math.max(1, request.integer("timeoutMinutes"))),
                        context.cancellationToken(), context::log);
                if (!hitList.succeeded()) {
                    throw new ExternalProcessException("MAST_FAILED",
                            "MAST exited with code " + process.exitCode() + " and the -hit_list fallback exited with "
                                    + hitList.exitCode() + ": " + tail(process.output() + hitList.output()),
                            "Inspect run.log and verify MAST is installed and the motif/sequence alphabets match.");
                }
                List<MemeResults.MastHit> fallbackHits = MastHitList.parse(hitList.output());
                MastHitList.writeXml(xml, fallbackHits);
                usableXml = true;
                context.log("MAST -hit_list fallback produced " + fallbackHits.size() + " hits.");
            }
            if (!process.succeeded()) {
                context.log("MAST reported exit code " + process.exitCode()
                        + " after writing mast.xml; continuing with the XML output.");
            }
            if (!usableXml) {
                throw new ExternalProcessException("MAST_OUTPUT_MISSING",
                        "MAST did not create mast.xml.", "Check the MAST output directory and command syntax.");
            }
            FormatToolSupport.createParent(output);
            Files.copy(xml, output, StandardCopyOption.REPLACE_EXISTING);
            List<MemeResults.MastHit> hits = MemeResults.readMastHits(output);
            Path table = output.resolveSibling(stripXml(output.getFileName().toString()) + ".hits.tsv");
            FormatToolSupport.writeAtomically(table, writer -> {
                writer.write("sequence\tmotif\tstart\tend\tstrand\tpvalue\tmatch\n");
                for (MemeResults.MastHit hit : hits) {
                    writer.write(hit.sequence());
                    writer.write('\t'); writer.write(hit.motif());
                    writer.write('\t'); writer.write(Integer.toString(hit.start()));
                    writer.write('\t'); writer.write(Integer.toString(hit.end()));
                    writer.write('\t'); writer.write(hit.strand());
                    writer.write('\t'); writer.write(Double.toString(hit.pvalue()));
                    writer.write('\t'); writer.write(hit.match());
                    writer.newLine();
                }
            });
            context.progress(1.0, "MAST motif scanning completed");
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("hits", hits.size());
            metrics.put("sequences", hits.stream().map(MemeResults.MastHit::sequence).distinct().count());
            metrics.put("mastVersion", mast.version());
            return new ToolResult(
                    "MAST found " + hits.size() + " motif hits",
                    List.of(hits.size()),
                    "Using " + mast.version() + "; " + metrics.get("sequences") + " sequences with hits.",
                    List.of(metrics.get("sequences")),
                    new ResultTable(List.of("Sequence", "Motif", "Start", "End", "P-value"),
                            hits.stream().limit(50).map(hit -> List.of(
                                    hit.sequence(), hit.motif(), Integer.toString(hit.start()),
                                    Integer.toString(hit.end()), Double.toString(hit.pvalue())
                            )).toList()),
                    List.of(new OutputArtifact(output, "MAST XML", "application/xml"),
                            new OutputArtifact(table, "Motif hits", "text/tab-separated-values")),
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

    private static String tail(String output) {
        return output == null ? "" : output.length() <= 3000 ? output : output.substring(output.length() - 3000);
    }
}
