package com.aiarchitect.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * PythonWorkerClient
 * Spring Boot → Python Worker communication.
 * Called after Phase 7 (code gen) to actually execute generated code.
 * Called during Phase 3 (research) to trigger browser research.
 *
 * Falls back gracefully if Python worker is not running.
 */
@Service
public class PythonWorkerClient {
    private static final Logger log = LoggerFactory.getLogger(PythonWorkerClient.class);


    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${python.worker.url:http://localhost:8081}")
    private String workerUrl;

    // ── Health Check ───────────────────────────────────────────

    public boolean isAvailable() {
        try {
            Request req = new Request.Builder()
                .url(workerUrl + "/health")
                .get().build();
            try (Response res = httpClient.newCall(req).execute()) {
                return res.isSuccessful();
            }
        } catch (Exception e) {
            log.debug("Python worker not available: {}", e.getMessage());
            return false;
        }
    }

    // ── Code Execution ─────────────────────────────────────────

    /**
     * Run generated code in isolated venv.
     * Called after Phase 7 completes.
     */
    public ExecutionResult runCode(Path projectDir, List<String> requirements) {
        if (!isAvailable()) {
            log.warn("Python worker not available — skipping code execution");
            return ExecutionResult.skipped("Python worker not running");
        }

        try {
            String body = objectMapper.writeValueAsString(Map.of(
                "project_dir",  projectDir.toString(),
                "requirements", requirements != null ? requirements : List.of(),
                "timeout",      60
            ));

            Request req = new Request.Builder()
                .url(workerUrl + "/execute/run")
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

            return executeRequest(req);
        } catch (Exception e) {
            log.error("Code execution failed: {}", e.getMessage());
            return ExecutionResult.error(e.getMessage());
        }
    }

    /**
     * Run full pipeline: execute + test + lint.
     * Called after Phase 7, results fed into Phase 8.
     */
    public ExecutionResult runFullPipeline(Path projectDir, List<String> requirements) {
        if (!isAvailable()) {
            log.warn("Python worker not available — skipping pipeline");
            return ExecutionResult.skipped("Python worker not running");
        }

        try {
            String body = objectMapper.writeValueAsString(Map.of(
                "project_dir",  projectDir.toString(),
                "requirements", requirements != null ? requirements : List.of(),
                "timeout",      90
            ));

            Request req = new Request.Builder()
                .url(workerUrl + "/execute/full-pipeline")
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

            return executeRequest(req);
        } catch (Exception e) {
            log.error("Full pipeline failed: {}", e.getMessage());
            return ExecutionResult.error(e.getMessage());
        }
    }

    // ── Web Research ───────────────────────────────────────────

    /**
     * Research a topic autonomously using Playwright.
     * Called during Phase 3 to enrich research prompt with real data.
     */
    public String research(String query, int maxPages) {
        if (!isAvailable()) {
            log.info("Python worker not available — skipping browser research");
            return "";
        }

        try {
            String body = objectMapper.writeValueAsString(Map.of(
                "query",     query,
                "max_pages", maxPages,
                "visible",   false
            ));

            Request req = new Request.Builder()
                .url(workerUrl + "/browser/research")
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

            try (Response res = httpClient.newCall(req).execute()) {
                if (!res.isSuccessful() || res.body() == null) return "";
                JsonNode json    = objectMapper.readTree(res.body().string());
                JsonNode results = json.get("results");
                if (results == null || !results.isArray()) return "";

                // Summarize results into a string for context injection
                StringBuilder sb = new StringBuilder("\n[WEB RESEARCH RESULTS]\n");
                for (JsonNode r : results) {
                    sb.append("Source: ").append(r.path("url").asText()).append("\n");
                    sb.append("Title: ").append(r.path("title").asText()).append("\n");
                    sb.append("Content: ").append(r.path("content").asText("").substring(
                        0, Math.min(500, r.path("content").asText("").length())
                    )).append("\n\n");
                }
                sb.append("[END WEB RESEARCH]\n");
                return sb.toString();
            }
        } catch (Exception e) {
            log.warn("Research failed: {}", e.getMessage());
            return "";
        }
    }

    // ── Internal ───────────────────────────────────────────────

    public PythonWorkerClient(
        OkHttpClient httpClient
    ) {
        this.httpClient = httpClient;
    }

    private ExecutionResult executeRequest(Request req) throws IOException {
        try (Response res = httpClient.newCall(req).execute()) {
            String responseBody = res.body() != null ? res.body().string() : "{}";

            if (!res.isSuccessful()) {
                return ExecutionResult.error("HTTP " + res.code() + ": " + responseBody);
            }
            JsonNode json = objectMapper.readTree(responseBody);
            return ExecutionResult.fromJson(json);
        }
    }

    // ── Result Types ───────────────────────────────────────────

    public record ExecutionResult(
        boolean success,
        String  stdout,
        String  stderr,
        int     exitCode,
        double  duration,
        String  status     // "ok" | "skipped" | "error"
    ) {
        static ExecutionResult fromJson(JsonNode json) {
            return new ExecutionResult(
                json.path("success").asBoolean(false),
                json.path("stdout").asText(""),
                json.path("stderr").asText(""),
                json.path("exit_code").asInt(-1),
                json.path("duration").asDouble(0),
                "ok"
            );
        }

        static ExecutionResult skipped(String reason) {
            return new ExecutionResult(false, "", reason, -1, 0, "skipped");
        }

        static ExecutionResult error(String msg) {
            return new ExecutionResult(false, "", msg, -1, 0, "error");
        }

        public String summary() {
            if ("skipped".equals(status)) return "[SKIPPED] " + stderr;
            if (!success)                 return "[FAILED] exit=" + exitCode + "\n" + stderr;
            return "[SUCCESS] " + String.format("%.1fs", duration) + "\n" + stdout;
        }
    }
}
