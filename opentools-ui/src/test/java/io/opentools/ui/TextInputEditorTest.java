package io.opentools.ui;

import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TextInputEditorTest {
    @Test
    void recognizesSmallTextInputs() {
        ToolDescriptor blast = descriptor(
                "alignment.blast.local",
                List.of("FASTA", "FASTA.gz"),
                ToolParameter.requiredFile("query", "Query FASTA", "Query sequences."),
                ToolParameter.requiredFile("subject", "Subject FASTA", "Subject sequences.")
        );

        assertTrue(TextInputEditor.supports(blast, blast.parameters().get(0)));
        assertTrue(TextInputEditor.supports(blast, blast.parameters().get(1)));
    }

    @Test
    void excludesExecutablesAndBinaryAlignmentFiles() {
        ToolDescriptor external = descriptor(
                "alignment.muscle",
                List.of("FASTA"),
                ToolParameter.optionalFile("executablePath", "MUSCLE executable", "Optional executable path.")
        );
        ToolDescriptor binary = descriptor(
                "hts.bam.stats",
                List.of("SAM", "BAM", "CRAM"),
                ToolParameter.requiredFile("input", "Input alignment", "SAM, BAM, or CRAM.")
        );

        assertFalse(TextInputEditor.supports(external, external.parameters().get(0)));
        assertFalse(TextInputEditor.supports(binary, binary.parameters().get(0)));
    }

    private static ToolDescriptor descriptor(String id, List<String> formats, ToolParameter... parameters) {
        return new ToolDescriptor(
                id,
                id,
                id,
                "Alignment",
                List.of(),
                formats,
                List.of(),
                "1.0.0",
                List.of(parameters)
        );
    }
}
