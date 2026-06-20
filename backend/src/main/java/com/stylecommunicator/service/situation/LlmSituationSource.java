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
            case "A1" -> "very simple English, short plain sentences, everyday workplace vocabulary";
            case "A2" -> "simple English, basic professional vocabulary";
            case "B1" -> "intermediate English, common workplace vocabulary";
            default   -> "advanced English, natural professional phrasing";
        };
        String powerDesc = switch (power.toUpperCase()) {
            case "DOMINANT"   -> "the user is in a position of authority or leadership over the other person";
            case "SUBMISSIVE" -> "the user must make a request or pushback to someone above them";
            default           -> "the user and the other person are colleagues or peers at the same level";
        };

        return """
            Generate %d unique, realistic workplace scenarios for a roleplay
            exercise. A learner will read each one and then write what they
            would actually say or write in response — so each scenario must
            give them enough concrete context to picture the situation and
            know exactly what they're responding to.

            Each scenario must include, in 2-3 short sentences:
            1. WHO is involved (their role, e.g. "your manager", "a teammate",
               "a client") — never just "someone" or "a colleague" with no detail.
            2. WHAT just happened or was just said — a specific, concrete event,
               not an abstract description of a task category.
            3. WHAT the user now needs to do or respond to — make the expected
               action obvious from the scenario itself.

            Power dynamic: %s.
            Language level: %s.

            Bad example (too abstract, reads like an instruction, not a scene):
            "You must diplomatically decline a senior executive's request for
            an unrealistic deadline."

            Good example (concrete, sets a scene, learner knows exactly what's
            happening):
            "Your VP just messaged you asking if the project can be finished by
            Friday — two weeks earlier than your team agreed on. She's waiting
            on your reply before the client call in an hour. You don't think
            it's realistic. Respond to her message."

            Rules:
            - Each scenario must be 2-3 sentences, max 45 words total.
            - No numbering. No bullet points inside the text.
            - Each scenario must be different. No duplicates.
            - End each scenario with a clear instruction of what the user
              should do (e.g. "Respond to her message.", "Reply to your
              teammate.", "Write what you'd say in the meeting.").

            Return JSON only, no markdown:
            {"situations": ["scenario 1", "scenario 2", ...]}
            """.formatted(count, powerDesc, levelDesc);
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
