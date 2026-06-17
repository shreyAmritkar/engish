package com.stylecommunicator.dto;

import com.stylecommunicator.domain.StyleSource;
import com.stylecommunicator.entity.StyleProfile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StyleProfileDto(
        UUID id,
        String name,
        StyleSource source,
        String vocabularyTier,
        String sentenceStructure,
        String emotionalRange,
        String powerDynamic,
        Integer formalityLevel,
        List<String> keyPatterns,
        List<String> avoidPatterns,
        List<String> samplePhrases,
        String rawDescription,
        Instant createdAt
) {
    public static StyleProfileDto from(StyleProfile profile) {
        return new StyleProfileDto(
                profile.getId(),
                profile.getName(),
                profile.getSource(),
                profile.getVocabularyTier(),
                profile.getSentenceStructure(),
                profile.getEmotionalRange(),
                profile.getPowerDynamic(),
                profile.getFormalityLevel(),
                profile.getKeyPatterns(),
                profile.getAvoidPatterns(),
                profile.getSamplePhrases(),
                profile.getRawDescription(),
                profile.getCreatedAt()
        );
    }
}
