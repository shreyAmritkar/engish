package com.stylecommunicator.service.situation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Pulls recent business headlines from NewsData.io and converts them
 * into workplace situations using SituationTemplateEngine.
 *
 * Free tier: 200 requests/day, no credit card required.
 * API docs:  https://newsdata.io/documentation
 *
 * Set NEWSDATA_API_KEY in your .env file to enable this source.
 * If the key is absent the source returns an empty list gracefully —
 * the router will fall through to the next source.
 */
@Component
public class NewsSource {

    private static final Logger log = LoggerFactory.getLogger(NewsSource.class);

    private static final String BASE_URL = "https://newsdata.io/api/1/news";

    // These NewsData categories are most relevant to workplace situations
    private static final List<String> CATEGORIES = List.of("business", "technology");

    @Value("${situation.news.api-key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final SituationTemplateEngine templateEngine;

    public NewsSource(ObjectMapper objectMapper, SituationTemplateEngine templateEngine) {
        this.objectMapper = objectMapper;
        this.templateEngine = templateEngine;
    }

    /**
     * Returns up to {@code count} situations sourced from news headlines.
     * Returns an empty list silently if no API key is configured.
     */
    public List<String> fetch(String power, String level, int count) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("NewsSource: NEWSDATA_API_KEY not set — skipping");
            return List.of();
        }

        List<String> results = new ArrayList<>();

        for (String category : CATEGORIES) {
            if (results.size() >= count) break;
            try {
                String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                    .queryParam("apikey", apiKey)
                    .queryParam("category", category)
                    .queryParam("language", "en")
                    .queryParam("size", "10")
                    .toUriString();

                String body = restTemplate.getForObject(url, String.class);
                List<String> parsed = parseHeadlines(body, power, level);
                results.addAll(parsed);

            } catch (Exception e) {
                log.warn("NewsSource fetch failed for category '{}': {}", category, e.getMessage());
            }
        }

        return results.subList(0, Math.min(results.size(), count));
    }

    // ── Parsing ───────────────────────────────────────────────────────────

    private List<String> parseHeadlines(String body, String power, String level) {
        List<String> out = new ArrayList<>();
        if (body == null) return out;

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode results = root.path("results");

            if (!results.isArray()) return out;

            for (JsonNode article : results) {
                // Prefer description over title — more substance
                String text = article.path("description").asText("").trim();
                if (text.isBlank()) {
                    text = article.path("title").asText("").trim();
                }
                if (text.isBlank()) continue;

                // Skip clickbait / listicles
                if (text.contains("?") || text.length() < 30) continue;

                String topic = templateEngine.extractTopic(text);
                out.add(templateEngine.apply(power, level, topic));
            }
        } catch (Exception e) {
            log.debug("NewsSource parse error: {}", e.getMessage());
        }
        return out;
    }
}
