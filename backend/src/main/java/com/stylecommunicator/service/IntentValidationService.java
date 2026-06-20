package com.stylecommunicator.service;

import com.stylecommunicator.exception.LlmUnavailableException;
import com.stylecommunicator.llm.LlmClient;
import com.stylecommunicator.llm.LlmTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Gate that runs BEFORE AnalysisRouter.analyze(). Rejects responses that
 * are not a genuine attempt to respond to the situation — keyboard mash,
 * single words, repeated junk, or empty input — so they never reach the
 * (paid-capacity) scoring LLM call and never earn real scores.
 *
 * Two layers:
 *  1. Fast heuristics (no LLM cost) — catch obvious junk immediately.
 *  2. LLM classification — only for borderline cases the heuristics can't
 *     confidently decide, so this adds at most one extra FAST-tier call,
 *     and only when genuinely needed.
 */
@Service
public class IntentValidationService {

    private static final Logger log = LoggerFactory.getLogger(IntentValidationService.class);

    private static final Set<String> WORKPLACE_SIGNALS = Set.of(
        "i", "we", "you", "your", "our", "the", "this", "that", "understand",
        "think", "feel", "believe", "want", "need", "would", "could", "should",
        "will", "let", "please", "thank", "sorry", "agree", "disagree",
        "concern", "issue", "proposal", "deadline", "project", "team", "work",
        "meeting", "schedule", "priority", "support", "help", "feedback",
        "however", "therefore", "although", "appreciate"
    );

    private static final Pattern MASH_PATTERN =
        Pattern.compile("^[a-z]{1,3}([a-z])\\1{2,}[a-z]{0,5}$|^[^a-zA-Z ]{1,10}$");

    private static final int MIN_WORDS = 5;
    private static final int MIN_DISTINCT_WORDS = 3;

    private final LlmClient llmClient;

    public IntentValidationService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public ValidationResult validate(String userResponse, String situation) {
        if (userResponse == null || userResponse.isBlank()) {
            return ValidationResult.fail("Please write a response to the situation.");
        }

        String trimmed = userResponse.trim();
        String[] words = trimmed.split("\\s+");

        if (words.length < MIN_WORDS) {
            return ValidationResult.fail(
                "Your response is too short. Try writing at least a sentence or two " +
                "that addresses the situation."
            );
        }

        if (words.length == 1 && MASH_PATTERN.matcher(trimmed.toLowerCase()).matches()) {
            return ValidationResult.fail(
                "This doesn't look like a response to the situation. " +
                "Try writing what you would actually say in this workplace scenario."
            );
        }

        long distinctWords = Set.of(words).stream()
                .map(String::toLowerCase)
                .distinct()
                .count();
        if (distinctWords < MIN_DISTINCT_WORDS) {
            return ValidationResult.fail(
                "Your response needs more variety. Try writing a genuine reply " +
                "to the situation using your own words."
            );
        }

        long signalCount = Set.of(words).stream()
                .map(w -> w.toLowerCase().replaceAll("[^a-z]", ""))
                .filter(WORKPLACE_SIGNALS::contains)
                .count();

        if (signalCount >= 4) {
            log.debug("Intent validation: heuristic pass (signals={})", signalCount);
            return ValidationResult.pass();
        }

        return validateWithLlm(trimmed, situation);
    }

    private ValidationResult validateWithLlm(String userResponse, String situation) {
        String prompt = """
                You are checking whether a learner's text is a genuine attempt to respond
                to a workplace scenario, even if imperfect.

                Scenario:
                %s

                Learner's response:
                "%s"

                Decide:
                - VALID if the response addresses the scenario in any meaningful way
                  (it can be short, informal, or imperfect — it just needs to be a real attempt)
                - INVALID if the response is clearly nonsense, random characters, unrelated
                  content, or shows zero engagement with the scenario

                Return ONLY valid JSON, no markdown:
                {"valid": true, "reason": ""}
                or
                {"valid": false, "reason": "One friendly sentence explaining why it's not a valid response."}
                """.formatted(truncate(situation, 400), truncate(userResponse, 500));

        try {
            Map<String, Object> result = llmClient.generateJson(prompt, LlmTier.FAST);
            Object validObj = result.get("valid");
            boolean valid = validObj instanceof Boolean b ? b
                    : Boolean.parseBoolean(String.valueOf(validObj));
            String reason = String.valueOf(result.getOrDefault("reason", "")).trim();

            log.debug("Intent validation LLM: valid={} reason={}", valid, reason);

            if (valid) {
                return ValidationResult.pass();
            }
            String friendlyReason = reason.isBlank()
                    ? "This doesn't appear to be a response to the situation. " +
                      "Try addressing what was asked in the scenario."
                    : reason;
            return ValidationResult.fail(friendlyReason);
        } catch (LlmUnavailableException e) {
            // LLM unavailable → be generous and allow the submission rather
            // than blocking the user on an infra issue.
            log.warn("Intent validation: LLM unavailable, defaulting to valid — {}", e.getMessage());
            return ValidationResult.pass();
        } catch (Exception e) {
            log.warn("Intent validation: unexpected error, defaulting to valid — {}", e.getMessage());
            return ValidationResult.pass();
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    public record ValidationResult(boolean valid, String reason) {
        static ValidationResult pass() {
            return new ValidationResult(true, null);
        }
        static ValidationResult fail(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
