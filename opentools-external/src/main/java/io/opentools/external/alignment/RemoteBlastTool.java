package io.opentools.external.alignment;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.CancellationToken;
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
import io.opentools.core.runtime.AppVersion;
import io.opentools.formats.FormatToolSupport;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs a BLAST search through the NCBI BLAST URL API.
 *
 * <p>The query is uploaded only after the user explicitly sets
 * {@code allowUpload=true}. Results are downloaded as tabular output and parsed
 * with the same reader used for local BLAST tables.</p>
 */
public final class RemoteBlastTool extends AbstractOpenTool {
    private static final String DEFAULT_BASE_URL = "https://blast.ncbi.nlm.nih.gov/Blast.cgi";
    private static final long MAX_QUERY_BYTES = 10L * 1024 * 1024;
    private static final Pattern RID_PATTERN = Pattern.compile("RID\\s*=\\s*([A-Za-z0-9_-]+)");
    private static final Pattern RTOE_PATTERN = Pattern.compile("RTOE\\s*=\\s*(\\d+)");
    private static final Pattern STATUS_PATTERN = Pattern.compile("Status\\s*=\\s*([A-Za-z_]+)");

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "alignment.blast.remote",
            "Remote BLAST Search",
            "Upload a query to the NCBI BLAST URL API, poll for completion, and save tabular hits.",
            "Alignment",
            List.of("remote blast", "ncbi blast", "online blast", "远程 BLAST", "在线 BLAST"),
            List.of("FASTA"),
            List.of("TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("query", "Query FASTA",
                            "Nucleotide or protein query. The sequence is sent to NCBI only with allowUpload."),
                    new ToolParameter("program", "BLAST program", "NCBI BLAST program name.",
                            ParameterType.ENUM, true, "blastn",
                            List.of("blastn", "blastp", "blastx", "tblastn", "tblastx"),
                            null, null, false, null),
                    ToolParameter.string("database", "Database",
                            "NCBI database name such as nt, nr, refseq_protein, or swissprot.", "nt"),
                    ToolParameter.integer("maxHits", "Maximum hits",
                            "HITLIST_SIZE requested from NCBI.", 100, 1, 5000),
                    ToolParameter.bool("allowUpload", "Allow upload",
                            "Explicitly confirm that the query is sent to NCBI.", false),
                    ToolParameter.integer("timeoutSeconds", "Timeout",
                            "Total time to wait for the remote search.", 600, 5, 3600),
                    ToolParameter.integer("pollSeconds", "Poll interval",
                            "Seconds between status checks.", 5, 2, 60),
                    ToolParameter.string("baseUrl", "Service URL",
                            "NCBI BLAST URL API endpoint. HTTPS is required except for loopback testing.",
                            DEFAULT_BASE_URL),
                    ToolParameter.optionalOutputFile("output", "Output table",
                            "Leave blank to save in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output",
                            "Allow replacing an existing custom output file.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request);
        if (!request.bool("allowUpload")) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "allowUpload",
                    "Remote BLAST sends the query sequence to NCBI.",
                    "Set allowUpload=true to confirm, or use local BLAST when the data must stay on this computer."
            )));
        }
        if (!isAllowedBaseUrl(request.string("baseUrl"))) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "baseUrl",
                    "Service URL must be HTTPS, or HTTP on a loopback host for testing.",
                    "Use the default NCBI endpoint or a trusted HTTPS mirror."
            )));
        }
        return result.merge(FormatToolSupport.validateOutput(request, request.path("query")));
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        if (!request.bool("allowUpload")) {
            throw new OpenToolException(
                    "REMOTE_BLAST_UPLOAD_NOT_CONFIRMED",
                    "Remote BLAST requires explicit upload confirmation.",
                    "Set allowUpload=true only when the query may be sent to NCBI."
            );
        }
        Path query = request.path("query");
        if (Files.size(query) > MAX_QUERY_BYTES) {
            throw new OpenToolException(
                    "REMOTE_BLAST_QUERY_TOO_LARGE",
                    "Query FASTA exceeds the 10 MiB remote-search limit.",
                    "Split the query or run BLAST locally."
            );
        }
        String queryText = Files.readString(query, StandardCharsets.UTF_8);
        String program = request.string("program").toLowerCase(Locale.ROOT);
        String database = request.string("database").trim();
        int maxHits = request.integer("maxHits");
        int timeoutSeconds = request.integer("timeoutSeconds");
        int pollSeconds = request.integer("pollSeconds");
        String baseUrl = request.string("baseUrl").trim();
        Path output = FormatToolSupport.outputPath(request, context, "remote-blast.tsv");

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        long started = System.nanoTime();
        context.progress(0.05, "Submitting query to NCBI BLAST");
        String submitBody = post(client, baseUrl, form(Map.of(
                "CMD", "Put",
                "PROGRAM", program,
                "DATABASE", database,
                "QUERY", queryText,
                "HITLIST_SIZE", Integer.toString(maxHits),
                "TOOL", "OpenTools"
        )), "submit");
        String rid = firstGroup(RID_PATTERN, submitBody);
        if (rid == null) {
            throw new OpenToolException(
                    "REMOTE_BLAST_SUBMIT_FAILED",
                    "NCBI did not return a search RID: " + summarize(submitBody),
                    "Check the program and database names, then retry."
            );
        }
        int rtoe = parseRtoe(submitBody);
        context.log("Remote BLAST RID " + rid + (rtoe > 0 ? ", estimated wait " + rtoe + " s" : ""));
        context.progress(0.2, "Remote BLAST queued as " + rid);

        String table = awaitTable(client, baseUrl, rid, timeoutSeconds, pollSeconds, context);
        FormatToolSupport.writeAtomically(output, writer -> {
            writer.write(table);
            if (!table.endsWith("\n")) {
                writer.newLine();
            }
        });

        List<BlastTabularHit> hits = new BlastTabularReader().read(output);
        long elapsed = Math.max(0, Duration.ofNanos(System.nanoTime() - started).toSeconds());
        context.progress(1.0, "Remote BLAST completed");
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("rid", rid);
        metrics.put("program", program);
        metrics.put("database", database);
        metrics.put("hits", hits.size());
        metrics.put("elapsedSeconds", elapsed);
        return new ToolResult(
                "Remote BLAST returned " + hits.size() + " hits",
                List.of(hits.size()),
                "RID " + rid + "; " + program + " against " + database + "; "
                        + hits.size() + " HSP rows in " + elapsed + " s.",
                List.of(rid, program, database, hits.size()),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("RID", rid),
                        List.of("Program", program),
                        List.of("Database", database),
                        List.of("HSP rows", Long.toString(hits.size())),
                        List.of("Elapsed seconds", Long.toString(elapsed))
                )),
                List.of(new OutputArtifact(output, "Remote BLAST hits", "text/tab-separated-values")),
                metrics
        );
    }

    private static String post(HttpClient client, String baseUrl, String body, String operation) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "text/plain")
                .header("User-Agent", "OpenTools/" + AppVersion.current())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return send(client, request, operation);
    }

    private static String awaitTable(
            HttpClient client,
            String baseUrl,
            String rid,
            int timeoutSeconds,
            int pollSeconds,
            ToolExecutionContext context
    ) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(timeoutSeconds).toNanos();
        String lastStatus = "WAITING";
        while (System.nanoTime() < deadline) {
            sleepSeconds(pollSeconds, context.cancellationToken());
            context.cancellationToken().throwIfCancelled();
            String url = baseUrl + "?CMD=Get&RID=" + encode(rid)
                    + "&FORMAT_TYPE=Tabular&FORMAT_OBJECT=Search";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(90))
                    .header("Accept", "text/plain")
                    .header("User-Agent", "OpenTools/" + AppVersion.current())
                    .GET()
                    .build();
            String body = send(client, request, "poll");
            if (isTabular(body)) {
                return body;
            }
            if (looksLikeNoHits(body)) {
                return "# No hits returned by NCBI BLAST\n";
            }
            String status = firstGroup(STATUS_PATTERN, body);
            if (status != null) {
                lastStatus = status.toUpperCase(Locale.ROOT);
            }
            if ("WAITING".equals(lastStatus)) {
                continue;
            }
            if ("FAILED".equals(lastStatus) || "UNKNOWN".equals(lastStatus)) {
                throw new OpenToolException(
                        "REMOTE_BLAST_REMOTE_ERROR",
                        "NCBI BLAST reported status " + lastStatus + ": " + summarize(body),
                        "Check the query and database, then retry."
                );
            }
            if (body.toLowerCase(Locale.ROOT).contains("error")) {
                throw new OpenToolException(
                        "REMOTE_BLAST_REMOTE_ERROR",
                        "NCBI BLAST returned an error: " + summarize(body),
                        "Check the query, program, and database, then retry."
                );
            }
        }
        throw new OpenToolException(
                "REMOTE_BLAST_TIMEOUT",
                "Remote BLAST did not finish within " + timeoutSeconds + " seconds (last status "
                        + lastStatus + ").",
                "Increase timeoutSeconds, reduce maxHits, or run the search locally."
        );
    }

    private static String send(HttpClient client, HttpRequest request, String operation) throws Exception {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new OpenToolException(
                    "REMOTE_BLAST_HTTP_ERROR",
                    "NCBI BLAST " + operation + " returned HTTP " + status + ": " + summarize(response.body()),
                    "Retry later, reduce the request rate, or use local BLAST."
            );
        }
        return response.body();
    }

    private static void sleepSeconds(int seconds, CancellationToken token) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(seconds).toNanos();
        while (System.nanoTime() < deadline) {
            token.throwIfCancelled();
            Thread.sleep(Math.min(200, Math.max(1, Duration.ofNanos(deadline - System.nanoTime()).toMillis())));
        }
    }

    private static boolean isTabular(String body) {
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (line.split("\t", -1).length >= 12) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeNoHits(String body) {
        String normalized = body.toLowerCase(Locale.ROOT);
        return normalized.contains("no hits found")
                || normalized.contains("there are no hits")
                || normalized.contains("no significant similarity");
    }

    private static String form(Map<String, String> values) {
        StringBuilder form = new StringBuilder();
        values.forEach((key, value) -> {
            if (!form.isEmpty()) {
                form.append('&');
            }
            form.append(encode(key)).append('=').append(encode(value));
        });
        return form.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static int parseRtoe(String body) {
        String value = firstGroup(RTOE_PATTERN, body);
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String summarize(String body) {
        if (body == null) {
            return "";
        }
        String collapsed = body.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= 400 ? collapsed : collapsed.substring(0, 400) + "...";
    }

    private static boolean isAllowedBaseUrl(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null) {
                return false;
            }
            if ("https".equals(scheme)) {
                return true;
            }
            if (!"http".equals(scheme)) {
                return false;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return "localhost".equals(host) || "127.0.0.1".equals(host)
                    || "::1".equals(host) || "[::1]".equals(host);
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
