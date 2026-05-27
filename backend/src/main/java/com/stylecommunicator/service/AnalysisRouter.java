package com.stylecommunicator.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.stylecommunicator.entity.PracticeSession;
import com.stylecommunicator.entity.StyleProfile;
import com.stylecommunicator.llm.LlmClient;
import com.stylecommunicator.llm.LlmTier;

@Service
public class AnalysisRouter {

    private static final List<String> SCORE_DIMENSIONS = List.of(
            "confidence", "tone", "persuasion", "emotional_control", "professionalism", "style_match"
    );

    private final LlmClient llmClient;
    private final GrammarCheckerService grammarCheckerService;
    private final StyleEngineService styleEngineService;

    public AnalysisRouter(
            LlmClient llmClient,
            GrammarCheckerService grammarCheckerService,
            StyleEngineService styleEngineService) {
        this.llmClient = llmClient;
        this.grammarCheckerService = grammarCheckerService;
        this.styleEngineService = styleEngineService;
    }

    // ── Existing single-response analysis (unchanged) ─────────────────────────

    public Map<String, Object> analyze(PracticeSession session, StyleProfile style, String userResponse) {
        String compressed = styleEngineService.getCompressedPrompt(style);
        String prompt = """
                %s
                Situation: %s
                Required word: "%s"
                Emotional context: %s
                User: "%s"
                Score 0-100: confidence, tone, persuasion, emotional_control, professionalism, style_match.
                Return JSON:
                {"scores":{"confidence":85},"grammar_notes":[],"misinterpretation_warnings":[]}
                """.formatted(
                compressed,
                session.getSituation(),
                session.getRequiredWord(),
                session.getEmotionalContext(),
                escape(userResponse)
        );

        Map<String, Object> result = llmClient.generateJson(prompt, LlmTier.FAST)
                .orElseGet(this::defaultAnalysis);

        List<String> ruleGrammar = grammarCheckerService.check(userResponse);
        @SuppressWarnings("unchecked")
        List<String> aiGrammar = result.get("grammar_notes") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList()
                : List.of();
        List<String> mergedGrammar = new ArrayList<>(ruleGrammar);
        mergedGrammar.addAll(aiGrammar);

        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("scores", normalizeScores(result.get("scores")));
        feedback.put("grammar_notes", mergedGrammar.stream().distinct().toList());
        feedback.put("misinterpretation_warnings", listValue(result.get("misinterpretation_warnings")));
        feedback.put("rewrites", null);
        feedback.put("coaching_tip", null);
        return feedback;
    }

    public Map<String, Object> generateRewrites(PracticeSession session, StyleProfile style, String userResponse) {
        String prompt = """
                Rewrite the user's response to be more assertive, then more diplomatic.
                Original: "%s"
                Target style: %s, formal %d.
                Return JSON: {"assertive":"...","diplomatic":"..."}
                """.formatted(
                escape(userResponse),
                style.getPowerDynamic(),
                style.getFormalityLevel() != null ? style.getFormalityLevel() : 5
        );

        return llmClient.generateJson(prompt, LlmTier.QUALITY).orElse(Map.of(
                "assertive", userResponse,
                "diplomatic", userResponse
        ));
    }

    public String generateCoachingTip(PracticeSession session, StyleProfile style, Map<String, Object> feedback) {
        @SuppressWarnings("unchecked")
        Map<String, Object> scores = feedback.get("scores") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();
        String prompt = """
                Give one short coaching tip (max 2 sentences) for improving workplace communication.
                Style: %s. Weakest score dimension: %s. Situation: %s
                Return JSON: {"coaching_tip":"..."}
                """.formatted(
                style.getPowerDynamic(),
                findWeakest(scores),
                session.getSituation()
        );
        return llmClient.generateJson(prompt, LlmTier.FAST)
                .map(m -> String.valueOf(m.getOrDefault("coaching_tip", "Focus on clarity and tone alignment with your target style.")))
                .orElse("Focus on clarity and tone alignment with your target style.");
    }

    // ── Multi-turn additions ──────────────────────────────────────────────────

    /**
     * Generates the "other party" reply after the user sends a message.
     * The character reacts realistically — it doesn't fold immediately.
     *
     * @param history   full conversation so far (EXCLUDING the message just sent)
     * @param userMessage the message the user just typed
     */
    public String generateCharacterReply(
            String situation,
            String emotionalContext,
            List<Map<String, Object>> history,
            String userMessage,
            String powerDynamic) {

        String historyText = buildHistoryText(history);

        String prompt = """
                You are the other party in this workplace scenario: %s
                Your emotional state: %s. Power context: %s.
                Conversation so far:
                %s
                The user just said: "%s"
                Reply as the other party (2-3 sentences). Be realistic — push back, ask follow-up questions, \
                or react naturally. Do NOT resolve the situation too quickly; keep the tension alive for \
                at least 2 exchanges. Stay in character.
                Return JSON: {"reply": "..."}
                """.formatted(
                situation,
                emotionalContext,
                powerDynamic != null ? powerDynamic : "EQUAL",
                historyText,
                escape(userMessage)
        );

        return llmClient.generateJson(prompt, LlmTier.FAST)
                .map(m -> String.valueOf(m.getOrDefault("reply", "I hear you, but I'm not fully convinced yet.")))
                .orElse("Let me think about that.");
    }

    /**
     * Scores the user's overall performance across the full multi-turn conversation.
     */
    public Map<String, Object> analyzeConversation(
            PracticeSession session,
            StyleProfile style,
            List<Map<String, Object>> history) {

        String compressed = styleEngineService.getCompressedPrompt(style);
        String historyText = buildHistoryText(history);

        // Gather all user messages to check required word and run grammar on them
        String allUserText = history.stream()
                .filter(t -> "user".equals(t.get("role")))
                .map(t -> String.valueOf(t.get("text")))
                .collect(Collectors.joining(" "));

        boolean requiredWordUsed = session.getRequiredWord() != null
                && allUserText.toLowerCase().contains(session.getRequiredWord().toLowerCase());

        String prompt = """
                %s
                Situation: %s
                Required word: "%s" — used by user: %s
                Emotional context: %s
                Full conversation (score the USER's turns only):
                %s
                Score the user 0-100 across: confidence, tone, persuasion, emotional_control, professionalism, style_match.
                Consider the whole arc: did they adapt, hold their position, stay on style?
                Return JSON:
                {"scores":{"confidence":85},"grammar_notes":[],"misinterpretation_warnings":[],"conversation_summary":"One sentence on how the user handled the situation."}
                """.formatted(
                compressed,
                session.getSituation(),
                session.getRequiredWord(),
                requiredWordUsed ? "yes" : "no — penalise style_match accordingly",
                session.getEmotionalContext(),
                historyText
        );

        Map<String, Object> result = llmClient.generateJson(prompt, LlmTier.FAST)
                .orElseGet(this::defaultAnalysis);

        List<String> ruleGrammar = grammarCheckerService.check(allUserText);
        @SuppressWarnings("unchecked")
        List<String> aiGrammar = result.get("grammar_notes") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList()
                : List.of();
        List<String> mergedGrammar = new ArrayList<>(ruleGrammar);
        mergedGrammar.addAll(aiGrammar);

        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("scores", normalizeScores(result.get("scores")));
        feedback.put("grammar_notes", mergedGrammar.stream().distinct().toList());
        feedback.put("misinterpretation_warnings", listValue(result.get("misinterpretation_warnings")));
        feedback.put("conversation_summary", result.getOrDefault("conversation_summary", ""));
        feedback.put("rewrites", null);
        feedback.put("coaching_tip", null);
        return feedback;
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private String buildHistoryText(List<Map<String, Object>> history) {
        if (history == null || history.isEmpty()) return "(no prior exchanges)";
        return history.stream()
                .map(t -> {
                    String role = "character".equals(t.get("role")) ? "Other party" : "User";
                    return role + ": " + t.get("text");
                })
                .collect(Collectors.joining("\n"));
    }

    private Map<String, Object> defaultAnalysis() {
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (String dim : SCORE_DIMENSIONS) {
            scores.put(dim, 50);
        }
        return Map.of(
                "scores", scores,
                "grammar_notes", List.of("AI scoring unavailable — default scores applied."),
                "misinterpretation_warnings", List.of()
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> normalizeScores(Object scoresObj) {
        Map<String, Integer> normalized = new LinkedHashMap<>();
        for (String dim : SCORE_DIMENSIONS) {
            normalized.put(dim, 50);
        }
        if (scoresObj instanceof Map<?, ?> map) {
            for (String dim : SCORE_DIMENSIONS) {
                Object val = map.get(dim);
                if (val instanceof Number n) {
                    normalized.put(dim, clamp(n.intValue()));
                }
            }
        }
        return normalized;
    }

    private List<String> listValue(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String findWeakest(Map<String, Object> scores) {
        return scores.entrySet().stream()
                .min(Comparator.comparingInt(e -> {
                    Object v = e.getValue();
                    return v instanceof Number n ? n.intValue() : 50;
                }))
                .map(Map.Entry::getKey)
                .orElse("tone");
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private String escape(String text) {
        return text == null ? "" : text.replace("\"", "\\\"");
    }
}