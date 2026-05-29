package com.stylecommunicator.service.situation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SituationTemplateEngine.
 * No Spring context — runs in milliseconds.
 */
class SituationTemplateEngineTest {

    private SituationTemplateEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SituationTemplateEngine();
    }

    // ── apply() ───────────────────────────────────────────────────────────

    @ParameterizedTest(name = "apply({0}, {1}) must not be blank")
    @CsvSource({
        "DOMINANT,   B2",
        "EQUAL,      B2",
        "SUBMISSIVE, B2",
        "DOMINANT,   A1",
        "EQUAL,      A1",
        "SUBMISSIVE, A1",
        "DOMINANT,   B1",
        "EQUAL,      A2",
        "SUBMISSIVE, B1",
    })
    void apply_producesNonBlankResult(String power, String level) {
        String result = engine.apply(power.trim(), level.trim(), "project deadlines");
        assertFalse(result.isBlank(),
            "apply(" + power + "," + level + ") returned blank");
    }

    @Test
    void apply_injectsTopic() {
        String topic = "budget constraints";
        String result = engine.apply("DOMINANT", "B2", topic);
        assertTrue(result.contains(topic),
            "Expected topic '" + topic + "' to appear in: " + result);
    }

    @Test
    void apply_unknownPowerFallsBackToEqual() {
        // UNKNOWN power should not throw — falls back to EQUAL templates
        assertDoesNotThrow(() -> engine.apply("UNKNOWN", "B2", "some topic"));
    }

    @Test
    void apply_a1PrefixesResult() {
        String result = engine.apply("DOMINANT", "A1", "daily targets");
        // A1 level adds "In simple words: " prefix
        assertTrue(result.startsWith("In simple words:"),
            "A1 result should start with prefix, got: " + result);
    }

    @Test
    void apply_b2HasNoPrefix() {
        // B2 should NOT have the simple-words prefix
        String result = engine.apply("EQUAL", "B2", "project scope");
        assertFalse(result.startsWith("In simple words:"),
            "B2 result should not have A1 prefix");
    }

    // ── extractTopic() ────────────────────────────────────────────────────

    @Test
    void extractTopic_nullInputReturnsFallback() {
        assertEquals("a work challenge", engine.extractTopic(null));
    }

    @Test
    void extractTopic_blankInputReturnsFallback() {
        assertEquals("a work challenge", engine.extractTopic("   "));
    }

    @Test
    void extractTopic_takesFirstSentenceOnly() {
        String raw = "Trust your team. Always give credit. Never micromanage.";
        String topic = engine.extractTopic(raw);
        // Should not contain content from the second sentence
        assertFalse(topic.contains("Always give"),
            "Should only use first sentence, got: " + topic);
    }

    @Test
    void extractTopic_capsAtEightWords() {
        String raw = "This is a very long sentence with way more than eight words in total here";
        String topic = engine.extractTopic(raw);
        String[] words = topic.split("\\s+");
        assertTrue(words.length <= 8,
            "Expected ≤ 8 words, got " + words.length + " in: " + topic);
    }

    @Test
    void extractTopic_stripsFillerOpeners() {
        String raw = "Always listen to your colleagues carefully";
        String topic = engine.extractTopic(raw);
        assertFalse(topic.toLowerCase().startsWith("always"),
            "Filler opener 'always' should be stripped, got: " + topic);
    }

    @Test
    void extractTopic_returnsLowercase() {
        String raw = "Conflict Resolution Is Important";
        String topic = engine.extractTopic(raw);
        assertEquals(topic, topic.toLowerCase(),
            "extractTopic should return lowercase, got: " + topic);
    }

    @Test
    void extractTopic_shortInputReturnedAsIs() {
        String raw = "manage conflict";
        String topic = engine.extractTopic(raw);
        assertEquals("manage conflict", topic);
    }
}
