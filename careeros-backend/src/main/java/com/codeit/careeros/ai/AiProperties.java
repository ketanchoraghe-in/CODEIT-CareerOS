package com.codeit.careeros.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Sprint 7 AI configuration surface. Everything comes from environment
 * variables — no API keys are hardcoded anywhere.
 *
 * <ul>
 *   <li>{@code AI_PROVIDER}: openai | gemini | ollama | custom | disabled</li>
 *   <li>{@code AI_MODEL}: chat model name (e.g. gpt-4o-mini, or a Gemini model
 *       such as gemini-2.0-flash when {@code AI_PROVIDER=gemini})</li>
 *   <li>{@code AI_BASE_URL}: OpenAI-compatible endpoint (provider default when blank)</li>
 *   <li>{@code AI_API_KEY}: secret key for the OpenAI-compatible providers;
 *       blank means the assistant is disabled</li>
 *   <li>{@code GEMINI_API_KEY}: secret key for {@code AI_PROVIDER=gemini};
 *       falls back to {@code AI_API_KEY} when blank so a single key env also works</li>
 *   <li>{@code GEMINI_MODEL}: optional Gemini model override (takes precedence
 *       over {@code AI_MODEL} when {@code AI_PROVIDER=gemini})</li>
 *   <li>{@code AI_MAX_TOOL_ITERATIONS}, {@code AI_HISTORY_LIMIT}: loop/history guards</li>
 *   <li>{@code AI_REQUEST_TIMEOUT_SECONDS}: per-provider-call wall-clock budget</li>
 *   <li>{@code AI_RATE_LIMIT_PER_MINUTE}: max chat turns per student per minute</li>
 *   <li>{@code AI_OFFLINE_FALLBACK}: when true (default), the built-in Smart
 *       Guidance engine answers even with no API key or when the provider
 *       fails — the chat NEVER goes silent like a dead chatbot.</li>
 * </ul>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private static final String UNCONFIGURED_SENTINEL = "__AI_NOT_CONFIGURED__";

    private String provider = "openai";
    private String model = "gpt-4o-mini";
    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey = "";
    /**
     * Gemini-specific overrides. Blank falls back to {@link #apiKey} /
     * {@link #model} so {@code AI_API_KEY}/{@code AI_MODEL} alone are enough
     * to run the Gemini provider.
     */
    private String geminiApiKey = "";
    private String geminiModel = "";
    private int maxToolIterations = 6;
    private int historyLimit = 20;
    /** Wall-clock budget for one provider call (seconds). Frontend mirrors this. */
    private int requestTimeoutSeconds = 120;
    /** Max chat turns per student per minute (simple in-memory abuse guard). */
    private int rateLimitPerMinute = 20;
    /**
     * Built-in Smart Guidance fallback. True by default so the assistant
     * ALWAYS responds (offline, grounded in CareerOS data) even with no
     * {@code AI_API_KEY} or when the LLM provider times out / errors.
     * Set {@code AI_OFFLINE_FALLBACK=false} to restore strict 503/502 errors.
     */
    private boolean offlineFallbackEnabled = true;

    /** True only when a real key is configured and the provider is not disabled. */
    public boolean isConfigured() {
        String effective = effectiveApiKey();
        return effective != null
                && !effective.isBlank()
                && !UNCONFIGURED_SENTINEL.equals(effective)
                && !"disabled".equalsIgnoreCase(provider);
    }

    /** True when the Gemini provider is selected via {@code AI_PROVIDER=gemini}. */
    public boolean isGemini() {
        return "gemini".equalsIgnoreCase(provider == null ? "" : provider.strip());
    }

    /**
     * API key for the active provider: {@code GEMINI_API_KEY} first, then
     * {@code AI_API_KEY} when the Gemini provider is selected.
     */
    public String effectiveApiKey() {
        if (isGemini() && geminiApiKey != null && !geminiApiKey.isBlank()) {
            return geminiApiKey;
        }
        return apiKey;
    }

    /**
     * Model for the active provider: {@code GEMINI_MODEL} first, then
     * {@code AI_MODEL}, then the per-provider default.
     */
    public String effectiveModel() {
        if (isGemini()) {
            if (geminiModel != null && !geminiModel.isBlank()) {
                return geminiModel.strip();
            }
            if (model != null && !model.isBlank() && !isOpenAiDefaultModelOnly()) {
                return model.strip();
            }
            return "gemini-2.0-flash";
        }
        return model;
    }

    /**
     * The shared {@code AI_MODEL} default is the OpenAI model. When the Gemini
     * provider is selected without an explicit model, prefer the Gemini default
     * instead of leaking {@code gpt-4o-mini} into Gemini calls/status.
     */
    private boolean isOpenAiDefaultModelOnly() {
        return "gpt-4o-mini".equals(model == null ? "" : model.strip())
                && (geminiModel == null || geminiModel.isBlank());
    }
}
