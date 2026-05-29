package com.stylecommunicator.service.situation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Pulls random advice slips from https://api.adviceslip.com
 *
 * Free, no API key, no rate-limit concerns at our volume.
 * The slip text is inverted into a workplace situation by SituationTemplateEngine.
 *
 * Keyword search endpoint: GET /advice/search/{keyword}
 * Random endpoint:         GET /advice
 */
@Component
public class AdviceSlipSource {

    private static final Logger log = LoggerFactory.getLogger(AdviceSlipSource.class);

    // Keywords that produce workplace-relevant advice
    private static final List<String> SEARCH_KEYWORDS = List.of(
        "conflict", "disagree", "listen", "speak", "work", "decision",
        "mistake", "trust", "respect", "communicate", "manage", "ask",
        "change", "pressure", "team"
    );

    private static final String SEARCH_URL = "https://api.adviceslip.com/advice/search/%s";
    private static final String RANDOM_URL  = "https://api.adviceslip.com/advice";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final SituationTemplateEngine templateEngine;

    public AdviceSlipSource(ObjectMapper objectMapper, SituationTemplateEngine templateEngine) {
        this.objectMapper = objectMapper;
        this.templateEngine = templateEngine;
    }

    /**
     * Fetches up to {@code count} situations.
     * Returns empty list on any error — callers treat it as a dry source.
     */
    public List<String> fetch(String power, String level, int count) {
        List<String> results = new ArrayList<>();

        // Try keyword search first — more relevant results
        for (String keyword : shuffled(SEARCH_KEYWORDS)) {
            if (results.size() >= count) break;
            try {
                String url = SEARCH_URL.formatted(keyword);
                String body = restTemplate.getForObject(url, String.class);
                List<String> parsed = parseSearchResponse(body, power, level);
                results.addAll(parsed);
            } catch (Exception e) {
                log.debug("AdviceSlip search failed for keyword '{}': {}", keyword, e.getMessage());
            }
        }

        // Top up with random slips if keyword search didn't fill the quota
        int remaining = count - results.size();
        for (int i = 0; i < remaining * 2 && results.size() < count; i++) {
            try {
                String body = restTemplate.getForObject(RANDOM_URL, String.class);
                String advice = parseRandomResponse(body);
                if (advice != null) {
                    results.add(templateEngine.apply(power, level, templateEngine.extractTopic(advice)));
                }
            } catch (Exception e) {
                log.debug("AdviceSlip random failed: {}", e.getMessage());
                break;
            }
        }

        return results.subList(0, Math.min(results.size(), count));
    }

    // ── Parsing ───────────────────────────────────────────────────────────

    private List<String> parseSearchResponse(String body, String power, String level) {
        List<String> out = new ArrayList<>();
        if (body == null) return out;
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode slips = root.path("slips");
            if (slips.isArray()) {
                for (JsonNode slip : slips) {
                    String advice = slip.path("advice").asText("").trim();
                    if (!advice.isBlank()) {
                        String topic = templateEngine.extractTopic(advice);
                        out.add(templateEngine.apply(power, level, topic));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("AdviceSlip parse error: {}", e.getMessage());
        }
        return out;
    }

    private String parseRandomResponse(String body) {
        if (body == null) return null;
        try {
            JsonNode root = objectMapper.readTree(body);
            String advice = root.path("slip").path("advice").asText("").trim();
            return advice.isBlank() ? null : advice;
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> shuffled(List<String> list) {
        List<String> copy = new ArrayList<>(list);
        java.util.Collections.shuffle(copy);
        return copy;
    }
}
