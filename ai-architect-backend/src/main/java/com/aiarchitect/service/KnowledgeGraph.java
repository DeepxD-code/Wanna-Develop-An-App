package com.aiarchitect.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * KnowledgeGraph
 * Persistent local knowledge base that learns from every project run.
 * Influences future generations with patterns, risks, and successful approaches.
 *
 * Storage: ~/ai-architect-knowledge/graph.json
 *
 * Nodes:
 *   - Projects (name, domain, stack, outcome score)
 *   - Patterns (successful architecture templates)
 *   - Risks (common failure modes per domain)
 *   - Technologies (compatibility + version notes)
 *
 * Used by PromptBuilder to inject relevant past experience.
 */
@Service
public class KnowledgeGraph {

    public KnowledgeGraph() {}
    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraph.class);


    @Value("${app.knowledge-dir:${user.home}/ai-architect-knowledge}")
    private String knowledgeDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory graph (persisted to disk)
    private Map<String, List<Map<String, Object>>> graph = new ConcurrentHashMap<>();

    // ── Lifecycle ──────────────────────────────────────────────

    @PostConstruct
    public void load() {
        try {
            Path dir = Path.of(knowledgeDir);
            Files.createDirectories(dir);
            Path file = dir.resolve("graph.json");
            if (Files.exists(file)) {
                graph = objectMapper.readValue(
                    file.toFile(),
                    new TypeReference<Map<String, List<Map<String, Object>>>>() {}
                );
                log.info("Knowledge graph loaded: {} node types, {} total nodes",
                    graph.size(), graph.values().stream().mapToInt(List::size).sum());
            } else {
                graph = new ConcurrentHashMap<>();
                log.info("Knowledge graph initialized (empty)");
            }
        } catch (Exception e) {
            log.warn("Failed to load knowledge graph: {}", e.getMessage());
            graph = new ConcurrentHashMap<>();
        }
    }

    public void persist() {
        try {
            Path file = Path.of(knowledgeDir, "graph.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), graph);
        } catch (Exception e) {
            log.warn("Failed to persist knowledge graph: {}", e.getMessage());
        }
    }

    // ── Record Project ─────────────────────────────────────────

    /**
     * Record a completed project into the knowledge graph.
     * Called after Phase 11 completes.
     */
    public void recordProject(String name, String domain, String stack,
                               String mode, double confidenceAvg, Map<String, Object> session) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name",           name);
        node.put("domain",         domain);
        node.put("stack",          stack);
        node.put("mode",           mode);
        node.put("confidenceAvg",  confidenceAvg);
        node.put("timestamp",      Instant.now().toString());
        node.put("outcome",        confidenceAvg >= 7.5 ? "success" : "partial");

        addNode("projects", node);

        // Extract and record patterns from this project
        extractPatterns(name, domain, stack, confidenceAvg);

        persist();
        log.info("Knowledge graph: recorded project '{}' (confidence={:.1f})", name, confidenceAvg);
    }

    /**
     * Record a risk encountered during a project.
     */
    public void recordRisk(String domain, String phaseId, String description, String mitigation) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("domain",      domain);
        node.put("phase",       phaseId);
        node.put("description", description);
        node.put("mitigation",  mitigation);
        node.put("timestamp",   Instant.now().toString());
        node.put("count",       1);

        // Increment count if same risk exists
        List<Map<String, Object>> risks = graph.computeIfAbsent("risks", k -> new ArrayList<>());
        boolean found = false;
        for (Map<String, Object> r : risks) {
            if (description.equals(r.get("description"))) {
                r.put("count", ((Number) r.getOrDefault("count", 1)).intValue() + 1);
                found = true;
                break;
            }
        }
        if (!found) risks.add(node);

        persist();
    }

    // ── Query for Context Injection ────────────────────────────

    /**
     * Get relevant past experience for a new project.
     * Returns a formatted string to inject into Phase 1 prompt.
     */
    public String getRelevantContext(String userInput, String domain) {
        if (graph.isEmpty()) return "";

        StringBuilder ctx = new StringBuilder();
        ctx.append("\n[KNOWLEDGE FROM PREVIOUS PROJECTS]\n");

        // Similar projects
        List<Map<String, Object>> projects = getSimilarProjects(userInput, domain, 3);
        if (!projects.isEmpty()) {
            ctx.append("Similar past projects:\n");
            for (Map<String, Object> p : projects) {
                ctx.append(String.format("  - %s (%s stack, %s outcome, confidence=%.1f)\n",
                    p.get("name"), p.get("stack"), p.get("outcome"),
                    ((Number) p.getOrDefault("confidenceAvg", 0)).doubleValue()));
            }
        }

        // Relevant patterns
        List<Map<String, Object>> patterns = getPatterns(domain, 3);
        if (!patterns.isEmpty()) {
            ctx.append("Proven patterns for this domain:\n");
            for (Map<String, Object> p : patterns) {
                ctx.append(String.format("  - %s: %s\n", p.get("name"), p.get("description")));
            }
        }

        // Common risks
        List<Map<String, Object>> risks = getRisks(domain, 3);
        if (!risks.isEmpty()) {
            ctx.append("Known risks to watch for:\n");
            for (Map<String, Object> r : risks) {
                ctx.append(String.format("  - %s (seen %dx) → %s\n",
                    r.get("description"),
                    ((Number) r.getOrDefault("count", 1)).intValue(),
                    r.get("mitigation")));
            }
        }

        ctx.append("[END KNOWLEDGE]\n");
        return ctx.toString();
    }

    /**
     * Get model performance stats for a given phase.
     * Used by provider selection to prefer successful models.
     */
    public Map<String, Double> getModelStats(int phaseId) {
        List<Map<String, Object>> stats = graph.getOrDefault("model_stats", List.of());
        Map<String, Double> result = new LinkedHashMap<>();

        for (Map<String, Object> s : stats) {
            if (phaseId == ((Number) s.getOrDefault("phaseId", -1)).intValue()) {
                result.put(
                    (String) s.get("provider"),
                    ((Number) s.getOrDefault("avgScore", 5.0)).doubleValue()
                );
            }
        }

        return result;
    }

    /**
     * Record model performance for a phase.
     */
    public void recordModelPerformance(String provider, int phaseId, double score) {
        List<Map<String, Object>> stats = graph.computeIfAbsent("model_stats", k -> new ArrayList<>());

        // Find existing record
        for (Map<String, Object> s : stats) {
            if (provider.equals(s.get("provider")) &&
                phaseId == ((Number) s.getOrDefault("phaseId", -1)).intValue()) {
                // Rolling average
                double current = ((Number) s.getOrDefault("avgScore", score)).doubleValue();
                int    count   = ((Number) s.getOrDefault("count", 1)).intValue();
                s.put("avgScore", (current * count + score) / (count + 1));
                s.put("count",    count + 1);
                persist();
                return;
            }
        }

        // New record
        stats.add(Map.of(
            "provider", provider,
            "phaseId",  phaseId,
            "avgScore", score,
            "count",    1
        ));
        persist();
    }

    // ── Stats ──────────────────────────────────────────────────

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalProjects",  graph.getOrDefault("projects",    List.of()).size());
        stats.put("totalPatterns",  graph.getOrDefault("patterns",    List.of()).size());
        stats.put("totalRisks",     graph.getOrDefault("risks",       List.of()).size());
        stats.put("totalModelStats",graph.getOrDefault("model_stats", List.of()).size());
        return stats;
    }

    // ── Internal ───────────────────────────────────────────────

    private void addNode(String type, Map<String, Object> node) {
        graph.computeIfAbsent(type, k -> new ArrayList<>()).add(node);
    }

    private void extractPatterns(String name, String domain, String stack, double confidence) {
        if (confidence < 7.5) return; // only learn from successful projects

        Map<String, Object> pattern = new LinkedHashMap<>();
        pattern.put("name",        name + " pattern");
        pattern.put("domain",      domain);
        pattern.put("stack",       stack);
        pattern.put("description", "Successful " + domain + " project using " + stack);
        pattern.put("confidence",  confidence);
        pattern.put("timestamp",   Instant.now().toString());

        addNode("patterns", pattern);
    }

    private List<Map<String, Object>> getSimilarProjects(String input, String domain, int limit) {
        List<Map<String, Object>> projects = graph.getOrDefault("projects", List.of());
        String lowerInput  = input != null ? input.toLowerCase() : "";
        String lowerDomain = domain != null ? domain.toLowerCase() : "";

        return projects.stream()
            .filter(p -> {
                String pDomain = String.valueOf(p.getOrDefault("domain", "")).toLowerCase();
                String pName   = String.valueOf(p.getOrDefault("name",   "")).toLowerCase();
                return pDomain.contains(lowerDomain) || lowerInput.contains(pDomain) ||
                       lowerInput.contains(pName);
            })
            .sorted(Comparator.comparingDouble(
                p -> -((Number) p.getOrDefault("confidenceAvg", 0)).doubleValue()))
            .limit(limit)
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> getPatterns(String domain, int limit) {
        List<Map<String, Object>> patterns = graph.getOrDefault("patterns", List.of());
        String lowerDomain = domain != null ? domain.toLowerCase() : "";
        return patterns.stream()
            .filter(p -> String.valueOf(p.getOrDefault("domain", "")).toLowerCase().contains(lowerDomain))
            .limit(limit)
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> getRisks(String domain, int limit) {
        List<Map<String, Object>> risks = graph.getOrDefault("risks", List.of());
        String lowerDomain = domain != null ? domain.toLowerCase() : "";
        return risks.stream()
            .filter(r -> String.valueOf(r.getOrDefault("domain", "")).toLowerCase().contains(lowerDomain))
            .sorted(Comparator.comparingInt(
                r -> -((Number) r.getOrDefault("count", 1)).intValue()))
            .limit(limit)
            .collect(Collectors.toList());
    }
}
