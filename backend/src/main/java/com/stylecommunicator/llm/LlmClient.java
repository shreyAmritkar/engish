package com.stylecommunicator.llm;

import com.stylecommunicator.exception.LlmUnavailableException;

import java.util.Map;

/**
 * Provider-agnostic LLM access via OpenRouter. Callers target a {@link LlmTier}
 * (FAST/QUALITY); the model id behind each tier is configured via env vars.
 *
 * @throws LlmUnavailableException if no model responds successfully
 */
public interface LlmClient {
    Map<String, Object> generateJson(String prompt, LlmTier tier);
}
