package com.stylecommunicator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylecommunicator.config.GeminiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*}");

    private final GeminiProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GeminiService(GeminiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    public Optional<Map<String, Object>> generateJson(String prompt, boolean usePro) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("GEMINI_API_KEY not set");
            return Optional.empty();
        }
        String model = usePro ? properties.getProModel() : properties.getFlashModel();
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

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Gemini HTTP error: {}", response.getStatusCode());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            if (text.isBlank()) {
                return Optional.empty();
            }
            return parseJsonFromText(text);
        } catch (Exception e) {
            log.error("Gemini call failed", e);
            return Optional.empty();
        }
    }

    private Optional<Map<String, Object>> parseJsonFromText(String text) {
        try {
            String trimmed = text.trim();
            if (trimmed.startsWith("```")) {
                trimmed = trimmed.replaceAll("^```json\\s*", "").replaceAll("^```\\s*", "").replaceAll("```$", "").trim();
            }
            return Optional.of(objectMapper.readValue(trimmed, new TypeReference<>() {}));
        } catch (Exception ignored) {
            Matcher matcher = JSON_BLOCK.matcher(text);
            if (matcher.find()) {
                try {
                    return Optional.of(objectMapper.readValue(matcher.group(), new TypeReference<>() {}));
                } catch (Exception e) {
                    log.warn("Failed to parse JSON from Gemini response");
                }
            }
        }
        return Optional.empty();
    }
}
