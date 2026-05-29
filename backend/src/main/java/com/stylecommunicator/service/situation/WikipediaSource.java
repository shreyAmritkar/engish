package com.stylecommunicator.service.situation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fetches Wikipedia article summaries as situation seeds.
 *
 * Free, no key, no rate limits at our volume.
 * Uses the REST summary endpoint: GET /page/summary/{title}
 *
 * The summary's first sentence is extracted and converted into a
 * workplace situation via SituationTemplateEngine.
 */
@Component
public class WikipediaSource {

    private static final Logger log = LoggerFactory.getLogger(WikipediaSource.class);

    private static final String SUMMARY_URL =
        "https://en.wikipedia.org/api/rest_v1/page/summary/%s";

    /**
     * Curated Wikipedia articles relevant to each power tier.
     * These are stable, high-quality articles about real workplace concepts.
     */
    private static final Map<String, List<String>> ARTICLES_BY_POWER = Map.of(
        "DOMINANT", List.of(
            "Leadership", "Conflict_resolution", "Crisis_management",
            "Performance_management", "Delegation", "Decision-making",
            "Mentorship", "Organizational_behavior", "Change_management",
            "Executive_communication", "Team_management", "Project_management",
            "Accountability", "Feedback", "Stakeholder_management"
        ),
        "EQUAL", List.of(
            "Negotiation", "Collaboration", "Peer_review",
            "Cross-functional_team", "Active_listening",
            "Constructive_criticism", "Conflict_of_interest",
            "Compromise", "Consensus_decision-making", "Mediation",
            "Interpersonal_communication", "Teamwork", "Trust",
            "Workplace_communication", "Cooperation"
        ),
        "SUBMISSIVE", List.of(
            "Assertiveness", "Self-advocacy", "Workplace_bullying",
            "Psychological_safety", "Asking_for_help",
            "Work-life_balance", "Burnout_(psychology)",
            "Imposter_syndrome", "Performance_appraisal",
            "Salary_negotiation", "Professional_development",
            "Boundary_(personal_space)", "Self-confidence",
            "Workplace_stress", "Career_development"
        )
    );

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final SituationTemplateEngine templateEngine;

    public WikipediaSource(ObjectMapper objectMapper, SituationTemplateEngine templateEngine) {
        this.objectMapper = objectMapper;
        this.templateEngine = templateEngine;
    }

    /**
     * Fetches up to {@code count} situations from Wikipedia summaries.
     */
    public List<String> fetch(String power, String level, int count) {
        List<String> articles = shuffled(
            ARTICLES_BY_POWER.getOrDefault(power.toUpperCase(), ARTICLES_BY_POWER.get("EQUAL"))
        );

        List<String> results = new ArrayList<>();

        for (String article : articles) {
            if (results.size() >= count) break;
            try {
                String url = SUMMARY_URL.formatted(article);
                String body = restTemplate.getForObject(url, String.class);
                String sentence = extractFirstSentence(body);
                if (sentence != null) {
                    String topic = templateEngine.extractTopic(sentence);
                    results.add(templateEngine.apply(power, level, topic));
                }
            } catch (Exception e) {
                log.debug("Wikipedia fetch failed for article '{}': {}", article, e.getMessage());
            }
        }

        return results;
    }

    // ── Parsing ───────────────────────────────────────────────────────────

    private String extractFirstSentence(String body) {
        if (body == null) return null;
        try {
            JsonNode root = objectMapper.readTree(body);

            // Wikipedia REST API returns 'extract' — plain text summary
            String extract = root.path("extract").asText("").trim();
            if (extract.isBlank()) return null;

            // Take only the first sentence (stop at first period followed by space or end)
            int dotIdx = extract.indexOf(". ");
            String first = dotIdx > 0 ? extract.substring(0, dotIdx) : extract;

            // Skip disambiguation pages ("X may refer to...")
            if (first.contains("may refer to") || first.contains("disambiguation")) return null;

            return first.trim();
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
