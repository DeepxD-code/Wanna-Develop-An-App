package com.aiarchitect.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aiarchitect.provider.ProviderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SupervisorAgent
 * Meta-agent that evaluates every phase output before it proceeds.
 *
 * Responsibilities:
 * - Score output quality (0-10) per phase
 * - Detect hallucinations, incomplete outputs, low-effort responses
 * - Decide: ACCEPT | REGEN | SIMPLIFY | ABORT
 * - Dynamically alter prompts for retry attempts
 * - Track confidence history per session
 */
@Component
public class SupervisorAgent {
    private static final Logger log = LoggerFactory.getLogger(SupervisorAgent.class);


    private final ProviderService providerService;
    private final ObjectMapper    objectMapper = new ObjectMapper();

    // Minimum confidence thresholds per phase (0-10)
    private static final Map<Integer, Double> THRESHOLDS = Map.ofEntries(
        Map.entry(1,  7.0),   // Ideation
        Map.entry(2,  7.0),   // Ranking
        Map.entry(3,  6.5),   // Research
        Map.entry(4,  8.0),   // Architecture — critical
        Map.entry(5,  7.0),   // Planning
        Map.entry(6,  7.0),   // API Integration
        Map.entry(7,  8.5),   // Code — most critical
        Map.entry(8,  7.5),   // Error Checking
        Map.entry(9,  7.0),   // Stress Testing
        Map.entry(10, 6.5),   // Extensibility
        Map.entry(11, 7.5)    // Final Report
    );

    // Confidence history: sessionId → phaseId → score
    private final Map<String, Map<Integer, Double>> confidenceHistory =
        new ConcurrentHashMap<>();

    // ── Public API ─────────────────────────────────────────────

    /**
     * Evaluate a phase output.
     * Returns a SupervisorDecision: score, action, reasoning, improved prompt.
     */
    public SupervisorDecision evaluate(
        int    phaseId,
        String output,
        String originalPrompt,
        String sessionId,
        int    attemptNumber
    ) {
        if (output == null || output.isBlank()) {
            return SupervisorDecision.regen(phaseId, 0.0,
                "Empty output", buildImprovedPrompt(originalPrompt, "Output was empty. Provide a complete response.", attemptNumber));
        }

        // Fast heuristic checks (no API call)
        SupervisorDecision heuristic = heuristicCheck(phaseId, output, originalPrompt, attemptNumber);
        if (heuristic != null) return heuristic;

        // Full AI-based scoring
        try {
            return aiScore(phaseId, output, originalPrompt, sessionId, attemptNumber);
        } catch (Exception e) {
            log.warn("Supervisor AI scoring failed for phase {}: {}", phaseId, e.getMessage());
            // Fall back to heuristic score if AI scoring fails
            double heuristicScore = estimateScore(phaseId, output);
            return decideFromScore(phaseId, heuristicScore, output, originalPrompt, attemptNumber,
                "Heuristic scoring (AI unavailable)");
        }
    }

    /**
     * Get confidence score for a completed phase.
     */
    public double getConfidence(String sessionId, int phaseId) {
        return confidenceHistory
            .getOrDefault(sessionId, Map.of())
            .getOrDefault(phaseId, -1.0);
    }

    /**
     * Get all confidence scores for a session.
     */
    public Map<Integer, Double> getConfidenceHistory(String sessionId) {
        return confidenceHistory.getOrDefault(sessionId, Map.of());
    }

    /**
     * Build a dynamically improved prompt for retry attempts.
     */
    public String buildRetryPrompt(
        String originalPrompt,
        String failureReason,
        String previousOutput,
        int    attemptNumber
    ) {
        return String.format("""
            %s

            ─────────────────────────────────────────
            SUPERVISOR FEEDBACK (Attempt %d failed)
            ─────────────────────────────────────────
            Issue: %s

            Previous attempt (DO NOT REPEAT):
            %s

            CORRECTIONS REQUIRED:
            - Address the issue above specifically
            - Be more thorough and complete
            - Provide concrete, actionable content
            - No placeholder text or TODO comments
            ─────────────────────────────────────────
            """,
            originalPrompt,
            attemptNumber,
            failureReason,
            previousOutput.length() > 500
                ? previousOutput.substring(0, 500) + "...[truncated]"
                : previousOutput
        );
    }

    // ── Heuristic Checks (fast, no API) ───────────────────────

    private SupervisorDecision heuristicCheck(
        int phaseId, String output, String prompt, int attempt
    ) {
        int len = output.length();

        // Too short for the phase
        int minLen = minLengthForPhase(phaseId);
        if (len < minLen) {
            String reason = String.format("Output too short (%d chars, expected >%d)", len, minLen);
            return SupervisorDecision.regen(phaseId, 2.0, reason,
                buildImprovedPrompt(prompt, reason + ". Provide a complete, detailed response.", attempt));
        }

        // Detect incomplete/placeholder content
        String lower = output.toLowerCase();
        if (lower.contains("todo:") || lower.contains("[insert") || lower.contains("placeholder")) {
            String reason = "Output contains placeholder/TODO content";
            return SupervisorDecision.regen(phaseId, 3.0, reason,
                buildImprovedPrompt(prompt, "Remove all placeholders and TODO comments. Fill in all content.", attempt));
        }

        // Code phase specific: must contain actual code
        if (phaseId == 7) {
            boolean hasCode = output.contains("```") || output.contains("def ") ||
                              output.contains("function ") || output.contains("class ");
            if (!hasCode) {
                String reason = "Code phase output contains no actual code";
                return SupervisorDecision.regen(phaseId, 2.0, reason,
                    buildImprovedPrompt(prompt, "You MUST generate actual runnable code files. No descriptions only.", attempt));
            }
        }

        // Architecture phase: must contain diagram
        if (phaseId == 4) {
            boolean hasDiagram = output.contains("─") || output.contains("+--") ||
                                 output.contains("│") || output.contains("┌") ||
                                 output.contains("```");
            if (!hasDiagram) {
                String reason = "Architecture phase missing system diagram";
                return SupervisorDecision.regen(phaseId, 4.0, reason,
                    buildImprovedPrompt(prompt, "You MUST include an ASCII architecture diagram.", attempt));
            }
        }

        return null; // pass heuristic — proceed to AI scoring
    }

    // ── AI-Based Scoring ──────────────────────────────────────

    private SupervisorDecision aiScore(
        int phaseId, String output, String prompt,
        String sessionId, int attempt
    ) throws Exception {

        String scoringPrompt = buildScoringPrompt(phaseId, output);
        boolean[] noAbort = {false};
        StringBuilder scoreOutput = new StringBuilder();

        providerService.streamWithFailover(
            scoringPrompt,
            scoreOutput::append,
            (p, m) -> {},
            a -> {},
            noAbort,
            0
        );

        double score = parseScore(scoreOutput.toString());
        String reasoning = extractReasoning(scoreOutput.toString());

        log.info("Supervisor: phase={} score={} session={}", phaseId, score, sessionId);

        // Store confidence
        confidenceHistory
            .computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
            .put(phaseId, score);

        return decideFromScore(phaseId, score, output, prompt, attempt, reasoning);
    }

    private String buildScoringPrompt(int phaseId, String output) {
        String phaseName = getPhaseName(phaseId);
        return String.format("""
            You are a strict quality supervisor for an AI software engineering system.
            Evaluate the following output from Phase %d (%s).

            OUTPUT TO EVALUATE:
            ---
            %s
            ---

            Score this output from 0-10 on:
            1. Completeness (is everything required present?)
            2. Technical accuracy (is it correct and realistic?)
            3. Specificity (concrete details, not vague statements?)
            4. Actionability (can a developer actually use this?)
            5. Quality (professional, enterprise-grade standard?)

            Respond in EXACTLY this format:
            SCORE: [number 0-10, one decimal]
            REASONING: [one sentence]
            ISSUES: [comma-separated list of specific problems, or "none"]

            Be strict. A score of 7+ means it can proceed.
            Do not be generous. Incomplete or vague outputs get 4 or below.
            """,
            phaseId, phaseName,
            output.length() > 3000 ? output.substring(0, 3000) + "...[truncated for evaluation]" : output
        );
    }

    private SupervisorDecision decideFromScore(
        int phaseId, double score, String output,
        String prompt, int attempt, String reasoning
    ) {
        double threshold = THRESHOLDS.getOrDefault(phaseId, 7.0);

        if (score >= threshold) {
            return SupervisorDecision.accept(phaseId, score, reasoning);
        }

        if (score >= threshold - 2.0) {
            // Close to threshold — regen with improved prompt
            String improved = buildImprovedPrompt(prompt,
                "Previous output scored " + score + "/10. Issues: " + reasoning, attempt);
            return SupervisorDecision.regen(phaseId, score, reasoning, improved);
        }

        if (score >= 3.0) {
            // Low score — try simplified generation
            return SupervisorDecision.simplify(phaseId, score, reasoning,
                buildSimplifiedPrompt(prompt, phaseId));
        }

        // Very low — abort this phase
        return SupervisorDecision.abort(phaseId, score, "Output quality too low: " + reasoning);
    }

    // ── Prompt Builders ────────────────────────────────────────

    private String buildImprovedPrompt(String original, String issue, int attempt) {
        return original + String.format(
            "\n\n[SUPERVISOR: Attempt %d. Issue: %s. Be thorough, complete, and specific.]\n",
            attempt, issue
        );
    }

    private String buildSimplifiedPrompt(String original, int phaseId) {
        return original + String.format(
            "\n\n[SUPERVISOR: Quality was too low. For phase %d, provide a simplified but complete and correct version. " +
            "Focus on the most critical aspects only. Quality over quantity.]\n",
            phaseId
        );
    }

    // ── Helpers ────────────────────────────────────────────────

    private double parseScore(String text) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("SCORE:\\s*(\\d+(?:\\.\\d+)?)")
                .matcher(text);
            if (m.find()) return Double.parseDouble(m.group(1));
        } catch (Exception ignored) {}
        return estimateScore(0, text);
    }

    private String extractReasoning(String text) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("REASONING:\\s*(.+)")
                .matcher(text);
            if (m.find()) return m.group(1).trim();
        } catch (Exception ignored) {}
        return "No reasoning extracted";
    }

    private double estimateScore(int phaseId, String output) {
        // Heuristic scoring when AI scoring not available
        int len = output.length();
        if (len < 100)  return 2.0;
        if (len < 500)  return 4.0;
        if (len < 2000) return 6.0;
        if (len < 5000) return 7.5;
        return 8.0;
    }

    private int minLengthForPhase(int phaseId) {
        return switch (phaseId) {
            case 7  -> 2000;  // Code must be substantial
            case 4  -> 1500;  // Architecture needs diagram
            case 11 -> 1500;  // Final report needs detail
            case 3  -> 1000;  // Research needs depth
            default -> 500;
        };
    }

    public SupervisorAgent(
        ProviderService providerService
    ) {
        this.providerService = providerService;
    }

    private String getPhaseName(int phaseId) {
        return switch (phaseId) {
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
            default -> "Phase " + phaseId;
        };
    }

    // ── Decision Record ────────────────────────────────────────

    public record SupervisorDecision(
        int     phaseId,
        double  score,
        Action  action,
        String  reasoning,
        String  improvedPrompt
    ) {
        public enum Action { ACCEPT, REGEN, SIMPLIFY, ABORT }

        public boolean shouldProceed() { return action == Action.ACCEPT; }
        public boolean shouldRegen()   { return action == Action.REGEN || action == Action.SIMPLIFY; }
        public boolean shouldAbort()   { return action == Action.ABORT; }

        static SupervisorDecision accept(int p, double s, String r) {
            return new SupervisorDecision(p, s, Action.ACCEPT, r, null);
        }
        static SupervisorDecision regen(int p, double s, String r, String improved) {
            return new SupervisorDecision(p, s, Action.REGEN, r, improved);
        }
        static SupervisorDecision simplify(int p, double s, String r, String simplified) {
            return new SupervisorDecision(p, s, Action.SIMPLIFY, r, simplified);
        }
        static SupervisorDecision abort(int p, double s, String r) {
            return new SupervisorDecision(p, s, Action.ABORT, r, null);
        }
    }
}
