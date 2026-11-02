package io.opentools.external.alignment;

import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolRequest;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class BlastLocalDelegation {
    private BlastLocalDelegation() {
    }

    static ToolResult run(ToolRequest request, ToolExecutionContext context, Path preparedQuery, Path output) throws Exception {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("query", preparedQuery.toString());
        parameters.put("subject", request.path("subject").toString());
        parameters.put("program", request.string("program"));
        parameters.put("evalue", request.value("evalue"));
        parameters.put("maxTargetSeqs", request.value("maxTargetSeqs"));
        parameters.put("threads", request.value("threads"));
        parameters.put("timeoutMinutes", request.value("timeoutMinutes"));
        parameters.put("output", output.toString());
        if (request.path("blastBin") != null) {
            parameters.put("blastBin", request.path("blastBin").toString());
        }
        parameters.put("cacheEnabled", request.value("cacheEnabled") == null
                ? true : request.bool("cacheEnabled"));
        parameters.put("refreshDatabase", request.value("refreshDatabase") != null
                && request.bool("refreshDatabase"));
        if (request.path("cacheDirectory") != null) {
            parameters.put("cacheDirectory", request.path("cacheDirectory").toString());
        }
        parameters.put("cacheMaxMegabytes", request.value("cacheMaxMegabytes") == null
                ? BlastDatabaseCache.DEFAULT_MAX_MEGABYTES
                : request.integer("cacheMaxMegabytes"));
        parameters.put("cacheMaxEntries", request.value("cacheMaxEntries") == null
                ? BlastDatabaseCache.DEFAULT_MAX_ENTRIES
                : request.integer("cacheMaxEntries"));
        parameters.put("extraArgs", request.string("extraArgs"));
        parameters.put("overwrite", true);
        return new BlastLocalTool().execute(
                new ToolRequest("alignment.blast.local", parameters),
                context
        );
    }
}
