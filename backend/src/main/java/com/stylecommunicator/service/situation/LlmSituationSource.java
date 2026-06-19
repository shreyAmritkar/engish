package com.stylecommunicator.service.situation;

import com.stylecommunicator.llm.LlmClient;
import com.stylecommunicator.llm.LlmTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Last-resort source: asks the LLM to generate a batch of situations.
 *
 * This is ONLY invoked when both free external sources (AdviceSlip,
 * Wikipedia) returned fewer results than needed. It fires at most once
 * per replenishment cycle and uses the free/fast tier.
 */
@Component
public class LlmSituationSource {

    private static final Logger log = LoggerFactory.getLogger(LlmSituationSource.class);

    private final LlmClient llmClient;

    public LlmSituationSource(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public List<String> fetch(String power, String level, int count) {
        log.info("LlmSituationSource: generating {} situations (power={}, level={})",
                count, power, level);

        String prompt = buildPrompt(power, level, count);

        Map<String, Object> json = llmClient.generateJson(prompt, LlmTier.FAST);

        if (json == null || json.isEmpty()) {
            log.warn("LlmSituationSource: LLM returned empty — no fallback available");
            return List.of();
        }

        return extractList(json);
    }

    // ── Prompt ────────────────────────────────────────────────────────────

    private String buildPrompt(String power, String level, int count) {
        String levelDesc = switch (level.toUpperCase()) {
            case "A1" -> "very simple English, everyday workplace, one short sentence each";
            case "A2" -> "simple English, basic professional situations";
            case "B1" -> "intermediate English, common workplace challenges";
            default   -> "advanced English, complex professional and leadership situations";
        };
        String powerDesc = switch (power.toUpperCase()) {
            case "DOMINANT"   -> "where the user is in a position of authority or leadership over others";
            case "SUBMISSIVE" -> "where the user must make a request or pushback to someone above them";
            default           -> "between colleagues or peers at the same level";
        };

        return """
            Generate %d unique, realistic workplace communication scenarios.
            Language level: %s.
            Power dynamic: each scenario should be a situation %s.
            Rules:
            - Each scenario must be one sentence, max 25 words.
            - No numbering. No bullet points inside the text.
            - Each scenario must be different. No duplicates.
            Return JSON only, no markdown:
            {"situations": ["scenario 1", "scenario 2", ...]}
            """.formatted(count, levelDesc, powerDesc);
    }

    // ── Parsing ───────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<String> extractList(Map<String, Object> json) {
        Object raw = json.get("situations");
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                String s = String.valueOf(item).trim();
                if (!s.isBlank() && !s.equals("null")) out.add(s);
            }
            return out;
        }
        return List.of();
    }
}
