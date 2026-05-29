package com.aiarchitect.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aiarchitect.config.ProviderProperties;
import com.aiarchitect.git.GitService;
import com.aiarchitect.orchestrator.PhaseOrchestrator;
import com.aiarchitect.output.ZipExporter;
import com.aiarchitect.service.SessionStore;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

// ─────────────────────────────────────────────────────────────
// SESSION CONTROLLER
// POST   /sessions              → create session
// GET    /sessions/:id          → get session state
// DELETE /sessions/:id          → stop + delete session
// GET    /sessions/:id/download → download project ZIP
// GET    /sessions/:id/git      → get git log
// ─────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/sessions")
class SessionController {
    private static final Logger log = LoggerFactory.getLogger(SessionController.class);


    private final SessionStore       sessionStore;
    private final com.aiarchitect.service.SecurityService security;
    private final PhaseOrchestrator orchestrator;
    private final ZipExporter      zipExporter;
    private final GitService       gitService;
    SessionController(SessionStore sessionStore,
        com.aiarchitect.service.SecurityService security,
        PhaseOrchestrator orchestrator,
        ZipExporter zipExporter,
        GitService gitService) {
        this.sessionStore = sessionStore;
        this.security = security;
        this.orchestrator = orchestrator;
        this.zipExporter = zipExporter;
        this.gitService = gitService;
    }


    @PostMapping
    public ResponseEntity<Map<String, Object>> createSession(@RequestBody Map<String, String> body) {
        String rawInput  = body.getOrDefault("userInput", "");
        var injection = security.detectInjection(rawInput);
        String userInput = injection.injected()
            ? security.sanitizeInput(rawInput)
            : rawInput;
        if (injection.injected()) {
            log.warn("Prompt injection detected in session creation: {}", injection.matches());
        }
        String mode      = body.getOrDefault("mode", "deep");
        Map<String, Object> session = sessionStore.createSession(userInput, mode);
        log.info("Session created: {}", session.get("sessionId"));
        return ResponseEntity.ok(session);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String sessionId) {
        Map<String, Object> session = sessionStore.getSession(sessionId);
        if (session == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(session);
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        orchestrator.abortSession(sessionId);
        sessionStore.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    // ── Download project as ZIP ────────────────────────────────────────────────
    @GetMapping("/{sessionId}/download")
    public ResponseEntity<byte[]> downloadProject(@PathVariable String sessionId) {
        Path projectDir = orchestrator.getProjectDir(sessionId);
        if (projectDir == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] zipBytes = zipExporter.zipProjectToBytes(projectDir);
            String filename = projectDir.getFileName().toString() + ".zip";
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zipBytes);
        } catch (Exception e) {
            log.error("Failed to create ZIP for session {}: {}", sessionId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Confidence scores ─────────────────────────────────────────────────────
    @GetMapping("/{sessionId}/confidence")
    public ResponseEntity<Map<String, Object>> getConfidence(@PathVariable String sessionId) {
        Map<Integer, Double> scores = orchestrator.getConfidenceHistory(sessionId);
        return ResponseEntity.ok(Map.of("sessionId", sessionId, "scores", scores));
    }

    // ── Git log ────────────────────────────────────────────────────────────────
    @GetMapping("/{sessionId}/git")
    public ResponseEntity<Map<String, Object>> getGitInfo(@PathVariable String sessionId) {
        Path projectDir = orchestrator.getProjectDir(sessionId);
        if (projectDir == null) return ResponseEntity.notFound().build();

        List<Map<String, String>> commits = gitService.getCommits(projectDir);
        String log = gitService.getLog(projectDir);

        return ResponseEntity.ok(Map.of(
            "commits",    commits,
            "log",        log,
            "projectDir", projectDir.toString()
        ));
    }
}

// ─────────────────────────────────────────────────────────────
// PHASE CONTROLLER
// GET /sessions/:id/phases/:phaseId/stream → SSE stream
// ─────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/sessions")
class PhaseController {
    private static final Logger log = LoggerFactory.getLogger(PhaseController.class);


    private final PhaseOrchestrator orchestrator;
    PhaseController(PhaseOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    private final ExecutorService   executor = Executors.newVirtualThreadPerTaskExecutor();

    @GetMapping(
        value    = "/{sessionId}/phases/{phaseId}/stream",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter streamPhase(
        @PathVariable String sessionId,
        @PathVariable int    phaseId
    ) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10 min timeout

        executor.submit(() -> {
            try {
                log.info("Starting phase {} for session {}", phaseId, sessionId);
                orchestrator.executePhase(sessionId, phaseId, emitter);
            } catch (Exception e) {
                log.error("Phase {} executor error: {}", phaseId, e.getMessage());
                try { emitter.completeWithError(e); } catch (Exception ignored) {}
            }
        });

        emitter.onTimeout(() -> {
            log.warn("Phase {} SSE timed out for session {}", phaseId, sessionId);
            orchestrator.abortSession(sessionId);
            emitter.complete();
        });

        emitter.onError(ex ->
            log.warn("Phase {} SSE error: {}", phaseId, ex.getMessage()));

        return emitter;
    }

    @DeleteMapping("/{sessionId}/phases/stop")
    public ResponseEntity<Void> stopPipeline(@PathVariable String sessionId) {
        orchestrator.abortSession(sessionId);
        return ResponseEntity.ok().build();
    }

    /**
     * Replay a single phase without restarting the entire pipeline.
     * Uses existing session context — preserves all other phase outputs.
     */
    @GetMapping(
        value    = "/{sessionId}/phases/{phaseId}/replay",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter replayPhase(
        @PathVariable String sessionId,
        @PathVariable int    phaseId
    ) {
        log.info("Replaying phase {} for session {}", phaseId, sessionId);

        // Rebuild context from all phases BEFORE this one
        com.aiarchitect.service.SessionStore sessionStore =
            orchestrator.getSessionStore();

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> session = sessionStore.getSession(sessionId);
        if (session != null) {
            // Rebuild rolling context from phases 1..(phaseId-1)
            StringBuilder ctx = new StringBuilder();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> results =
                (java.util.Map<String, Object>) session.getOrDefault("phaseResults", java.util.Map.of());

            for (int p = 1; p < phaseId; p++) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> pr =
                    (java.util.Map<String, Object>) results.get(String.valueOf(p));
                if (pr != null && pr.containsKey("output")) {
                    ctx.append("\n=== PHASE ").append(p).append(" ===\n")
                       .append(pr.get("output"));
                }
            }
            sessionStore.updateContext(sessionId, ctx.toString());
        }

        SseEmitter emitter = new SseEmitter(600_000L);
        executor.submit(() -> {
            try {
                orchestrator.executePhase(sessionId, phaseId, emitter);
            } catch (Exception e) {
                log.error("Phase {} replay error: {}", phaseId, e.getMessage());
                try { emitter.completeWithError(e); } catch (Exception ignored) {}
            }
        });

        emitter.onTimeout(() -> { orchestrator.abortSession(sessionId); emitter.complete(); });
        return emitter;
    }
}

// ─────────────────────────────────────────────────────────────
// PROVIDER CONTROLLER
// GET /providers/health → provider key status + health
// ─────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/providers")
class ProviderController {

    private final ProviderProperties props;
    ProviderController(ProviderProperties props) {
        this.props = props;
    }


    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        Map<String, ProviderProperties.ProviderConfig> providers = Map.of(
            "gemini",     props.getGemini(),
            "openrouter", props.getOpenrouter(),
            "groq",       props.getGroq(),
            "together",   props.getTogether(),
            "cerebras",   props.getCerebras()
        );
        providers.forEach((name, cfg) -> health.put(name, Map.of(
            "enabled",  cfg.isEnabled(),
            "hasKeys",  cfg.hasValidKeys(),
            "keyCount", cfg.getValidKeys().size(),
            "model",    cfg.getModel(),
            "rpmLimit", cfg.getRpmLimit()
        )));
        return ResponseEntity.ok(health);
    }
}


// ─────────────────────────────────────────────────────────────
// KNOWLEDGE CONTROLLER
// GET /knowledge/stats    → knowledge graph statistics
// GET /knowledge/context  → get context for a query
// ─────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/knowledge")
class KnowledgeController {

    private final com.aiarchitect.service.KnowledgeGraph knowledgeGraph;
    KnowledgeController(com.aiarchitect.service.KnowledgeGraph knowledgeGraph) {
        this.knowledgeGraph = knowledgeGraph;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(knowledgeGraph.getStats());
    }

    @GetMapping("/context")
    public ResponseEntity<Map<String, String>> context(
        @RequestParam(defaultValue = "") String query,
        @RequestParam(defaultValue = "") String domain
    ) {
        String ctx = knowledgeGraph.getRelevantContext(query, domain);
        return ResponseEntity.ok(Map.of("context", ctx));
    }
}


// ─────────────────────────────────────────────────────────────
// ANALYTICS CONTROLLER
// GET /analytics/sessions   → recent session history
// GET /analytics/summary    → overall platform stats
// GET /analytics/providers  → provider performance breakdown
// ─────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/analytics")
class AnalyticsController {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);


    private final com.aiarchitect.service.PostgresSessionStore pgStore;
    private final com.aiarchitect.repository.ProviderMetricRepository metricRepo;
    private final com.aiarchitect.repository.SessionRepository sessionRepo;

    AnalyticsController(
        com.aiarchitect.service.PostgresSessionStore pgStore,
        com.aiarchitect.repository.ProviderMetricRepository metricRepo,
        com.aiarchitect.repository.SessionRepository sessionRepo
    ) {
        this.pgStore = pgStore;
        this.metricRepo = metricRepo;
        this.sessionRepo = sessionRepo;
    }


    @GetMapping("/sessions")
    public ResponseEntity<List<Map<String, Object>>> recentSessions(
        @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(pgStore.getRecentSessions(limit));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(pgStore.getAnalytics());
    }

    @GetMapping("/providers")
    public ResponseEntity<List<Object[]>> providerStats() {
        try {
            return ResponseEntity.ok(metricRepo.successRateByProviderAndPhase());
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/sessions/{sessionId}/logs")
    public ResponseEntity<List<Map<String, Object>>> sessionLogs(
        @PathVariable String sessionId
    ) {
        // Returns audit logs for a session — full implementation via AuditLogRepository
        return ResponseEntity.ok(List.of());
    }
}
