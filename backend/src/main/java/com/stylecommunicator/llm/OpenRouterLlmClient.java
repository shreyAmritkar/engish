package com.stylecommunicator.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylecommunicator.config.LlmProperties;
import com.stylecommunicator.util.JsonResponseParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class OpenRouterLlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterLlmClient.class);

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public OpenRouterLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Optional<Map<String, Object>> generateJson(String prompt, LlmTier tier) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("OPENROUTER_API_KEY not set");
            return Optional.empty();
        }

        List<String> models = properties.modelsToTry(tier);
        int maxAttempts = Math.max(1, properties.getRetryMaxAttempts());

        for (String model : models) {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    Optional<Map<String, Object>> result = callOnce(prompt, model);
                    if (result.isPresent()) {
                        if (!model.equals(properties.modelFor(tier))) {
                            log.info("OpenRouter succeeded with fallback model: {}", model);
                        }
                        return result;
                    }
                } catch (RateLimitedException e) {
                    log.warn("OpenRouter 429 on model {} (attempt {}/{}): {}",
                            model, attempt, maxAttempts, e.getMessage());
                    if (attempt < maxAttempts) {
                        sleep(properties.getRetryBackoffMs() * attempt);
                    }
                } catch (HttpClientErrorException e) {
                    if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                        log.warn("OpenRouter 429 on model {} — trying next model if available", model);
                        break;
                    }
                    log.error("OpenRouter HTTP {} on model {} — body: {}",
                            e.getStatusCode(), model, e.getResponseBodyAsString());
                    break;
                } catch (Exception e) {
                    log.error("OpenRouter call failed for model {}", model, e);
                    break;
                }
            }
        }

        log.error("All OpenRouter models exhausted for tier {} — using app fallbacks", tier);
        return Optional.empty();
    }

    private Optional<Map<String, Object>> callOnce(String prompt, String model) {
        LlmProperties.OpenRouter or = properties.getOpenrouter();
        String url = or.getBaseUrl().replaceAll("/$", "") + "/chat/completions";

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.3,
                "response_format", Map.of("type", "json_object")
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());
        if (or.getSiteUrl() != null && !or.getSiteUrl().isBlank()) {
            headers.set("HTTP-Referer", or.getSiteUrl());
        }
        if (or.getAppName() != null && !or.getAppName().isBlank()) {
            headers.set("X-Title", or.getAppName());
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw new RateLimitedException(parseRateLimitMessage(e));
            }
            throw e;
        }

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.warn("OpenRouter HTTP error: {} for model {}", response.getStatusCode(), model);
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            String text = root.path("choices").path(0).path("message").path("content").asText("");
            return JsonResponseParser.parse(text, objectMapper);
        } catch (Exception e) {
            log.error("Failed to parse OpenRouter response for model {}", model, e);
            return Optional.empty();
        }
    }

    private static String parseRateLimitMessage(HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        if (body != null && body.contains("rate-limited")) {
            return "upstream rate limit (try another model or add BYOK key on OpenRouter)";
        }
        return "rate limited";
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class RateLimitedException extends RuntimeException {
        RateLimitedException(String message) {
            super(message);
        }
    }
}
