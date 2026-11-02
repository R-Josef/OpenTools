import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentools.analysis.BuiltInAnalysis;
import io.opentools.core.job.JobRecord;
import io.opentools.core.job.JobStatus;
import io.opentools.core.job.SqliteJobStore;
import io.opentools.core.job.ToolExecutionService;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.api.tool.ToolRequest;
import io.opentools.core.workspace.Project;
import io.opentools.core.workspace.ProjectService;
import io.opentools.formats.BuiltInTools;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TbtoolsCompareHarness {
    private TbtoolsCompareHarness() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 5 || args.length > 7) {
            throw new IllegalArgumentException(
                    "Usage: project toolId input outputPrefix iterations [transposeMode] [threads]");
        }
        Path projectRoot = Path.of(args[0]).toAbsolutePath().normalize();
        String toolId = args[1];
        Path input = Path.of(args[2]).toAbsolutePath().normalize();
        Path outputPrefix = Path.of(args[3]).toAbsolutePath().normalize();
        int iterations = Integer.parseInt(args[4]);
        String transposeMode = args.length >= 6 ? args[5] : "DISK";
        Integer threads = args.length == 7 ? Integer.parseInt(args[6]) : null;
        if (iterations < 2) {
            throw new IllegalArgumentException("iterations must include a warmup and >= 1 measured run");
        }

        ProjectService projectService = new ProjectService();
        Project project = Files.isRegularFile(projectRoot.resolve("project.json"))
                ? projectService.open(projectRoot)
                : projectService.create(projectRoot, projectRoot.getFileName().toString());
        ToolRegistry registry = new ToolRegistry();
        BuiltInTools.register(registry);
        BuiltInAnalysis.register(registry);
        SqliteJobStore store = new SqliteJobStore(project.databasePath());
        store.initialize();

        List<Long> measured = new ArrayList<>();
        try (ToolExecutionService execution = new ToolExecutionService(registry, store)) {
            for (int iteration = 0; iteration < iterations; iteration++) {
                Path output = Path.of(outputPrefix + "-" + iteration + ".tsv");
                Map<String, Object> parameters = parameters(
                        toolId, input, output, transposeMode, threads);
                long start = System.nanoTime();
                JobRecord job = execution.runAndWait(
                        project,
                        new ToolRequest(toolId, parameters)
                );
                long elapsed = (System.nanoTime() - start) / 1_000_000L;
                if (job.status() != JobStatus.SUCCEEDED) {
                    throw new IllegalStateException(
                            toolId + " failed: " + job.status() + " " + job.errorMessage());
                }
                if (iteration > 0) {
                    measured.add(elapsed);
                }
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("toolId", toolId);
        report.put("milliseconds", measured);
        System.out.println(new ObjectMapper().writeValueAsString(report));
    }

    private static Map<String, Object> parameters(
            String toolId,
            Path input,
            Path output,
            String transposeMode,
            Integer threads
    ) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("input", input.toString());
        parameters.put("output", output.toString());
        parameters.put("overwrite", true);
        if (threads != null) {
            parameters.put("threads", threads);
        }
        if ("table.transpose".equals(toolId)) {
            parameters.put("transposeMode", transposeMode);
            parameters.put("maxTempMegabytes", 8_192);
        }
        return parameters;
    }
}
