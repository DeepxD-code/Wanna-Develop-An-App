package com.aiarchitect.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aiarchitect.entity.*;
import com.aiarchitect.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PostgresSessionStore
 * Replaces the Redis-only SessionStore with a dual-write strategy:
 *   - Redis for fast reads (hot session cache)
 *   - PostgreSQL for persistence and history
 *   - In-memory map as emergency fallback
 *
 * Marked @Primary so Spring injects this instead of SessionStore
 * wherever SessionStore is required.
 */
@Service
@Primary
public class PostgresSessionStore extends SessionStore {
    private static final Logger log = LoggerFactory.getLogger(PostgresSessionStore.class);


    private final SessionRepository     sessionRepo;
    private final PhaseResultRepository phaseRepo;
    private final AuditLogRepository    auditRepo;
    private final ProviderMetricRepository metricRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory hot cache (always available, even if Redis/PG down)
    private final Map<String, Map<String, Object>> hotCache = new ConcurrentHashMap<>();

    @Value("${orchestration.session-ttl-minutes:120}")
    private int sessionTtlMinutes;

    // ── Create ─────────────────────────────────────────────────

    @Override
    public Map<String, Object> createSession(String userInput, String mode) {
        String sessionId = UUID.randomUUID().toString();

        // Write to PostgreSQL
        try {
            sessionRepo.save(SessionEntity.builder()
                .sessionId(sessionId)
                .userInput(userInput != null ? userInput : "")
                .mode(mode != null ? mode : "deep")
                .status("PENDING")
                .currentPhase(0)
                .contextBuffer("")
                .build());
        } catch (Exception e) {
            log.warn("PG write failed for new session: {}", e.getMessage());
        }

        // Build response map
        Map<String, Object> session = buildSessionMap(sessionId, userInput, mode);
        hotCache.put(sessionId, session);

        // Also write to Redis (best-effort)
        super.createSession(userInput, mode); // delegates to parent for Redis
        // But override with our session id
        hotCache.put(sessionId, session);

        log.info("Session created: {} mode={}", sessionId, mode);
        return session;
    }

    // ── Read ───────────────────────────────────────────────────

    @Override
    public Map<String, Object> getSession(String sessionId) {
        // 1. Hot cache
        if (hotCache.containsKey(sessionId)) return hotCache.get(sessionId);

        // 2. Redis (parent)
        Map<String, Object> fromRedis = super.getSession(sessionId);
        if (fromRedis != null) { hotCache.put(sessionId, fromRedis); return fromRedis; }

        // 3. PostgreSQL
        try {
            Optional<SessionEntity> entity = sessionRepo.findById(sessionId);
            if (entity.isPresent()) {
                SessionEntity s   = entity.get();
                Map<String, Object> session = buildSessionMap(
                    s.getSessionId(), s.getUserInput(), s.getMode());
                session.put("status",        s.getStatus());
                session.put("currentPhase",  s.getCurrentPhase());
                session.put("contextBuffer", s.getContextBuffer());

                // Load phase results
                List<Map<String, Object>> phases = new ArrayList<>();
                phaseRepo.findBySessionIdOrderByPhaseIdAsc(sessionId).forEach(pr -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("phaseId",    pr.getPhaseId());
                    m.put("status",     pr.getStatus());
                    m.put("output",     pr.getOutput());
                    m.put("provider",   pr.getProvider());
                    m.put("confidence", pr.getConfidence());
                    m.put("latencyMs",  pr.getLatencyMs());
                    phases.add(m);
                });
                session.put("phaseResults", phases);

                hotCache.put(sessionId, session);
                return session;
            }
        } catch (Exception e) {
            log.warn("PG read failed for session {}: {}", sessionId, e.getMessage());
        }

        return null;
    }

    // ── Update ─────────────────────────────────────────────────

    @Override
    public void updateStatus(String sessionId, String status) {
        // Update hot cache
        Map<String, Object> s = getSession(sessionId);
        if (s != null) { s.put("status", status); s.put("updatedAt", Instant.now().toString()); }

        // Update PG
        try {
            sessionRepo.findById(sessionId).ifPresent(entity -> {
                entity.setStatus(status);
                sessionRepo.save(entity);
            });
        } catch (Exception e) { log.warn("PG status update failed: {}", e.getMessage()); }

        // Update Redis
        super.updateStatus(sessionId, status);
    }

    @Override
    public void updateContext(String sessionId, String context) {
        Map<String, Object> s = getSession(sessionId);
        if (s != null) s.put("contextBuffer", context);

        try {
            sessionRepo.findById(sessionId).ifPresent(entity -> {
                entity.setContextBuffer(context);
                sessionRepo.save(entity);
            });
        } catch (Exception e) { log.warn("PG context update failed: {}", e.getMessage()); }

        super.updateContext(sessionId, context);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void updatePhaseResult(String sessionId, int phaseId, Map<String, Object> result) {
        // Update hot cache
        Map<String, Object> s = getSession(sessionId);
        if (s != null) {
            Map<String, Object> results = (Map<String, Object>) s.computeIfAbsent(
                "phaseResults", k -> new LinkedHashMap<>());
            results.put(String.valueOf(phaseId), result);
            s.put("currentPhase", phaseId);
        }

        // Write to PG
        try {
            PhaseResultEntity entity = phaseRepo
                .findBySessionIdAndPhaseId(sessionId, phaseId)
                .orElse(PhaseResultEntity.builder()
                    .sessionId(sessionId).phaseId(phaseId).build());

            entity.setStatus((String) result.getOrDefault("status", "PENDING"));
            entity.setOutput((String) result.get("output"));
            entity.setProvider((String) result.get("provider"));
            entity.setModel((String) result.get("model"));
            entity.setRetries(((Number) result.getOrDefault("retries", 0)).intValue());
            entity.setErrorMessage((String) result.get("error"));

            if (result.containsKey("confidence"))
                entity.setConfidence(((Number) result.get("confidence")).doubleValue());
            if (result.containsKey("latencyMs"))
                entity.setLatencyMs(((Number) result.get("latencyMs")).longValue());
            if (result.containsKey("startedAt"))
                entity.setStartedAt(Instant.parse((String) result.get("startedAt")));
            if (result.containsKey("completedAt"))
                entity.setCompletedAt(Instant.parse((String) result.get("completedAt")));

            phaseRepo.save(entity);
        } catch (Exception e) { log.warn("PG phase result save failed: {}", e.getMessage()); }

        super.updatePhaseResult(sessionId, phaseId, result);
    }

    // ── Delete ─────────────────────────────────────────────────

    @Override
    public void deleteSession(String sessionId) {
        hotCache.remove(sessionId);
        try { sessionRepo.deleteById(sessionId); } catch (Exception e) { log.warn("PG delete failed: {}", e.getMessage()); }
        super.deleteSession(sessionId);
    }

    // ── Analytics ──────────────────────────────────────────────

    public void recordProviderMetric(String provider, String model, boolean success,
                                      int tokens, long latencyMs, int phaseId, String sessionId) {
        try {
            metricRepo.save(ProviderMetricEntity.builder()
                .provider(provider).model(model).success(success)
                .tokens(tokens).latencyMs(latencyMs)
                .phaseId(phaseId).sessionId(sessionId)
                .build());
        } catch (Exception e) { log.warn("Metric record failed: {}", e.getMessage()); }
    }

    public void writeAuditLog(String sessionId, String level, String component, String message) {
        try {
            auditRepo.save(AuditLogEntity.builder()
                .sessionId(sessionId).level(level)
                .component(component).message(message)
                .build());
        } catch (Exception e) { log.warn("Audit log failed: {}", e.getMessage()); }
    }

    public List<Map<String, Object>> getRecentSessions(int limit) {
        try {
            return sessionRepo.findTop20ByOrderByCreatedAtDesc().stream()
                .limit(limit)
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("sessionId",   s.getSessionId());
                    m.put("userInput",   s.getUserInput());
                    m.put("mode",        s.getMode());
                    m.put("status",      s.getStatus());
                    m.put("createdAt",   s.getCreatedAt());
                    return m;
                })
                .toList();
        } catch (Exception e) {
            log.warn("Recent sessions query failed: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> getAnalytics() {
        Map<String, Object> analytics = new LinkedHashMap<>();
        try {
            analytics.put("totalSessions",    sessionRepo.count());
            analytics.put("activeSessions",   sessionRepo.findActiveSessions().size());
            analytics.put("totalProjects",    0);
            analytics.put("recentMetrics",    metricRepo.findTop100ByOrderByRecordedAtDesc().size());
        } catch (Exception e) {
            log.warn("Analytics query failed: {}", e.getMessage());
        }
        return analytics;
    }

    // ── Helpers ────────────────────────────────────────────────

    public PostgresSessionStore(
        SessionRepository sessionRepo,
        PhaseResultRepository phaseRepo,
        AuditLogRepository auditRepo,
        ProviderMetricRepository metricRepo,
        RedisTemplate<String, Object> redisTemplate,
        com.aiarchitect.config.OrchestrationProperties orchProps
    ) {
        super(redisTemplate, orchProps);
        this.sessionRepo = sessionRepo;
        this.phaseRepo = phaseRepo;
        this.auditRepo = auditRepo;
        this.metricRepo = metricRepo;
    }

    private Map<String, Object> buildSessionMap(String sessionId, String userInput, String mode) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId",    sessionId);
        m.put("userInput",    userInput != null ? userInput : "");
        m.put("mode",         mode != null ? mode : "deep");
        m.put("status",       "PENDING");
        m.put("currentPhase", 0);
        m.put("contextBuffer","");
        m.put("phaseResults", new LinkedHashMap<String, Object>());
        m.put("createdAt",    Instant.now().toString());
        m.put("updatedAt",    Instant.now().toString());
        return m;
    }
}
