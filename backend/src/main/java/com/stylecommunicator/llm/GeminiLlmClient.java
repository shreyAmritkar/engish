package com.stylecommunicator.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylecommunicator.config.LlmProperties;
import com.stylecommunicator.util.JsonResponseParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class GeminiLlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

    public GeminiLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Optional<Map<String, Object>> generateJson(String prompt, LlmTier tier) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("LLM API key not set (Gemini)");
            return Optional.empty();
        }
        String model = properties.modelFor(tier);
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":generateContent?key=" + properties.getApiKey();

            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(Map.of("text", prompt))
                    )),
                    "generationConfig", Map.of(
                            "temperature", 0.3,
                            "responseMimeType", "application/json"
                    )
            );

            var headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            var entity = new org.springframework.http.HttpEntity<>(body, headers);
            var response = restTemplate.exchange(url, org.springframework.http.HttpMethod.POST, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Gemini HTTP error: {}", response.getStatusCode());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            return JsonResponseParser.parse(text, objectMapper);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Gemini HTTP {} — body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Gemini call failed", e);
            return Optional.empty();
        }
    }
}
