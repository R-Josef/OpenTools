package io.opentools.formats.fasta;

import io.opentools.api.tool.OpenToolException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * NCBI genetic-code definitions that can be represented by an unambiguous
 * codon-to-amino-acid table.
 */
public final class GeneticCode {
    private static final List<String> CODONS = codons();
    private static final String STANDARD_AMINO_ACIDS =
            "FFLLSSSSYY**CC*W"
                    + "LLLLPPPPHHQQRRRR"
                    + "IIIMTTTTNNKKSSRR"
                    + "VVVVAAAADDEEGGGG";
    private static final Map<String, Definition> DEFINITIONS = definitions();

    private GeneticCode() {
    }

    public static List<String> supportedCodes() {
        return List.of(
                "1", "2", "3", "4", "5", "6", "9", "10", "11", "12", "13", "14",
                "15", "16", "21", "22", "23", "24", "25", "26", "29", "30", "31", "33"
        );
    }

    public static Map<String, Character> table(String code) {
        Definition definition = DEFINITIONS.get(normalize(code));
        if (definition == null) {
            throw new OpenToolException(
                    "GENETIC_CODE_UNSUPPORTED",
                    "Unsupported genetic code: " + code,
                    "Choose one of: " + String.join(", ", supportedCodes()) + "."
            );
        }
        return definition.table();
    }

    public static Set<String> stopCodons(String code) {
        Definition definition = DEFINITIONS.get(normalize(code));
        if (definition == null) {
            table(code);
            throw new IllegalStateException();
        }
        return definition.stopCodons();
    }

    public static Character translate(String code, String codon) {
        return table(code).get(normalizeCodon(codon));
    }

    public static String name(String code) {
        Definition definition = DEFINITIONS.get(normalize(code));
        if (definition == null) {
            table(code);
            throw new IllegalStateException();
        }
        return definition.name();
    }

    private static Map<String, Definition> definitions() {
        List<Definition> definitions = List.of(
                definition("1", "Standard", ""),
                definition("2", "Vertebrate mitochondrial", "TGA:W,ATA:M,AGA:*,AGG:*"),
                definition("3", "Yeast mitochondrial",
                        "ATA:M,CTT:T,CTC:T,CTA:T,CTG:T,TGA:W"),
                definition("4", "Mold/protozoan/coelenterate mitochondrial", "TGA:W"),
                definition("5", "Invertebrate mitochondrial", "TGA:W,ATA:M,AGA:S,AGG:S"),
                definition("6", "Ciliate/dasycladacean/hexamita nuclear", "TAA:Q,TAG:Q"),
                definition("9", "Echinoderm/flatworm mitochondrial", "TGA:W,AAA:N,AGA:S,AGG:S"),
                definition("10", "Euplotid nuclear", "TGA:C"),
                definition("11", "Bacterial/archaeal/plastid", ""),
                definition("12", "Alternative yeast nuclear", "CTG:S"),
                definition("13", "Ascidian mitochondrial", "TGA:W,ATA:M"),
                definition("14", "Alternative flatworm mitochondrial",
                        "TAA:Y,ATA:M,AAA:N,TGA:W"),
                definition("15", "Blepharisma nuclear", "TAG:Q"),
                definition("16", "Chlorophycean mitochondrial", "TAG:L"),
                definition("21", "Trematode mitochondrial",
                        "TGA:W,ATA:M,AAA:N,AGA:S,AGG:S"),
                definition("22", "Scenedesmus obliquus mitochondrial", "TCA:*,TAG:L"),
                definition("23", "Thraustochytrium mitochondrial", "TTA:*"),
                definition("24", "Pterobranchia mitochondrial", "TGA:W,AGA:S,AGG:K"),
                definition("25", "Candidate division SR1/gracilibacteria", "TGA:G"),
                definition("26", "Pachysolen tannophilus nuclear", "CTG:A"),
                definition("29", "Mesodinium nuclear", "TAA:Y,TAG:Y"),
                definition("30", "Peritrich nuclear", "TAA:E,TAG:E"),
                definition("31", "Blastocrithidia nuclear", "TAA:E,TAG:E,TGA:W"),
                definition("33", "Cephalodiscidae mitochondrial",
                        "TAA:Y,TAG:L,TGA:W,AGA:S,AGG:K")
        );
        Map<String, Definition> result = new LinkedHashMap<>();
        definitions.forEach(definition -> result.put(definition.code(), definition));
        return Map.copyOf(result);
    }

    private static Definition definition(String code, String name, String changes) {
        Map<String, Character> table = new LinkedHashMap<>();
        for (int index = 0; index < CODONS.size(); index++) {
            table.put(CODONS.get(index), STANDARD_AMINO_ACIDS.charAt(index));
        }
        if (!changes.isBlank()) {
            for (String change : changes.split(",")) {
                String[] parts = change.split(":");
                table.put(parts[0], parts[1].charAt(0));
            }
        }
        Set<String> stops = new LinkedHashSet<>();
        table.forEach((codon, aminoAcid) -> {
            if (aminoAcid == '*') {
                stops.add(codon);
            }
        });
        return new Definition(code, name, Map.copyOf(table), Set.copyOf(stops));
    }

    private static List<String> codons() {
        List<String> result = new ArrayList<>(64);
        for (char first : "TCAG".toCharArray()) {
            for (char second : "TCAG".toCharArray()) {
                for (char third : "TCAG".toCharArray()) {
                    result.add(new String(new char[]{first, second, third}));
                }
            }
        }
        return List.copyOf(result);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "1" : value.trim();
    }

    private static String normalizeCodon(String codon) {
        return codon == null ? "" : codon.toUpperCase(Locale.ROOT).replace('U', 'T');
    }

    private record Definition(
            String code,
            String name,
            Map<String, Character> table,
            Set<String> stopCodons
    ) {
    }
}
