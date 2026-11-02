package io.opentools.external.alignment;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.Compression;
import io.opentools.formats.FormatToolSupport;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedWriter;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlastXmlToTableTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "alignment.blast.xml-to-table",
            "Convert BLAST XML to Table",
            "Stream BLAST XML HSP records to a stable 12-column outfmt 6 style table.",
            "Alignment",
            List.of("blast xml", "xml to table", "BLAST XML 转表格"),
            List.of("BLAST XML", "XML.gz"),
            List.of("TSV"),
            "1.1.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input BLAST XML", "BLAST XML output."),
                    ToolParameter.optionalOutputFile("output", "Output table", "Leave blank to save in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)
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
        Path input = request.path("input");
        Path output = FormatToolSupport.outputPath(request, context, "blast-table.tsv");
        long[] hits = new long[1];
        FormatToolSupport.writeAtomically(output, writer -> {
            hits[0] = convert(input, writer, context);
            if (hits[0] == 0) {
                throw new OpenToolException(
                        "BLAST_XML_NO_HSPS",
                        "No BLAST HSP records were found.",
                        "Check that the input is BLAST XML containing at least one Hsp."
                );
            }
        });
        int metricHits = Math.toIntExact(hits[0]);
        context.progress(1.0, "BLAST XML conversion completed");
        return new ToolResult(
                "Converted " + hits[0] + " BLAST HSP records", List.of(metricHits),
                "Output uses 12 columns compatible with BLAST outfmt 6.", List.of(),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("HSP records", Long.toString(metricHits))
                )),
                List.of(new OutputArtifact(output, "BLAST tabular hits", "text/tab-separated-values")),
                Map.of("hits", metricHits, "parserMemory", "streaming-stax")
        );
    }

    static long convert(Path input, BufferedWriter writer, ToolExecutionContext context) throws Exception {
        XMLInputFactory factory = secureFactory();
        long count = 0;
        String queryDefinition = "";
        String queryId = "";
        boolean queryDefinitionSeen = false;
        String subjectAccession = "";
        String subjectId = "";
        Map<String, String> hsp = null;

        try (InputStream stream = new RejectingDoctypeInputStream(Compression.open(input))) {
            XMLStreamReader xml = null;
            try {
                xml = factory.createXMLStreamReader(stream);
                while (xml.hasNext()) {
                    context.cancellationToken().throwIfCancelled();
                    int event = xml.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String name = xml.getLocalName();
                        switch (name) {
                            case "Iteration" -> {
                                queryDefinition = "";
                                queryId = "";
                                queryDefinitionSeen = false;
                            }
                            case "Iteration_query-def" -> {
                                queryDefinition = xml.getElementText().trim();
                                queryDefinitionSeen = !queryDefinition.isBlank();
                            }
                            case "Iteration_query-ID" -> queryId = xml.getElementText().trim();
                            case "Hit" -> {
                                subjectAccession = "";
                                subjectId = "";
                            }
                            case "Hit_accession" -> subjectAccession = xml.getElementText().trim();
                            case "Hit_id" -> subjectId = xml.getElementText().trim();
                            case "Hsp" -> hsp = new LinkedHashMap<>();
                            case "Hsp_identity", "Hsp_align-len", "Hsp_gaps",
                                    "Hsp_query-from", "Hsp_query-to", "Hsp_hit-from", "Hsp_hit-to",
                                    "Hsp_evalue", "Hsp_bit-score" -> {
                                if (hsp != null) {
                                    hsp.put(name, xml.getElementText().trim());
                                }
                            }
                            default -> {
                                // Other BLAST XML elements are intentionally ignored.
                            }
                        }
                    } else if (event == XMLStreamConstants.END_ELEMENT
                            && "Hsp".equals(xml.getLocalName()) && hsp != null) {
                        String query = queryDefinitionSeen ? queryDefinition : queryId;
                        query = query.isBlank() ? "" : query.split("\\s+", 2)[0];
                        String subject = usable(subjectAccession) ? subjectAccession : subjectId;
                        writeTable(writer, hit(query, subject, hsp));
                        count++;
                        hsp = null;
                    }
                }
            } finally {
                if (xml != null) {
                    xml.close();
                }
            }
        } catch (XMLStreamException ex) {
            throw new OpenToolException(
                    "BLAST_XML_INVALID",
                    "BLAST XML could not be parsed: " + ex.getMessage(),
                    "Use well-formed BLAST XML without DTD or external entities.",
                    ex
            );
        }
        return count;
    }

    private static XMLInputFactory secureFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        try {
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        } catch (IllegalArgumentException ignored) {
            // The two mandatory StAX properties above are supported by every JAXP provider.
        }
        return factory;
    }

    private static Hit hit(String query, String subject, Map<String, String> hsp) {
        double identity = number(hsp.get("Hsp_identity"));
        double alignLength = number(hsp.get("Hsp_align-len"));
        int gaps = (int) number(hsp.get("Hsp_gaps"));
        long length = Math.round(alignLength);
        return new Hit(
                query,
                subject,
                alignLength == 0 ? 0 : identity * 100.0 / alignLength,
                length,
                length - Math.round(identity),
                gaps,
                Math.round(number(hsp.get("Hsp_query-from"))),
                Math.round(number(hsp.get("Hsp_query-to"))),
                Math.round(number(hsp.get("Hsp_hit-from"))),
                Math.round(number(hsp.get("Hsp_hit-to"))),
                number(hsp.get("Hsp_evalue")),
                number(hsp.get("Hsp_bit-score"))
        );
    }

    private static void writeTable(BufferedWriter writer, Hit hit) throws Exception {
        writer.write(hit.query());
        writer.write('\t');
        writer.write(hit.subject());
        writer.write('\t');
        writer.write(Double.toString(hit.percentIdentity()));
        writer.write('\t');
        writer.write(Long.toString(hit.alignmentLength()));
        writer.write('\t');
        writer.write(Long.toString(hit.mismatches()));
        writer.write('\t');
        writer.write(Long.toString(hit.gapOpenings()));
        writer.write('\t');
        writer.write(Long.toString(hit.queryStart()));
        writer.write('\t');
        writer.write(Long.toString(hit.queryEnd()));
        writer.write('\t');
        writer.write(Long.toString(hit.subjectStart()));
        writer.write('\t');
        writer.write(Long.toString(hit.subjectEnd()));
        writer.write('\t');
        writer.write(Double.toString(hit.evalue()));
        writer.write('\t');
        writer.write(Double.toString(hit.bitscore()));
        writer.newLine();
    }

    private static boolean usable(String value) {
        return value != null && !value.isBlank() && !"null".equals(value);
    }

    private static double number(String value) {
        try {
            return Double.parseDouble(value == null ? "" : value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static final class RejectingDoctypeInputStream extends FilterInputStream {
        private static final byte[] DOCTYPE = "<!DOCTYPE".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        private int matched;

        private RejectingDoctypeInputStream(InputStream input) {
            super(input);
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                scan(value);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            for (int index = 0; index < read; index++) {
                scan(buffer[offset + index] & 0xff);
            }
            return read;
        }

        private void scan(int value) throws IOException {
            int normalized = value >= 'a' && value <= 'z' ? value - ('a' - 'A') : value;
            if (normalized == DOCTYPE[matched]) {
                matched++;
                if (matched == DOCTYPE.length) {
                    throw new IOException("DOCTYPE declarations are prohibited");
                }
            } else {
                matched = normalized == '<' ? 1 : 0;
            }
        }
    }

    private record Hit(
            String query,
            String subject,
            double percentIdentity,
            long alignmentLength,
            long mismatches,
            long gapOpenings,
            long queryStart,
            long queryEnd,
            long subjectStart,
            long subjectEnd,
            double evalue,
            double bitscore
    ) {
    }
}
