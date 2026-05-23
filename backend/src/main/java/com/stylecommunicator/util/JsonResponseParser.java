package com.stylecommunicator.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonResponseParser {

    private static final Logger log = LoggerFactory.getLogger(JsonResponseParser.class);
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*}");

    private JsonResponseParser() {}

    public static Optional<Map<String, Object>> parse(String text, ObjectMapper objectMapper) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            String trimmed = text.trim();
            if (trimmed.startsWith("```")) {
                trimmed = trimmed
                        .replaceAll("^```json\\s*", "")
                        .replaceAll("^```\\s*", "")
                        .replaceAll("```$", "")
                        .trim();
            }
            return Optional.of(objectMapper.readValue(trimmed, new TypeReference<>() {}));
        } catch (Exception ignored) {
            Matcher matcher = JSON_BLOCK.matcher(text);
            if (matcher.find()) {
                try {
                    return Optional.of(objectMapper.readValue(matcher.group(), new TypeReference<>() {}));
                } catch (Exception e) {
                    log.warn("Failed to parse JSON from LLM response");
                }
            }
        }
        return Optional.empty();
    }
}
