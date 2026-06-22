package com.stylecommunicator.service;

import com.stylecommunicator.exception.LlmUnavailableException;
import com.stylecommunicator.llm.LlmClient;
import com.stylecommunicator.llm.LlmTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntentValidationServiceTest {

    @Mock private LlmClient llmClient;

    private IntentValidationService service;

    private static final String SITUATION =
        "You are the team lead. Your developer just sent a Slack message: " +
        "\"I don't think your approach is right.\" Write your reply.";

    @BeforeEach
    void setUp() {
        service = new IntentValidationService(llmClient);
    }

    // ── Null / blank ──────────────────────────────────────────────────────

    @Test
    void validate_nullResponse_fails() {
        var result = service.validate(null, SITUATION);
        assertFalse(result.valid());
        assertNotNull(result.reason());
    }

    @Test
    void validate_blankResponse_fails() {
        var result = service.validate("   ", SITUATION);
        assertFalse(result.valid());
    }

    // ── Too short ─────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"ok", "yes", "I agree", "sure thing"})
    void validate_tooFewWords_fails(String response) {
        var result = service.validate(response, SITUATION);
        assertFalse(result.valid());
        assertTrue(result.reason().toLowerCase().contains("short") ||
                   result.reason().toLowerCase().contains("sentence"));
    }

    // ── Keyboard mash ─────────────────────────────────────────────────────

    @Test
    void validate_keyboardMash_fails() {
        var result = service.validate("aaaaaaaaaaaaaaaaaaaaaaaaa", SITUATION);
        assertFalse(result.valid());
    }

    // ── Low variety ───────────────────────────────────────────────────────

    @Test
    void validate_repeatedWords_fails() {
        // Only 1 distinct word repeated
        var result = service.validate("yes yes yes yes yes yes yes", SITUATION);
        assertFalse(result.valid());
        assertTrue(result.reason().toLowerCase().contains("variety") ||
                   result.reason().toLowerCase().contains("genuine"));
    }

    // ── Heuristic pass (enough signals — no LLM call) ────────────────────

    @Test
    void validate_responseWithEnoughWorkplaceSignals_passesWithoutLlm() {
        String response = "I understand your concern and I think we should discuss " +
                          "this priority before the team meeting.";
        var result = service.validate(response, SITUATION);
        assertTrue(result.valid());
        verifyNoInteractions(llmClient);
    }

    @Test
    void validate_clearWorkplaceResponse_passesWithoutLlm() {
        String response = "Thank you for the feedback. I believe our approach is correct " +
                          "because we need to support the team deadline.";
        var result = service.validate(response, SITUATION);
        assertTrue(result.valid());
        verifyNoInteractions(llmClient);
    }

    // ── LLM fallback for borderline cases ────────────────────────────────

    @Test
    void validate_borderlineResponse_callsLlm() {
        String response = "No way this works trust me bro definitely not happening here";
        when(llmClient.generateJson(anyString(), eq(LlmTier.FAST)))
            .thenReturn(Map.of("valid", true, "reason", ""));

        var result = service.validate(response, SITUATION);
        assertTrue(result.valid());
        verify(llmClient, times(1)).generateJson(anyString(), eq(LlmTier.FAST));
    }

    @Test
    void validate_llmRejectsResponse_returnsFailWithReason() {
        String response = "pizza unicorn moon stars blinking lights";
        when(llmClient.generateJson(anyString(), eq(LlmTier.FAST)))
            .thenReturn(Map.of("valid", false,
                "reason", "This response has nothing to do with the workplace scenario."));

        var result = service.validate(response, SITUATION);
        assertFalse(result.valid());
        assertFalse(result.reason().isBlank());
    }

    // ── LLM unavailable → fail open ───────────────────────────────────────

    @Test
    void validate_llmUnavailable_failsOpen() {
        String response = "totally random stuff without workplace signals at all ever";
        when(llmClient.generateJson(anyString(), eq(LlmTier.FAST)))
            .thenThrow(new LlmUnavailableException("LLM down"));

        var result = service.validate(response, SITUATION);
        assertTrue(result.valid(), "Should fail open when LLM is unavailable");
    }

    // ── LLM unexpected exception → fail open ─────────────────────────────

    @Test
    void validate_llmThrowsUnexpectedException_failsOpen() {
        String response = "something borderline without enough signals here";
        when(llmClient.generateJson(anyString(), eq(LlmTier.FAST)))
            .thenThrow(new RuntimeException("Unexpected error"));

        var result = service.validate(response, SITUATION);
        assertTrue(result.valid(), "Should fail open on unexpected errors");
    }

    // ── LLM returns empty reason → uses fallback message ─────────────────

    @Test
    void validate_llmRejectsWithEmptyReason_usesFallbackMessage() {
        String response = "unicorn pizza blinking stars moon";
        when(llmClient.generateJson(anyString(), eq(LlmTier.FAST)))
            .thenReturn(Map.of("valid", false, "reason", ""));

        var result = service.validate(response, SITUATION);
        assertFalse(result.valid());
        assertFalse(result.reason().isBlank(), "Should use fallback message when reason is empty");
    }

    // ── ValidationResult record ───────────────────────────────────────────

    @Test
    void validationResult_pass_isValid() {
        var result = IntentValidationService.ValidationResult.pass();
        assertTrue(result.valid());
        assertNull(result.reason());
    }

    @Test
    void validationResult_fail_isNotValid() {
        var result = IntentValidationService.ValidationResult.fail("Too short.");
        assertFalse(result.valid());
        assertEquals("Too short.", result.reason());
    }
}
