package com.stylecommunicator.llm;

import java.util.Map;
import java.util.Optional;

/**
 * Provider-agnostic LLM access via OpenRouter. Callers target a {@link LlmTier}
 * (FAST/QUALITY); the model id behind each tier (Gemini, GPT-4o, DeepSeek, etc.)
 * is configured via env vars, not chosen in code.
 * Callers use {@link LlmTier} — not a specific model id.
 */
public interface LlmClient {

    Optional<Map<String, Object>> generateJson(String prompt, LlmTier tier);
}
