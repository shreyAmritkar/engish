package com.stylecommunicator.config;

import com.stylecommunicator.llm.LlmProvider;
import com.stylecommunicator.llm.LlmTier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** openrouter (recommended) or gemini (direct Google API — legacy) */
    private LlmProvider provider = LlmProvider.OPENROUTER;

    /** OpenRouter: OPENROUTER_API_KEY. Gemini direct: GEMINI_API_KEY */
    private String apiKey = "";

    /** Primary model for FAST tier (extraction, scoring, tips) */
    private String fastModel = "google/gemini-2.0-flash-001";

    /** Primary model for QUALITY tier (rewrites) */
    private String qualityModel = "google/gemini-2.0-flash-001";

    /** Tried in order when primary returns 429 / rate-limited */
    private List<String> fallbackModels = List.of(
            "google/gemini-2.0-flash-001",
            "openai/gpt-4o-mini"
    );

    private int retryMaxAttempts = 3;
    private long retryBackoffMs = 2000;

    private final OpenRouter openrouter = new OpenRouter();

    public LlmProvider getProvider() {
        return provider;
    }

    public void setProvider(LlmProvider provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getFastModel() {
        return fastModel;
    }

    public void setFastModel(String fastModel) {
        this.fastModel = fastModel;
    }

    public String getQualityModel() {
        return qualityModel;
    }

    public void setQualityModel(String qualityModel) {
        this.qualityModel = qualityModel;
    }

    public OpenRouter getOpenrouter() {
        return openrouter;
    }

    public String modelFor(LlmTier tier) {
        return tier == LlmTier.QUALITY ? qualityModel : fastModel;
    }

    public List<String> getFallbackModels() {
        return fallbackModels;
    }

    public void setFallbackModels(List<String> fallbackModels) {
        this.fallbackModels = fallbackModels;
    }

    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public void setRetryMaxAttempts(int retryMaxAttempts) {
        this.retryMaxAttempts = retryMaxAttempts;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    /** Primary model first, then fallbacks (deduped, preserves order). */
    public List<String> modelsToTry(LlmTier tier) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ordered.add(modelFor(tier));
        if (fallbackModels != null) {
            ordered.addAll(fallbackModels);
        }
        return new ArrayList<>(ordered);
    }

    public static class OpenRouter {
        private String baseUrl = "https://openrouter.ai/api/v1";
        private String siteUrl = "http://localhost:3000";
        private String appName = "Style Communicator";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getSiteUrl() {
            return siteUrl;
        }

        public void setSiteUrl(String siteUrl) {
            this.siteUrl = siteUrl;
        }

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }
    }
}
