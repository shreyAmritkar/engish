package com.stylecommunicator.util;

import com.stylecommunicator.entity.StyleProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosineSimilarityUtilTest {

    @Test
    void identicalProfilesHaveSimilarityOne() {
        StyleProfile a = profile("DOMINANT", "SHORT_PUNCHY", "MODERATE", "ADVANCED", 8);
        StyleProfile b = profile("DOMINANT", "SHORT_PUNCHY", "MODERATE", "ADVANCED", 8);
        assertEquals(1.0, CosineSimilarityUtil.cosineSimilarity(a, b), 0.001);
    }

    @Test
    void differentProfilesHaveLowerSimilarity() {
        StyleProfile a = profile("DOMINANT", "SHORT_PUNCHY", "FLAT", "SIMPLE", 9);
        StyleProfile b = profile("SUBMISSIVE", "LONG_COMPLEX", "EXPRESSIVE", "TECHNICAL", 3);
        assertTrue(CosineSimilarityUtil.cosineSimilarity(a, b) < 0.85);
    }

    private StyleProfile profile(String power, String structure, String emotion, String vocab, int formality) {
        StyleProfile p = new StyleProfile();
        p.setPowerDynamic(power);
        p.setSentenceStructure(structure);
        p.setEmotionalRange(emotion);
        p.setVocabularyTier(vocab);
        p.setFormalityLevel(formality);
        return p;
    }
}
