package com.aiarchitect.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "providers")
public class ProviderProperties {

    private ProviderConfig gemini     = new ProviderConfig();
    private ProviderConfig openrouter = new ProviderConfig();
    private ProviderConfig groq       = new ProviderConfig();
    private ProviderConfig together   = new ProviderConfig();
    private ProviderConfig cerebras   = new ProviderConfig();

    public ProviderConfig getGemini()     { return gemini; }
    public ProviderConfig getOpenrouter() { return openrouter; }
    public ProviderConfig getGroq()       { return groq; }
    public ProviderConfig getTogether()   { return together; }
    public ProviderConfig getCerebras()   { return cerebras; }

    public void setGemini(ProviderConfig v)     { gemini = v; }
    public void setOpenrouter(ProviderConfig v) { openrouter = v; }
    public void setGroq(ProviderConfig v)       { groq = v; }
    public void setTogether(ProviderConfig v)   { together = v; }
    public void setCerebras(ProviderConfig v)   { cerebras = v; }

    public Map<String, ProviderConfig> asOrderedMap() {
        return Map.of(
            "gemini", gemini, "openrouter", openrouter,
            "groq", groq, "together", together, "cerebras", cerebras
        );
    }

    public static class ProviderConfig {
        private List<String> keys         = new ArrayList<>();
        private String       baseUrl      = "";
        private String       model        = "";
        private String       fallbackModel = "";
        private int          rpmLimit     = 10;
        private boolean      enabled      = false;

        public List<String> getKeys()          { return keys; }
        public String       getBaseUrl()       { return baseUrl; }
        public String       getModel()         { return model; }
        public String       getFallbackModel() { return fallbackModel; }
        public int          getRpmLimit()      { return rpmLimit; }
        public boolean      isEnabled()        { return enabled; }

        public void setKeys(List<String> v)          { keys = v; }
        public void setBaseUrl(String v)             { baseUrl = v; }
        public void setModel(String v)               { model = v; }
        public void setFallbackModel(String v)       { fallbackModel = v; }
        public void setRpmLimit(int v)               { rpmLimit = v; }
        public void setEnabled(boolean v)            { enabled = v; }

        public List<String> getValidKeys() {
            return keys.stream()
                .filter(k -> k != null && !k.isBlank() && !k.startsWith("YOUR_"))
                .toList();
        }

        public boolean hasValidKeys() { return !getValidKeys().isEmpty(); }
    }
}
