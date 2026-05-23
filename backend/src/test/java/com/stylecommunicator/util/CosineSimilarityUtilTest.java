package com.stylecommunicator.util;

import com.stylecommunicator.entity.StyleProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CosineSimilarityUtilTest {

    @Test
    void identicalProfilesHaveHighSimilarity() {
        StyleProfile a = ceoProfile();
        StyleProfile b = ceoProfile();
        assertTrue(CosineSimilarityUtil.cosineSimilarity(a, b) > 0.95);
    }

    @Test
    void structurallySimilarArchetypesStayDistinct() {
        StyleProfile ceo = ceoProfile();
        StyleProfile meme = memeProfile();
        double similarity = CosineSimilarityUtil.cosineSimilarity(ceo, meme);
        assertTrue(similarity < 0.85, "CEO vs meme similarity was " + similarity);
    }

    @Test
    void differentEnumsHaveLowStructuralSimilarity() {
        StyleProfile a = base("DOMINANT", "SHORT_PUNCHY", "FLAT", "SIMPLE", 9,
                List.of("direct"), List.of("sorry"), List.of("We will proceed."));
        StyleProfile b = base("SUBMISSIVE", "LONG_COMPLEX", "EXPRESSIVE", "TECHNICAL", 3,
                List.of("deferential"), List.of("demand"), List.of("Could we discuss?"));
        assertTrue(CosineSimilarityUtil.structuralCosine(a, b) < 0.85);
    }

    private static StyleProfile ceoProfile() {
        return base(
                "DOMINANT", "SHORT_PUNCHY", "MODERATE", "ADVANCED", 9,
                List.of("executive authority", "direct accountability", "strategic framing"),
                List.of("slang", "maybe", "sorry but"),
                List.of(
                        "The board expects results this quarter.",
                        "Here is the decision and the owner.",
                        "We will prioritize revenue and delivery."
                ),
                "Fortune 500 CEO: formal, decisive, stakeholder-focused leadership communication."
        );
    }

    private static StyleProfile memeProfile() {
        return base(
                "DOMINANT", "SHORT_PUNCHY", "EXPRESSIVE", "SIMPLE", 4,
                List.of("ironic humor", "internet slang", "viral meme references"),
                List.of("corporate speak", "quarterly", "pursuant"),
                List.of(
                        "no cap this take is based",
                        "chat is absolutely wild right now",
                        "ratio plus you fell off"
                ),
                "Meme streamer: chaotic, slang-heavy, ironic internet personality."
        );
    }

    private static StyleProfile base(
            String power, String structure, String emotion, String vocab, int formality,
            List<String> patterns, List<String> avoid, List<String> phrases) {
        return base(power, structure, emotion, vocab, formality, patterns, avoid, phrases, null);
    }

    private static StyleProfile base(
            String power, String structure, String emotion, String vocab, int formality,
            List<String> patterns, List<String> avoid, List<String> phrases, String description) {
        StyleProfile p = new StyleProfile();
        p.setName("test");
        p.setPowerDynamic(power);
        p.setSentenceStructure(structure);
        p.setEmotionalRange(emotion);
        p.setVocabularyTier(vocab);
        p.setFormalityLevel(formality);
        p.setKeyPatterns(patterns);
        p.setAvoidPatterns(avoid);
        p.setSamplePhrases(phrases);
        p.setRawDescription(description);
        return p;
    }
}
