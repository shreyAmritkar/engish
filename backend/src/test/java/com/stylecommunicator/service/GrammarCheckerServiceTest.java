package com.stylecommunicator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GrammarCheckerServiceTest {

    private GrammarCheckerService service;

    @BeforeEach
    void setUp() {
        service = new GrammarCheckerService();
    }

    // ── Null / blank ──────────────────────────────────────────────────────

    @Test
    void check_nullInput_returnsEmptyNote() {
        List<String> notes = service.check(null);
        assertFalse(notes.isEmpty());
    }

    @Test
    void check_blankInput_returnsEmptyNote() {
        List<String> notes = service.check("   ");
        assertFalse(notes.isEmpty());
    }

    // ── Clean text produces no notes ──────────────────────────────────────

    @Test
    void check_cleanProfessionalText_returnsNoNotes() {
        // Short sentence, starts with capital, ends with period, no double spaces
        String text = "I agree fully.";
        List<String> notes = service.check(text);
        assertTrue(notes.isEmpty(), "Clean text should produce no notes, got: " + notes);
    }

    // ── Lowercase start ───────────────────────────────────────────────────

    @Test
    void check_lowercaseStart_flagsCapitalization() {
        List<String> notes = service.check("we need to discuss this matter.");
        assertTrue(notes.stream().anyMatch(n -> n.toLowerCase().contains("capital")));
    }

    // ── Missing terminal punctuation ──────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
        "I think we should proceed with caution",
        "Thank you for your feedback"
    })
    void check_missingTerminalPunctuation_flagsIt(String text) {
        List<String> notes = service.check(text);
        assertTrue(notes.stream().anyMatch(n -> n.toLowerCase().contains("punctuation")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "I think we should proceed with caution.",
        "Thank you for your feedback!",
        "Do you agree with this approach?"
    })
    void check_hasTerminalPunctuation_noFlag(String text) {
        List<String> notes = service.check(text);
        assertFalse(notes.stream().anyMatch(n -> n.toLowerCase().contains("punctuation")),
            "Should not flag terminal punctuation when present");
    }

    // ── Double spaces ─────────────────────────────────────────────────────

    @Test
    void check_doubleSpaces_flagsThem() {
        List<String> notes = service.check("I  think we should proceed.");
        assertTrue(notes.stream().anyMatch(n -> n.toLowerCase().contains("space")));
    }

    // ── Excessive exclamation marks ───────────────────────────────────────

    @Test
    void check_excessiveExclamations_flagsThem() {
        List<String> notes = service.check("Great idea!! Let's do it!!");
        assertTrue(notes.stream().anyMatch(n -> n.toLowerCase().contains("exclamation")));
    }

    @Test
    void check_singleExclamation_noFlag() {
        List<String> notes = service.check("Great idea! Let's proceed.");
        assertFalse(notes.stream().anyMatch(n -> n.toLowerCase().contains("exclamation")));
    }

    // ── Long text without comma ───────────────────────────────────────────

    @Test
    void check_longTextWithoutComma_flagsIt() {
        String text = "I believe the approach we have taken is fundamentally sound and we should continue.";
        List<String> notes = service.check(text);
        assertTrue(notes.stream().anyMatch(n -> n.toLowerCase().contains("comma")));
    }

    @Test
    void check_longTextWithComma_noFlag() {
        String text = "I believe the approach is sound, and we should continue with it today.";
        List<String> notes = service.check(text);
        assertFalse(notes.stream().anyMatch(n -> n.toLowerCase().contains("comma")),
            "Should not flag comma when one is already present, got: " + notes);
    }

    // ── Multiple issues detected together ────────────────────────────────

    @Test
    void check_multipleIssues_flagsAll() {
        String text = "we  need to fix this!!"; // lowercase start + double space + exclamation
        List<String> notes = service.check(text);
        assertTrue(notes.size() >= 2, "Should detect multiple issues, got: " + notes);
    }

    // ── Return type contract ──────────────────────────────────────────────

    @Test
    void check_alwaysReturnsList() {
        assertNotNull(service.check("Some text."));
        assertNotNull(service.check(null));
        assertNotNull(service.check(""));
    }
}
