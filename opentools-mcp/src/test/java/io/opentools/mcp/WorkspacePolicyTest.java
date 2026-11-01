package io.opentools.mcp;

import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorkspacePolicyTest {
    @TempDir
    Path tempDir;

    @Test
    void normalizesRelativePathsAndRejectsEscapesAndUnknownFields() {
        Path project = tempDir.resolve("project");
        WorkspacePolicy policy = new WorkspacePolicy(project, List.of());
        ToolDescriptor descriptor = new ToolDescriptor(
                "test.paths",
                "Paths",
                "Path policy",
                "Test",
                List.of(),
                List.of(),
                List.of(),
                "1.0.0",
                List.of(
                        ToolParameter.requiredFile("input", "Input", "Input"),
                        ToolParameter.optionalOutputFile("output", "Output", "Output"),
                        ToolParameter.bool("overwrite", "Overwrite", "Overwrite", false)
                )
        );

        WorkspacePolicy.NormalizedRequest valid = policy.normalize(
                descriptor,
                java.util.Map.of("input", "data/a.fa")
        );
        assertTrue(valid.valid());
        assertTrue(valid.request().string("input").startsWith(project.toAbsolutePath().toString()));

        WorkspacePolicy.NormalizedRequest unsafe = policy.normalize(
                descriptor,
                java.util.Map.of(
                        "input", tempDir.resolve("outside.fa").toString(),
                        "overwrite", true,
                        "unknown", "x"
                )
        );
        assertFalse(unsafe.valid());
        String issues = unsafe.issues().toString();
        assertTrue(issues.contains("PATH_OUTSIDE_WORKSPACE"));
        assertTrue(issues.contains("OVERWRITE_NOT_ALLOWED"));
        assertTrue(issues.contains("UNKNOWN_PARAMETER"));
    }
}
