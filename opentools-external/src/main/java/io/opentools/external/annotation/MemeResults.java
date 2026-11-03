package io.opentools.external.annotation;

import io.opentools.api.tool.OpenToolException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads MEME and MAST XML results into small immutable records.
 */
public final class MemeResults {
    private MemeResults() {
    }

    public record Motif(String id, String name, int width, int sites, double evalue) {
    }

    public record MastHit(
            String sequence,
            String motif,
            int start,
            int end,
            String strand,
            double pvalue,
            String match
    ) {
    }

    public static List<Motif> readMotifs(Path xml) {
        Document document = parse(xml);
        NodeList nodes = document.getElementsByTagName("motif");
        List<Motif> motifs = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String id = element.getAttribute("id");
            if (id.isBlank()) {
                continue;
            }
            motifs.add(new Motif(
                    id,
                    element.getAttribute("name"),
                    integer(element.getAttribute("width")),
                    integer(element.getAttribute("sites")),
                    decimal(element.getAttribute("evalue"))
            ));
        }
        return List.copyOf(motifs);
    }

    public static List<MastHit> readMastHits(Path xml) {
        Document document = parse(xml);
        NodeList hits = document.getElementsByTagName("hit");
        List<MastHit> result = new ArrayList<>();
        for (int i = 0; i < hits.getLength(); i++) {
            Element hit = (Element) hits.item(i);
            Node segment = hit.getParentNode();
            Node sequence = segment == null ? null : segment.getParentNode();
            String sequenceId = sequence instanceof Element element
                    ? element.getAttribute("id") : "";
            String motif = !hit.getAttribute("motiff").isBlank()
                    ? hit.getAttribute("motiff") : hit.getAttribute("motif");
            if (motif.isBlank()) {
                continue;
            }
            int position = integer(hit.getAttribute("position"));
            String match = hit.getAttribute("match");
            int start = position + 1;
            int end = match == null || match.isBlank() ? start : start + match.length() - 1;
            result.add(new MastHit(
                    sequenceId,
                    motif,
                    start,
                    end,
                    hit.getAttribute("strand"),
                    decimal(hit.getAttribute("pvalue")),
                    match
            ));
        }
        return List.copyOf(result);
    }

    private static Document parse(Path xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(xml.toFile());
        } catch (Exception ex) {
            throw new OpenToolException(
                    "MEME_XML_INVALID",
                    "Could not parse MEME/MAST XML: " + ex.getMessage(),
                    "Choose a valid meme.xml or mast.xml produced by the MEME Suite."
            );
        }
    }

    private static int integer(String value) {
        try {
            return value == null || value.isBlank() ? 0 : Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static double decimal(String value) {
        try {
            return value == null || value.isBlank() ? Double.NaN : Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }
}
