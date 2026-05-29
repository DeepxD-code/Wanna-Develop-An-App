package com.aiarchitect.provider;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * PhaseModelRouter
 * Routes each phase to its optimal model based on specialization.
 *
 * Phase → Optimal model reasoning:
 * 1  Ideation     → Gemini (creative, broad knowledge)
 * 2  Ranking      → Gemini (analytical reasoning)
 * 3  Research     → Gemini (factual, up-to-date knowledge)
 * 4  Architecture → Groq/DeepSeek (structured, technical)
 * 5  Planning     → Gemini (strategic)
 * 6  API Design   → Groq (fast, code-aware)
 * 7  Code Gen     → Groq/Together (fastest code models)
 * 8  Error Check  → Gemini (critical analysis)
 * 9  Stress Test  → Groq (fast, structured output)
 * 10 Extensibility→ Gemini (visionary)
 * 11 Final Report → Gemini (synthesis, documentation)
 */
@Component
public class PhaseModelRouter {

    /**
     * Returns the preferred provider order for a given phase.
     * First in list = most preferred. Falls back through the list on failure.
     */
    public List<String> getProviderOrder(int phaseId) {
        return switch (phaseId) {
            case 1  -> List.of("gemini", "openrouter", "groq", "together", "cerebras");
            case 2  -> List.of("gemini", "openrouter", "groq", "together", "cerebras");
            case 3  -> List.of("gemini", "openrouter", "groq", "together", "cerebras");
            case 4  -> List.of("groq",   "gemini",     "openrouter", "together", "cerebras");
            case 5  -> List.of("gemini", "groq",       "openrouter", "together", "cerebras");
            case 6  -> List.of("groq",   "together",   "gemini",     "openrouter", "cerebras");
            case 7  -> List.of("groq",   "together",   "cerebras",   "gemini",    "openrouter");
            case 8  -> List.of("gemini", "groq",       "openrouter", "together",  "cerebras");
            case 9  -> List.of("groq",   "cerebras",   "gemini",     "together",  "openrouter");
            case 10 -> List.of("gemini", "openrouter", "groq",       "together",  "cerebras");
            case 11 -> List.of("gemini", "openrouter", "groq",       "together",  "cerebras");
            default -> List.of("gemini", "openrouter", "groq",       "together",  "cerebras");
        };
    }

    /**
     * Get the preferred model override for a provider+phase combination.
     * Returns null to use the provider's default model.
     */
    public String getModelOverride(String provider, int phaseId) {
        // Code phases: use fastest coding model
        if (phaseId == 7 && "groq".equals(provider)) {
            return "llama-3.3-70b-versatile";
        }
        if (phaseId == 7 && "together".equals(provider)) {
            return "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free";
        }
        if (phaseId == 7 && "cerebras".equals(provider)) {
            return "llama-3.3-70b";
        }

        // Architecture phases: use largest available model
        if (phaseId == 4 && "openrouter".equals(provider)) {
            return "meta-llama/llama-3.3-70b-instruct:free";
        }

        // Research phases: use most knowledgeable model
        if (phaseId == 3 && "gemini".equals(provider)) {
            return "gemini-2.0-flash";
        }

        return null; // use provider default
    }

    /**
     * Get display name for a phase's specialty.
     */
    public String getPhaseSpecialty(int phaseId) {
        return switch (phaseId) {
            case 1, 2, 3, 10, 11 -> "Gemini (research+reasoning)";
            case 4, 5             -> "Groq/Gemini (architecture)";
            case 6, 7, 9          -> "Groq/Together (code+speed)";
            case 8                -> "Gemini (critical analysis)";
            default               -> "Auto";
        };
    }
}
