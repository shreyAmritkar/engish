package com.stylecommunicator.llm;

import java.util.Map;
import java.util.Optional;

/**
 * Provider-agnostic LLM access. Implementations: Gemini, OpenRouter.
 * Callers use {@link LlmTier} — not a specific model id.
 */
public interface LlmClient {

    Optional<Map<String, Object>> generateJson(String prompt, LlmTier tier);
}
