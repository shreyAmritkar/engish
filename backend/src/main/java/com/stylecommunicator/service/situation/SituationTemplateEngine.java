package com.stylecommunicator.service.situation;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Converts a raw phrase from an external API (news headline, advice slip,
 * Wikipedia summary sentence) into a properly framed workplace situation.
 *
 * No LLM involved — pure string templates.
 */
@Component
public class SituationTemplateEngine {

    private static final Random RNG = new Random();

    // Templates per power tier. %s is replaced with the extracted topic phrase.
    private static final Map<String, List<String>> TEMPLATES = Map.of(
        "DOMINANT", List.of(
            "Your team is concerned about %s. As team lead, address this professionally.",
            "A client questions your decision about %s during a meeting. Respond clearly.",
            "A junior team member publicly challenges your position on %s. Handle it.",
            "Stakeholders want an explanation of your stance on %s. Provide one.",
            "Two colleagues disagree over %s and look to you to resolve it."
        ),
        "EQUAL", List.of(
            "You and a peer have different opinions on how to approach %s.",
            "Your colleague wants to change the team's plan regarding %s without discussion.",
            "You need to give honest feedback to a peer about their handling of %s.",
            "You and your PM see %s differently. Find a shared position.",
            "A partner team's new proposal about %s will increase your workload. Respond."
        ),
        "SUBMISSIVE", List.of(
            "You need to ask your manager to reconsider the approach to %s.",
            "You want to raise a concern about %s without damaging the relationship.",
            "You must push back on a decision about %s while staying professional.",
            "Your manager asks for your view on %s. Share it honestly but respectfully.",
            "You believe the team's approach to %s is wrong. Make your case upward."
        )
    );

    // Level-specific softening for A1/A2 — wraps around the base template
    private static final Map<String, String> LEVEL_PREFIX = Map.of(
        "A1", "In simple words: ",
        "A2", ""
    );

    /**
     * Applies a random template to the extracted topic.
     *
     * @param power  DOMINANT | EQUAL | SUBMISSIVE
     * @param level  A1 | A2 | B1 | B2
     * @param topic  a short phrase extracted from the external API response
     * @return a ready-to-use situation string
     */
    public String apply(String power, String level, String topic) {
        List<String> pool = TEMPLATES.getOrDefault(power.toUpperCase(), TEMPLATES.get("EQUAL"));
        String template = pool.get(RNG.nextInt(pool.size()));
        String situation = template.formatted(topic);

        // For A1 we prepend a gentle context cue so the sentence reads easier
        String prefix = LEVEL_PREFIX.getOrDefault(level.toUpperCase(), "");
        return prefix + situation;
    }

    /**
     * Extracts a short topic phrase (≤ 6 words) from a longer raw string.
     * Strategy: take the first sentence, strip it to the core noun phrase.
     */
    public String extractTopic(String raw) {
        if (raw == null || raw.isBlank()) return "a work challenge";

        // Take only the first sentence
        String first = raw.split("[.!?]")[0].trim();

        // Remove common filler openers
        first = first.replaceAll("(?i)^(always|never|try to|remember to|make sure to|be sure to)\\s+", "");

        // Truncate to 8 words max
        String[] words = first.split("\\s+");
        if (words.length <= 8) return first.toLowerCase();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i > 0) sb.append(' ');
            sb.append(words[i]);
        }
        return sb.toString().toLowerCase();
    }
}
