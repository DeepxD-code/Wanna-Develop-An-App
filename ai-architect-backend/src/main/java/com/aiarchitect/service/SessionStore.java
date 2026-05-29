package com.aiarchitect.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aiarchitect.config.OrchestrationProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SessionStore
 * Stores session state in Redis for persistence across restarts.
 * Falls back to in-memory map if Redis is unavailable.
 */
@Service
public class SessionStore {
    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);


    private final RedisTemplate<String, Object> redisTemplate;
    private final OrchestrationProperties       orchProps;
    private final ObjectMapper                  objectMapper = new ObjectMapper();

    // Fallback in-memory store when Redis is down
    private final Map<String, Map<String, Object>> memStore = new ConcurrentHashMap<>();

    private static final String KEY_PREFIX = "ai-architect:session:";

    // ── Create ─────────────────────────────────────────────────────────────────

    public Map<String, Object> createSession(String userInput, String mode) {
        String sessionId = UUID.randomUUID().toString();
        Map<String, Object> session = new HashMap<>();
        session.put("sessionId",     sessionId);
        session.put("userInput",     userInput != null ? userInput : "");
        session.put("mode",          mode != null ? mode : "deep");
        session.put("status",        "PENDING");
        session.put("currentPhase",  0);
        session.put("contextBuffer", "");
        session.put("phaseResults",  new HashMap<String, Object>());
        session.put("createdAt",     Instant.now().toString());
        session.put("updatedAt",     Instant.now().toString());

        save(sessionId, session);
        log.info("Session created: {} mode={}", sessionId, mode);
        return session;
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Map<String, Object> getSession(String sessionId) {
        try {
            Object raw = redisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
            if (raw instanceof String json) {
                return objectMapper.readValue(json, Map.class);
            }
        } catch (Exception e) {
            log.debug("Redis unavailable, using memStore: {}", e.getMessage());
        }
        return memStore.get(sessionId);
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    public void updateStatus(String sessionId, String status) {
        Map<String, Object> s = getSession(sessionId);
        if (s == null) return;
        s.put("status", status);
        s.put("updatedAt", Instant.now().toString());
        save(sessionId, s);
    }

    public void updateContext(String sessionId, String context) {
        Map<String, Object> s = getSession(sessionId);
        if (s == null) return;
        s.put("contextBuffer", context);
        s.put("updatedAt", Instant.now().toString());
        save(sessionId, s);
    }

    @SuppressWarnings("unchecked")
    public void updatePhaseResult(String sessionId, int phaseId, Map<String, Object> result) {
        Map<String, Object> s = getSession(sessionId);
        if (s == null) return;
        Map<String, Object> results = (Map<String, Object>) s.getOrDefault("phaseResults", new HashMap<>());
        results.put(String.valueOf(phaseId), result);
        s.put("phaseResults", results);
        s.put("currentPhase", phaseId);
        s.put("updatedAt", Instant.now().toString());
        save(sessionId, s);
    }

    // ── Delete ─────────────────────────────────────────────────────────────────

    public void deleteSession(String sessionId) {
        try {
            redisTemplate.delete(KEY_PREFIX + sessionId);
        } catch (Exception ignored) {}
        memStore.remove(sessionId);
        log.info("Session deleted: {}", sessionId);
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    public SessionStore(
        RedisTemplate<String, Object> redisTemplate,
        OrchestrationProperties orchProps
    ) {
        this.redisTemplate = redisTemplate;
        this.orchProps = orchProps;
    }

    private void save(String sessionId, Map<String, Object> session) {
        // Always save to memStore as backup
        memStore.put(sessionId, session);

        // Try Redis
        try {
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(
                KEY_PREFIX + sessionId,
                json,
                Duration.ofMinutes(orchProps.getSessionTtlMinutes())
            );
        } catch (Exception e) {
            log.debug("Redis save failed, using memStore only: {}", e.getMessage());
        }
    }
}
