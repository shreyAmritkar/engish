package com.stylecommunicator.llm;

import com.stylecommunicator.config.LlmProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Single entry point for all AI calls. Delegates to Gemini or OpenRouter based on {@code llm.provider}.
 */
@Service
@Primary
public class RoutingLlmClient implements LlmClient {

    private final LlmProperties properties;
    private final GeminiLlmClient gemini;
    private final OpenRouterLlmClient openRouter;

    public RoutingLlmClient(LlmProperties properties, GeminiLlmClient gemini, OpenRouterLlmClient openRouter) {
        this.properties = properties;
        this.gemini = gemini;
        this.openRouter = openRouter;
    }

    @Override
    public Optional<Map<String, Object>> generateJson(String prompt, LlmTier tier) {
        return switch (properties.getProvider()) {
            case GEMINI -> gemini.generateJson(prompt, tier);
            case OPENROUTER -> openRouter.generateJson(prompt, tier);
        };
    }
}
