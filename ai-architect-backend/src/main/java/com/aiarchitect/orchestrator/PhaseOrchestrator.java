package com.aiarchitect.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aiarchitect.config.OrchestrationProperties;
import com.aiarchitect.service.PythonWorkerClient;
import com.aiarchitect.service.KnowledgeGraph;
import com.aiarchitect.service.SecurityService;
import com.aiarchitect.git.GitService;
import com.aiarchitect.output.ProjectWriter;
import com.aiarchitect.output.ZipExporter;
import com.aiarchitect.provider.ProviderService;
import com.aiarchitect.service.SessionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * PhaseOrchestrator
 * Executes phases with supervisor oversight, streams via SSE,
 * writes files, commits git, runs code execution.
 */
@Service
public class PhaseOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(PhaseOrchestrator.class);


    private final ProviderService         providerService;
    private final SessionStore            sessionStore;
    private final PromptBuilder           promptBuilder;
    private final OrchestrationProperties orchProps;
    private final ProjectWriter           projectWriter;
    private final ZipExporter             zipExporter;
    private final GitService              gitService;
    private final PythonWorkerClient      pythonWorker;
    private final SupervisorAgent         supervisor;
    private final KnowledgeGraph          knowledgeGraph;
    private final SecurityService         security;
    private final ObjectMapper            objectMapper = new ObjectMapper();

    private final Map<String, boolean[]> abortFlags   = new ConcurrentHashMap<>();
    private final Map<String, Path>      projectDirs  = new ConcurrentHashMap<>();
    private final Map<String, String[]>  lastProvider = new ConcurrentHashMap<>();

    // ── Execute Phase ──────────────────────────────────────────────────────────

    public void executePhase(String sessionId, int phaseId, SseEmitter emitter) {
        Map<String, Object> session = sessionStore.getSession(sessionId);
        if (session == null) { sendError(emitter, "Session not found: " + sessionId); return; }

        String rawInput      = (String) session.getOrDefault("userInput", "");
        String userInput     = security.sanitizeInput(rawInput);
        String mode          = (String) session.getOrDefault("mode", "deep");
        String contextBuffer = (String) session.getOrDefault("contextBuffer", "");

        // Init project dir on first phase
        if (phaseId == 1) {
            try {
                Path dir = projectWriter.initProjectDir(sessionId, deriveProjectName(userInput));
                projectDirs.put(sessionId, dir);
                gitService.initRepo(dir);
                log.info("Project dir: {}", dir);
            } catch (Exception e) { log.warn("Project dir init failed: {}", e.getMessage()); }
        }

        // Phase 1: inject knowledge from past projects
        String knowledgeContext = "";
        if (phaseId == 1) {
            knowledgeContext = knowledgeGraph.getRelevantContext(userInput, "");
        }

        // Phase 3: enrich with real web research
        String researchContext = "";
        if (phaseId == 3) {
            sendEvent(emitter, Map.of("type", "supervisor", "message", "Starting autonomous web research..."));
            researchContext = pythonWorker.research(
                !userInput.isBlank() ? userInput : "innovative software project ideas", 5);
        }

        boolean[] abort = {false};
        abortFlags.put(sessionId, abort);
        lastProvider.put(sessionId, new String[]{"unknown", "unknown"});

        long   startTime      = System.currentTimeMillis();
        String currentPrompt  = promptBuilder.build(phaseId, contextBuffer + researchContext, userInput, mode);
        String finalResult    = null;

        sessionStore.updateStatus(sessionId, "RUNNING");
        sessionStore.updatePhaseResult(sessionId, phaseId, Map.of(
            "phaseId", phaseId, "status", "RUNNING", "startedAt", Instant.now().toString()));

        try {
            // ── Supervisor-guided execution loop ──────────────────────────────
            for (int attempt = 1; attempt <= orchProps.getMaxRetries(); attempt++) {
                if (abort[0]) break;

                if (attempt > 1) {
                    sendEvent(emitter, Map.of(
                        "type",    "supervisor",
                        "message", "Supervisor: regenerating (attempt " + attempt + "/" + orchProps.getMaxRetries() + ")..."));
                    // Small delay between attempts
                    Thread.sleep(orchProps.getRequestDelayMs());
                }

                // Stream phase output
                StringBuilder phaseOutput = new StringBuilder();
                final int     thisAttempt = attempt;

                String result = providerService.streamWithFailover(
                    currentPrompt,
                    token -> {
                        phaseOutput.append(token);
                        sendEvent(emitter, Map.of("type", "delta", "text", token));
                    },
                    (prov, model) -> {
                        lastProvider.get(sessionId)[0] = prov;
                        lastProvider.get(sessionId)[1] = model;
                        sendEvent(emitter, Map.of("type", "provider", "provider", prov, "model", model));
                    },
                    a -> sendEvent(emitter, Map.of("type", "retry", "attempt", a)),
                    abort,
                    phaseId
                );

                if (abort[0]) break;

                // ── Supervisor evaluation ─────────────────────────────────────
                sendEvent(emitter, Map.of("type", "supervisor", "message",
                    "Supervisor: evaluating quality (attempt " + attempt + ")..."));

                SupervisorAgent.SupervisorDecision decision =
                    supervisor.evaluate(phaseId, result, currentPrompt, sessionId, attempt);

                // Send confidence score to frontend
                sendEvent(emitter, Map.of(
                    "type",       "confidence",
                    "phaseId",    phaseId,
                    "score",      decision.score(),
                    "action",     decision.action().name(),
                    "reasoning",  decision.reasoning()
                ));

                log.info("Supervisor phase={} attempt={} score={} action={}",
                    phaseId, attempt, decision.score(), decision.action());

                if (decision.shouldAbort()) {
                    sendEvent(emitter, Map.of("type", "supervisor",
                        "message", "Supervisor: quality too low — proceeding with best output"));
                    finalResult = result; // use what we have
                    break;
                }

                if (decision.shouldProceed()) {
                    finalResult = result;
                    break;
                }

                // Regen — update prompt with supervisor feedback
                if (decision.improvedPrompt() != null) {
                    currentPrompt = decision.improvedPrompt();
                }

                // Clear previous output from display for retry
                sendEvent(emitter, Map.of("type", "clear", "phaseId", phaseId));
            }

            if (finalResult == null) finalResult = "";

            // ── Post-execution ─────────────────────────────────────────────────
            long   latency  = System.currentTimeMillis() - startTime;
            String provider = lastProvider.getOrDefault(sessionId, new String[]{"unknown","unknown"})[0];
            String model    = lastProvider.getOrDefault(sessionId, new String[]{"unknown","unknown"})[1];
            double confidence = supervisor.getConfidence(sessionId, phaseId);

            Path projectDir = projectDirs.get(sessionId);
            if (projectDir != null) {
                writePhaseFiles(projectDir, phaseId, finalResult, session, emitter);
                gitService.commitPhase(projectDir, phaseId);

                if (phaseId == 11) {
                    // Record to knowledge graph
                    Map<Integer,Double> conf = supervisor.getConfidenceHistory(sessionId);
                    double avgConf = conf.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    knowledgeGraph.recordProject(
                        deriveProjectName(userInput), userInput, mode, mode, avgConf, session);
                }
                if (phaseId == 11) {
                    projectWriter.finalizeProject(projectDir, deriveProjectName(userInput), session);
                    gitService.commitPhase(projectDir, 99);
                    Path zipPath = zipExporter.zipProject(projectDir);
                    sendEvent(emitter, Map.of(
                        "type", "project_ready",
                        "downloadUrl", "/api/sessions/" + sessionId + "/download",
                        "projectDir",  projectDir.toString(),
                        "zipPath",     zipPath.toString()
                    ));
                }
            }

            String newCtx = appendToContext(contextBuffer, phaseId, finalResult);
            sessionStore.updateContext(sessionId, newCtx);
            sessionStore.updatePhaseResult(sessionId, phaseId, Map.of(
                "phaseId",     phaseId,
                "status",      "COMPLETE",
                "output",      finalResult,
                "latencyMs",   latency,
                "provider",    provider,
                "model",       model,
                "confidence",  confidence,
                "completedAt", Instant.now().toString()
            ));

            sendEvent(emitter, Map.of(
                "type",       "done",
                "provider",   provider,
                "model",      model,
                "tokens",     estimateTokens(finalResult),
                "latency",    latency,
                "confidence", confidence
            ));
            emitter.complete();
            knowledgeGraph.recordModelPerformance(provider, phaseId, confidence);
            log.info("Phase {} complete session={} latency={}ms confidence={}", phaseId, sessionId, latency, confidence);

        } catch (InterruptedException e) {
            log.info("Phase {} aborted session={}", phaseId, sessionId);
            sendEvent(emitter, Map.of("type", "error", "message", "Aborted by user"));
            emitter.complete();
        } catch (Exception e) {
            log.error("Phase {} failed session={}: {}", phaseId, sessionId, e.getMessage());
            sessionStore.updatePhaseResult(sessionId, phaseId,
                Map.of("phaseId", phaseId, "status", "ERROR", "error", e.getMessage()));
            sendError(emitter, e.getMessage());
        } finally {
            abortFlags.remove(sessionId);
        }
    }

    // ── File Writing ───────────────────────────────────────────────────────────

    private void writePhaseFiles(Path projectDir, int phaseId, String result, Map<String, Object> session, SseEmitter emitter) {
        try {
            String userInput = (String) session.getOrDefault("userInput", "project");
            projectWriter.writePhaseOutput(projectDir, phaseId, getPhaseName(phaseId), result);
            switch (phaseId) {
                case 4 -> projectWriter.writeArchitectureFiles(projectDir, result);
                case 5 -> projectWriter.writeCiCdFiles(projectDir, result);
                case 7 -> {
                    projectWriter.writeCodeFiles(projectDir, result);
                    projectWriter.writeEnvExample(projectDir, result);
                    projectWriter.writeMakefile(projectDir, deriveProjectName(userInput));
                    projectWriter.writeLicense(projectDir, deriveProjectName(userInput));

                    // Security scan generated code
                    var credFindings = security.scanProjectDirectory(projectDir);
                    if (!credFindings.isEmpty()) {
                        log.warn("Security scan: potential credentials in {} files — injecting .env guidance",
                            credFindings.size());
                        sendEvent(emitter, Map.of("type", "supervisor",
                            "message", "Security: potential credentials detected — enforcing env var usage"));
                    }
                    log.info("Phase 7: running generated code...");
                    PythonWorkerClient.ExecutionResult exec = pythonWorker.runFullPipeline(projectDir, List.of());
                    projectWriter.writePhaseOutput(projectDir, 70, "Code Execution Results", exec.summary());
                }
                case 11 -> projectWriter.writeDocFiles(projectDir, result);
            }
        } catch (Exception e) { log.warn("File writing phase {} failed: {}", phaseId, e.getMessage()); }
    }

    // ── Abort / State ──────────────────────────────────────────────────────────

    public void abortSession(String sessionId) {
        boolean[] flag = abortFlags.get(sessionId);
        if (flag != null) flag[0] = true;
        sessionStore.updateStatus(sessionId, "STOPPED");
    }

    public Path getProjectDir(String sessionId) { return projectDirs.get(sessionId); }

    public SessionStore getSessionStore() { return sessionStore; }

    public Map<Integer, Double> getConfidenceHistory(String sessionId) {
        return supervisor.getConfidenceHistory(sessionId);
    }

    // ── Context ────────────────────────────────────────────────────────────────

    private String appendToContext(String existing, int phaseId, String output) {
        String combined = existing + "\n=== PHASE " + phaseId + " ===\n" + output;
        String[] parts  = combined.split("=== PHASE ");
        int max = orchProps.getMaxContextPhases() + 1;
        if (parts.length > max) {
            parts = Arrays.copyOfRange(parts, parts.length - orchProps.getMaxContextPhases(), parts.length);
            return Arrays.stream(parts).map(p -> "=== PHASE " + p).reduce("", String::concat);
        }
        return combined;
    }

    // ── SSE ────────────────────────────────────────────────────────────────────

    private void sendEvent(SseEmitter emitter, Map<String, Object> data) {
        try { emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(data))); }
        catch (IOException e) { log.debug("SSE send failed: {}", e.getMessage()); }
        catch (Exception e)   { log.warn("SSE error: {}", e.getMessage()); }
    }

    private void sendError(SseEmitter emitter, String message) {
        sendEvent(emitter, Map.of("type", "error", "message", message != null ? message : "Unknown error"));
        try { emitter.complete(); } catch (Exception ignored) {}
    }

    // ── Utils ──────────────────────────────────────────────────────────────────

    private int  estimateTokens(String t) { return t != null ? t.length() / 4 : 0; }

    private String deriveProjectName(String input) {
        if (input == null || input.isBlank()) return "ai-generated-project";
        return input.trim().toLowerCase().replaceAll("[^a-z0-9 ]", "").trim()
                    .replaceAll("\\s+", "-").substring(0, Math.min(input.length(), 30));
    }

    public PhaseOrchestrator(
        ProviderService providerService,
        SessionStore sessionStore,
        PromptBuilder promptBuilder,
        OrchestrationProperties orchProps,
        ProjectWriter projectWriter,
        ZipExporter zipExporter,
        GitService gitService,
        PythonWorkerClient pythonWorker,
        SupervisorAgent supervisor,
        KnowledgeGraph knowledgeGraph,
        SecurityService security
    ) {
        this.providerService = providerService;
        this.sessionStore = sessionStore;
        this.promptBuilder = promptBuilder;
        this.orchProps = orchProps;
        this.projectWriter = projectWriter;
        this.zipExporter = zipExporter;
        this.gitService = gitService;
        this.pythonWorker = pythonWorker;
        this.supervisor = supervisor;
        this.knowledgeGraph = knowledgeGraph;
        this.security = security;
    }

    private String getPhaseName(int p) {
        return switch (p) {
            case 1 -> "Auto Ideation";
            case 2 -> "Auto Ranking";
            case 3 -> "Research";
            case 4 -> "Architecture";
            case 5 -> "Implementation";
            case 6 -> "API Integration";
            case 7 -> "Code Generation";
            case 8 -> "Error Checking";
            case 9 -> "Stress Testing";
            case 10 -> "Extensibility";
            case 11 -> "Final Report";
            default -> "Phase " + p;
        };
    }
}
