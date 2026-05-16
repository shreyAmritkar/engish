package com.stylecommunicator.service;

import com.stylecommunicator.entity.PracticeSession;
import com.stylecommunicator.entity.StyleProfile;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AnalysisRouter {

    private static final List<String> SCORE_DIMENSIONS = List.of(
            "confidence", "tone", "persuasion", "emotional_control", "professionalism", "style_match"
    );

    private final GeminiService geminiService;
    private final GrammarCheckerService grammarCheckerService;
    private final StyleEngineService styleEngineService;

    public AnalysisRouter(
            GeminiService geminiService,
            GrammarCheckerService grammarCheckerService,
            StyleEngineService styleEngineService) {
        this.geminiService = geminiService;
        this.grammarCheckerService = grammarCheckerService;
        this.styleEngineService = styleEngineService;
    }

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

        Map<String, Object> result = geminiService.generateJson(prompt, false)
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

        return geminiService.generateJson(prompt, true).orElse(Map.of(
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
        return geminiService.generateJson(prompt, false)
                .map(m -> String.valueOf(m.getOrDefault("coaching_tip", "Focus on clarity and tone alignment with your target style.")))
                .orElse("Focus on clarity and tone alignment with your target style.");
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
