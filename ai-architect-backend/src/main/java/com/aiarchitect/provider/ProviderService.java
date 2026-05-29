package com.aiarchitect.provider;

import com.aiarchitect.config.OrchestrationProperties;
import com.aiarchitect.config.ProviderProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Service
public class ProviderService {

    private static final Logger log = LoggerFactory.getLogger(ProviderService.class);

    private final ProviderProperties      props;
    private final OrchestrationProperties orchProps;
    private final OkHttpClient            httpClient;
    private final PhaseModelRouter        router;
    private final ObjectMapper            objectMapper = new ObjectMapper();

    public ProviderService(ProviderProperties props,
                           OrchestrationProperties orchProps,
                           OkHttpClient httpClient,
                           PhaseModelRouter router) {
        this.props      = props;
        this.orchProps  = orchProps;
        this.httpClient = httpClient;
        this.router     = router;
    }

    private static final List<String> DEFAULT_ORDER =
        List.of("gemini", "openrouter", "groq", "together", "cerebras");

    private final Map<String, AtomicInteger> keyIndexes  = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> failCounts  = new ConcurrentHashMap<>();
    private static final int FAIL_THRESHOLD = 3;

    // ── Public API ──────────────────────────────────────────────

    /** Stream with default provider order (no phase specialisation) */
    public String streamWithFailover(
        String              prompt,
        Consumer<String>    onToken,
        ProviderSwitchEvent onProviderSwitch,
        RetryEvent          onRetry,
        boolean[]           abortFlag
    ) throws Exception {
        return streamWithFailover(prompt, onToken, onProviderSwitch, onRetry, abortFlag, 0);
    }

    /** Stream with phase-aware provider order */
    public String streamWithFailover(
        String              prompt,
        Consumer<String>    onToken,
        ProviderSwitchEvent onProviderSwitch,
        RetryEvent          onRetry,
        boolean[]           abortFlag,
        int                 phaseId
    ) throws Exception {

        List<String> order  = phaseId > 0 ? router.getProviderOrder(phaseId) : DEFAULT_ORDER;
        List<String> errors = new ArrayList<>();
        Set<String> rateLimited = new HashSet<>();

        for (String providerName : order) {
            if (rateLimited.contains(providerName)) {
                log.debug("Skipping previously rate-limited provider {}", providerName);
                continue;
            }
            AtomicInteger fc = failCounts.get(providerName);
            if (fc != null && fc.get() >= FAIL_THRESHOLD) {
                log.debug("Skipping provider {}: {} consecutive failures", providerName, fc.get());
                continue;
            }
            ProviderProperties.ProviderConfig cfg = getConfig(providerName);
            if (cfg == null || !cfg.isEnabled() || !cfg.hasValidKeys()) {
                log.debug("Skipping provider {}: no valid keys or disabled", providerName);
                continue;
            }

            List<String> models = new ArrayList<>();
            models.add(cfg.getModel());
            if (cfg.getFallbackModel() != null && !cfg.getFallbackModel().isBlank()) {
                models.add(cfg.getFallbackModel());
            }

            List<String> keys = cfg.getValidKeys();
            for (int ki = 0; ki < keys.size(); ki++) {
                if (abortFlag[0]) throw new InterruptedException("Aborted");

                for (String model : models) {
                    if (abortFlag[0]) throw new InterruptedException("Aborted");
                    String key = rotateKey(providerName, keys);
                    log.info("Trying provider={} model={}", providerName, model);
                    onProviderSwitch.accept(providerName, model);

                    for (int attempt = 0; attempt < orchProps.getMaxRetries(); attempt++) {
                        if (abortFlag[0]) throw new InterruptedException("Aborted");
                        if (attempt > 0) {
                            onRetry.accept(attempt);
                            Thread.sleep(orchProps.getRequestDelayMs() * (long) Math.pow(2, attempt));
                        }
                        try {
                            String result = switch (providerName) {
                                case "gemini"     -> streamGemini(cfg, key, prompt, onToken, abortFlag, model);
                                default           -> streamOpenAICompat(cfg, key, prompt, onToken, abortFlag, cfg.getBaseUrl(), model);
                            };
                            failCounts.getOrDefault(providerName, new AtomicInteger()).set(0);
                            log.info("Phase complete via provider={}", providerName);
                            return result;
                        } catch (RateLimitException e) {
                            log.warn("Rate limit {} key={} model={}: {}", providerName, ki, model, e.getMessage());
                            errors.add(providerName + " rate-limited");
                            rateLimited.add(providerName);
                            failCounts.computeIfAbsent(providerName, k -> new AtomicInteger()).incrementAndGet();
                            Thread.sleep(1000);
                            break; // break to next model/key
                        } catch (Exception e) {
                            log.warn("Provider {} key={} model={} attempt {} failed: {}", providerName, ki, model, attempt + 1, e.getMessage());
                            errors.add(providerName + "[" + attempt + "]: " + e.getMessage());
                            failCounts.computeIfAbsent(providerName, k -> new AtomicInteger()).incrementAndGet();
                        }
                    }
                }
            }
            Thread.sleep(1000);
        }
        throw new Exception("All providers exhausted.\n" + String.join("\n", errors));
    }

    // ── Gemini ──────────────────────────────────────────────────

    private String streamGemini(ProviderProperties.ProviderConfig cfg, String key,
                                 String prompt, Consumer<String> onToken,
                                 boolean[] abortFlag, String model) throws Exception {
        String url = cfg.getBaseUrl() + "/models/" + model
            + ":streamGenerateContent?alt=sse&key=" + key;

        String body = objectMapper.writeValueAsString(Map.of(
            "contents", List.of(Map.of("role","user","parts",List.of(Map.of("text",prompt)))),
            "generationConfig", Map.of("maxOutputTokens",8192,"temperature",0.7,"topP",0.95)
        ));

        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(body, MediaType.parse("application/json")))
            .build();

        return executeStreaming(request, abortFlag, onToken, "gemini");
    }

    // ── OpenAI-compatible (Groq, OpenRouter, Together, Cerebras) ─

    private String streamOpenAICompat(ProviderProperties.ProviderConfig cfg, String key,
                                        String prompt, Consumer<String> onToken,
                                        boolean[] abortFlag, String baseUrl, String model) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "model", model,
            "stream", true,
            "max_tokens", 8192,
            "messages", List.of(Map.of("role","user","content",prompt))
        ));

        Request.Builder rb = new Request.Builder()
            .url(baseUrl + "/chat/completions")
            .addHeader("Authorization", "Bearer " + key)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(body, MediaType.parse("application/json")));

        if (baseUrl.contains("openrouter")) {
            rb.addHeader("HTTP-Referer", "https://ai-architect.app");
            rb.addHeader("X-Title", "AI Architect");
        }

        return executeStreaming(rb.build(), abortFlag, onToken, "openai-compat");
    }

    // ── SSE Reader ───────────────────────────────────────────────

    private String executeStreaming(Request request, boolean[] abortFlag,
                                     Consumer<String> onToken, String format) throws Exception {
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                if (response.code() == 429) throw new RateLimitException("429: " + err);
                throw new Exception("HTTP " + response.code() + ": " + err);
            }
            if (response.body() == null) throw new Exception("Empty response body");

            StringBuilder full = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (abortFlag[0]) break;
                    if (!line.startsWith("data: ")) continue;
                    String raw = line.substring(6).trim();
                    if (raw.isEmpty() || "[DONE]".equals(raw)) continue;
                    try {
                        String delta = "gemini".equals(format)
                            ? extractGeminiDelta(raw)
                            : extractOpenAIDelta(raw);
                        if (delta != null && !delta.isEmpty()) {
                            full.append(delta);
                            onToken.accept(delta);
                        }
                    } catch (Exception ignored) {}
                }
            }
            return full.toString();
        }
    }

    private String extractGeminiDelta(String raw) throws Exception {
        JsonNode n = objectMapper.readTree(raw);
        return n.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
    }

    private String extractOpenAIDelta(String raw) throws Exception {
        JsonNode n = objectMapper.readTree(raw);
        return n.path("choices").path(0).path("delta").path("content").asText(null);
    }

    // ── Key Rotation ─────────────────────────────────────────────

    private String rotateKey(String providerName, List<String> keys) {
        AtomicInteger idx = keyIndexes.computeIfAbsent(providerName, k -> new AtomicInteger(0));
        return keys.get(idx.getAndIncrement() % keys.size());
    }

    private ProviderProperties.ProviderConfig getConfig(String name) {
        return switch (name) {
            case "gemini"     -> props.getGemini();
            case "openrouter" -> props.getOpenrouter();
            case "groq"       -> props.getGroq();
            case "together"   -> props.getTogether();
            case "cerebras"   -> props.getCerebras();
            default           -> null;
        };
    }

    @FunctionalInterface public interface ProviderSwitchEvent { void accept(String provider, String model); }
    @FunctionalInterface public interface RetryEvent          { void accept(int attempt); }

    public static class RateLimitException extends Exception {
        public RateLimitException(String msg) { super(msg); }
    }
}
