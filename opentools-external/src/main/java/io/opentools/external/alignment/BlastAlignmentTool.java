package io.opentools.external.alignment;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.Compression;
import io.opentools.formats.FormatToolSupport;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlastAlignmentTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "alignment.blast.alignments",
            "BLAST Alignment Viewer Data",
            "Extract paired query/subject alignments, coordinates, E-values, and midlines from BLAST XML.",
            "Alignment",
            List.of("BLAST alignments", "pairwise alignment", "BLAST 对齐", "BLAST 比对查看"),
            List.of("BLAST XML", "XML.gz"),
            List.of("TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input BLAST XML", "BLAST XML output."),
                    ToolParameter.optionalOutputFile("output", "Output TSV",
                            "Leave blank to save in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output",
                            "Allow replacing an existing output.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        return validateParameters(request)
                .merge(FormatToolSupport.validateOutput(request, request.path("input")));
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path output = FormatToolSupport.outputPath(request, context, "blast-alignments.tsv");
        long[] counters = new long[2];
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try (InputStream stream = Compression.open(request.path("input"))) {
            FormatToolSupport.writeAtomically(output, writer -> {
                writer.write("query\tsubject\tq_start\tq_end\ts_start\ts_end\tevalue\tbit_score"
                        + "\talignment_length\tquery_alignment\tsubject_alignment\tmidline\n");
                XMLStreamReader xml = factory.createXMLStreamReader(stream);
                String queryDefinition = "";
                String queryId = "";
                String subjectAccession = "";
                String subjectId = "";
                Map<String, String> hsp = null;
                try {
                    while (xml.hasNext()) {
                        context.cancellationToken().throwIfCancelled();
                        int event = xml.next();
                        if (event == XMLStreamConstants.START_ELEMENT) {
                            String name = xml.getLocalName();
                            switch (name) {
                                case "Iteration" -> {
                                    queryDefinition = "";
                                    queryId = "";
                                }
                                case "Iteration_query-def" -> queryDefinition = xml.getElementText().trim();
                                case "Iteration_query-ID" -> queryId = xml.getElementText().trim();
                                case "Hit" -> {
                                    subjectAccession = "";
                                    subjectId = "";
                                }
                                case "Hit_accession" -> subjectAccession = xml.getElementText().trim();
                                case "Hit_id" -> subjectId = xml.getElementText().trim();
                                case "Hsp" -> hsp = new LinkedHashMap<>();
                                case "Hsp_query-from", "Hsp_query-to", "Hsp_hit-from", "Hsp_hit-to",
                                        "Hsp_evalue", "Hsp_bit-score", "Hsp_align-len",
                                        "Hsp_qseq", "Hsp_hseq", "Hsp_midline" -> {
                                    if (hsp != null) hsp.put(name, xml.getElementText().trim());
                                }
                                default -> { }
                            }
                        } else if (event == XMLStreamConstants.END_ELEMENT
                                && "Hsp".equals(xml.getLocalName()) && hsp != null) {
                            String query = queryDefinition.isBlank() ? queryId : queryDefinition.split("\\s+", 2)[0];
                            String subject = subjectAccession.isBlank() ? subjectId : subjectAccession;
                            writer.write(row(query, subject, hsp));
                            counters[0]++;
                            if (hsp.getOrDefault("Hsp_qseq", "").isBlank()
                                    || hsp.getOrDefault("Hsp_hseq", "").isBlank()) {
                                counters[1]++;
                            }
                            hsp = null;
                        }
                    }
                } finally {
                    xml.close();
                }
            });
        } catch (Exception ex) {
            if (ex instanceof OpenToolException openTool) throw openTool;
            throw new OpenToolException(
                    "BLAST_XML_INVALID",
                    "BLAST XML could not be read: " + ex.getMessage(),
                    "Use well-formed BLAST XML.",
                    ex
            );
        }
        if (counters[0] == 0) {
            throw new OpenToolException("BLAST_XML_NO_HSPS",
                    "No BLAST HSP records were found.", "Check that the input is BLAST XML.");
        }
        context.progress(1.0, "BLAST alignment extraction completed");
        return new ToolResult(
                "Extracted " + counters[0] + " BLAST alignments",
                List.of(counters[0]),
                counters[1] + " HSPs had no qseq/hseq sequence strings.",
                List.of(counters[1]),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("HSPs", Long.toString(counters[0])),
                        List.of("Without sequence strings", Long.toString(counters[1]))
                )),
                List.of(new OutputArtifact(output, "BLAST pairwise alignments",
                        "text/tab-separated-values")),
                Map.of("hspRecords", counters[0], "withoutSequence", counters[1])
        );
    }

    private static String row(String query, String subject, Map<String, String> hsp) {
        return escape(query) + "\t" + escape(subject) + "\t"
                + hsp.getOrDefault("Hsp_query-from", "") + "\t"
                + hsp.getOrDefault("Hsp_query-to", "") + "\t"
                + hsp.getOrDefault("Hsp_hit-from", "") + "\t"
                + hsp.getOrDefault("Hsp_hit-to", "") + "\t"
                + hsp.getOrDefault("Hsp_evalue", "") + "\t"
                + hsp.getOrDefault("Hsp_bit-score", "") + "\t"
                + hsp.getOrDefault("Hsp_align-len", "") + "\t"
                + escape(hsp.getOrDefault("Hsp_qseq", "")) + "\t"
                + escape(hsp.getOrDefault("Hsp_hseq", "")) + "\t"
                + escape(hsp.getOrDefault("Hsp_midline", "")) + "\n";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ');
    }
}
