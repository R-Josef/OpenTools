package io.opentools.external.alignment;

import io.opentools.api.tool.OpenToolException;
import io.opentools.formats.Compression;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses BLAST XML once for tabular and pairwise-alignment outputs. */
final class BlastXmlResult {
    private BlastXmlResult() {
    }

    record Alignment(
            String query,
            String subject,
            double percentIdentity,
            long alignmentLength,
            long mismatches,
            long gapOpenings,
            long qStart,
            long qEnd,
            long sStart,
            long sEnd,
            double evalue,
            double bitScore,
            long queryLength,
            long subjectLength,
            String queryAlignment,
            String subjectAlignment,
            String midline
    ) {
    }

    static List<Alignment> read(Path xml) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        List<Alignment> alignments = new ArrayList<>();
        try (InputStream stream = Compression.open(xml)) {
            XMLStreamReader reader = factory.createXMLStreamReader(stream);
            String queryDefinition = "";
            String queryId = "";
            long queryLength = 0;
            String hitId = "";
            String hitAccession = "";
            long subjectLength = 0;
            Map<String, String> hsp = null;
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String name = reader.getLocalName();
                        switch (name) {
                            case "Iteration" -> {
                                queryDefinition = "";
                                queryId = "";
                                queryLength = 0;
                            }
                            case "Iteration_query-def" -> queryDefinition = reader.getElementText().trim();
                            case "Iteration_query-ID" -> queryId = reader.getElementText().trim();
                            case "Iteration_query-len" -> queryLength = longValue(reader.getElementText());
                            case "Hit" -> {
                                hitId = "";
                                hitAccession = "";
                                subjectLength = 0;
                            }
                            case "Hit_id" -> hitId = reader.getElementText().trim();
                            case "Hit_accession" -> hitAccession = reader.getElementText().trim();
                            case "Hit_len" -> subjectLength = longValue(reader.getElementText());
                            case "Hsp" -> hsp = new LinkedHashMap<>();
                            case "Hsp_query-from", "Hsp_query-to", "Hsp_hit-from", "Hsp_hit-to",
                                    "Hsp_evalue", "Hsp_bit-score", "Hsp_align-len",
                                    "Hsp_identity", "Hsp_mismatches", "Hsp_gapopen",
                                    "Hsp_qseq", "Hsp_hseq", "Hsp_midline" -> {
                                if (hsp != null) hsp.put(name, reader.getElementText().trim());
                            }
                            default -> { }
                        }
                    } else if (event == XMLStreamConstants.END_ELEMENT
                            && "Hsp".equals(reader.getLocalName()) && hsp != null) {
                        String query = queryDefinition.isBlank()
                                ? queryId : queryDefinition.split("\\s+", 2)[0];
                        String subject = hitAccession.isBlank() ? hitId : hitAccession;
                        long length = longValue(hsp.get("Hsp_align-len"));
                        long identity = longValue(hsp.get("Hsp_identity"));
                        alignments.add(new Alignment(
                                query,
                                subject,
                                length == 0 ? 0 : identity * 100.0 / length,
                                length,
                                longValue(hsp.get("Hsp_mismatches")),
                                longValue(hsp.get("Hsp_gapopen")),
                                longValue(hsp.get("Hsp_query-from")),
                                longValue(hsp.get("Hsp_query-to")),
                                longValue(hsp.get("Hsp_hit-from")),
                                longValue(hsp.get("Hsp_hit-to")),
                                doubleValue(hsp.get("Hsp_evalue")),
                                doubleValue(hsp.get("Hsp_bit-score")),
                                queryLength,
                                subjectLength,
                                hsp.getOrDefault("Hsp_qseq", ""),
                                hsp.getOrDefault("Hsp_hseq", ""),
                                hsp.getOrDefault("Hsp_midline", "")
                        ));
                        hsp = null;
                    }
                }
            } finally {
                reader.close();
            }
        }
        return alignments;
    }

    static void writeTabular(Path output, List<Alignment> alignments) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (Alignment hit : alignments) {
                writer.write(escape(hit.query()));
                writer.write('\t');
                writer.write(escape(hit.subject()));
                writer.write('\t');
                writer.write(format(hit.percentIdentity()));
                writer.write('\t');
                writer.write(Long.toString(hit.alignmentLength()));
                writer.write('\t');
                writer.write(Long.toString(hit.mismatches()));
                writer.write('\t');
                writer.write(Long.toString(hit.gapOpenings()));
                writer.write('\t');
                writer.write(Long.toString(hit.qStart()));
                writer.write('\t');
                writer.write(Long.toString(hit.qEnd()));
                writer.write('\t');
                writer.write(Long.toString(hit.sStart()));
                writer.write('\t');
                writer.write(Long.toString(hit.sEnd()));
                writer.write('\t');
                writer.write(format(hit.evalue()));
                writer.write('\t');
                writer.write(format(hit.bitScore()));
                writer.write('\t');
                writer.write(Long.toString(hit.queryLength()));
                writer.write('\t');
                writer.write(Long.toString(hit.subjectLength()));
                writer.newLine();
            }
        }
    }

    static void writeAlignments(Path output, List<Alignment> alignments) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("query\tsubject\tq_start\tq_end\ts_start\ts_end\tevalue\tbit_score"
                    + "\talignment_length\tquery_alignment\tsubject_alignment\tmidline\n");
            for (Alignment hit : alignments) {
                writer.write(escape(hit.query()));
                writer.write('\t');
                writer.write(escape(hit.subject()));
                writer.write('\t');
                writer.write(Long.toString(hit.qStart()));
                writer.write('\t');
                writer.write(Long.toString(hit.qEnd()));
                writer.write('\t');
                writer.write(Long.toString(hit.sStart()));
                writer.write('\t');
                writer.write(Long.toString(hit.sEnd()));
                writer.write('\t');
                writer.write(format(hit.evalue()));
                writer.write('\t');
                writer.write(format(hit.bitScore()));
                writer.write('\t');
                writer.write(Long.toString(hit.alignmentLength()));
                writer.write('\t');
                writer.write(escape(hit.queryAlignment()));
                writer.write('\t');
                writer.write(escape(hit.subjectAlignment()));
                writer.write('\t');
                writer.write(escape(hit.midline()));
                writer.newLine();
            }
        }
    }

    private static long longValue(String value) {
        try {
            return value == null || value.isBlank() ? 0 : Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static double doubleValue(String value) {
        try {
            return value == null || value.isBlank() ? Double.NaN : Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    private static String format(double value) {
        return Double.isNaN(value) ? "" : String.format(java.util.Locale.ROOT, "%.10g", value);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ');
    }
}
