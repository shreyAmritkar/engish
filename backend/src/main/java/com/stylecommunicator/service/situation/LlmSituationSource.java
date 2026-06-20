package com.stylecommunicator.service.situation;

import com.stylecommunicator.exception.LlmUnavailableException;
import com.stylecommunicator.llm.LlmClient;
import com.stylecommunicator.llm.LlmTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class LlmSituationSource {

    private static final Logger log = LoggerFactory.getLogger(LlmSituationSource.class);

    private final LlmClient llmClient;

    public LlmSituationSource(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /** Backward-compatible — defaults to PROFESSIONAL context. */
    public List<String> fetch(String power, String level, int count) {
        return fetch(power, level, "PROFESSIONAL", count);
    }

    public List<String> fetch(String power, String level, String context, int count) {
        log.info("LlmSituationSource: generating {} situations (power={} level={} context={})",
                 count, power, level, context);
        try {
            Map<String, Object> json = llmClient.generateJson(
                buildPrompt(power, level, context, count), LlmTier.FAST);
            return extractList(json);
        } catch (LlmUnavailableException e) {
            log.warn("LLM unavailable for situation generation: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildPrompt(String power, String level, String context, int count) {
        String levelDesc = switch (level.toUpperCase()) {
            case "A1" -> "very simple English, short sentences, everyday vocabulary";
            case "A2" -> "simple English, basic sentences, common words";
            case "B1" -> "intermediate English, some complexity, clear structure";
            default   -> "advanced English, complex situations, nuanced language";
        };

        String powerDesc = switch (power.toUpperCase()) {
            case "DOMINANT"   -> "The user is the leader, team lead, or senior person. The other person is pushing back or causing a problem.";
            case "SUBMISSIVE" -> "The user is junior or mid-level. The other person is a manager or someone with authority. The user needs to push back or ask for something difficult.";
            default           -> "The user and the other person are peers at the same level. There is a disagreement or tension between them.";
        };

        String contextDesc = switch (context.toUpperCase()) {
            case "CASUAL" -> """
                Tone: casual, everyday, social. NOT workplace or professional.
                Settings: friends, family, social groups, personal life.
                Examples: "your friend is about to make a bad decision",
                          "someone in your group keeps ignoring your input",
                          "you need to call out a friend without losing them".
                Keep it real and relatable — not corporate.""";
            case "DRAMATIC" -> """
                Tone: high-stakes, emotionally charged, larger-than-life.
                Settings: any — but the stakes feel significant. Something important is at risk.
                Examples: "everything your group built is at risk because of one bad call",
                          "your closest ally is about to make an irreversible mistake",
                          "you've stayed silent too long — it's now or never".
                Use vivid, urgent language. Make it feel meaningful.""";
            default -> """
                Tone: professional, workplace, formal or semi-formal.
                Settings: office, team meetings, work emails, corporate environments.
                Examples: "a colleague challenges your decision in front of the team",
                          "your manager asks you to take on work outside your role",
                          "you need to push back on a deadline without damaging the relationship".""";
        };

        return """
            Generate %d unique communication practice scenarios.
            Language level: %s.
            Power dynamic: %s
            Context and tone:
            %s

            FORMAT — every scenario must follow this exact structure:
            "You are the [user role]. Your [their role] [their name] just [action + medium]: \\"[what they said].\\" Write your reply. Goal: [what the user needs to accomplish]."

            Rules:
            - Match the context tone exactly — do NOT generate corporate situations for CASUAL/DRAMATIC
            - [user role] and [their role] should fit the context (friend/teammate for casual, ally/rival for dramatic, manager/colleague for professional)
            - [their name]: a realistic first name
            - Keep each scenario to 2–3 sentences max
            - Each scenario must be unique
            - Return JSON only, no markdown: {"situations": ["...", "..."]}
            """.formatted(count, levelDesc, powerDesc, contextDesc);
    }

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
