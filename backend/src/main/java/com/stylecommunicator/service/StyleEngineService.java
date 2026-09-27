package com.stylecommunicator.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.stylecommunicator.config.AppProperties;
import com.stylecommunicator.domain.StyleSource;
import com.stylecommunicator.entity.StyleProfile;
import com.stylecommunicator.llm.LlmClient;
import com.stylecommunicator.llm.LlmTier;
import com.stylecommunicator.repository.StyleProfileRepository;
import com.stylecommunicator.util.CosineSimilarityUtil;
import com.stylecommunicator.util.StylePromptCompressor;

@Service
public class StyleEngineService {

    private static final Logger log = LoggerFactory.getLogger(StyleEngineService.class);

    private final StyleProfileRepository styleProfileRepository;
    private final LlmClient llmClient;
    private final AppProperties appProperties;
    private final StylePromptCache stylePromptCache;

    public StyleEngineService(
            StyleProfileRepository styleProfileRepository,
            LlmClient llmClient,
            AppProperties appProperties,
            StylePromptCache stylePromptCache) {
        this.styleProfileRepository = styleProfileRepository;
        this.llmClient = llmClient;
        this.appProperties = appProperties;
        this.stylePromptCache = stylePromptCache;
    }

    @Transactional
    public StyleProfile extractFromDescription(String name, String description, UUID createdBy, StyleSource source) {
        Map<String, Object> extracted = extractDna(description);
        StyleProfile candidate = mapToProfile(name, description, createdBy, source, extracted);
        Optional<StyleProfile> duplicate = findSimilarProfile(candidate);
        if (duplicate.isPresent()) {
            return duplicate.get();
        }
        candidate.setCompressedPrompt(StylePromptCompressor.compress(candidate));
        StyleProfile saved = styleProfileRepository.save(candidate);
        stylePromptCache.put(saved.getId(), saved.getCompressedPrompt());
        return saved;
    }

    public String getCompressedPrompt(StyleProfile profile) {
        return stylePromptCache.get(profile.getId())
                .orElseGet(() -> {
                    String compressed = profile.getCompressedPrompt();
                    if (compressed == null || compressed.isBlank()) {
                        compressed = StylePromptCompressor.compress(profile);
                        profile.setCompressedPrompt(compressed);
                    }
                    stylePromptCache.put(profile.getId(), compressed);
                    return compressed;
                });
    }

    private Map<String, Object> extractDna(String description) {
        String prompt = """
                Analyse the communication style in the text below and extract a profile.
                The text may be fictional dialogue, character descriptions, or real speech samples.

                Text: "%s"

                For power_dynamic, judge how this person positions themselves RELATIVE TO OTHERS
                in conversation — not how confident, dramatic, or strong they sound in isolation.
                  DOMINANT   = directs, commands, or controls others. Sets rules for people around them.
                               Examples: a boss, a strict mentor, a villain giving orders.
                  EQUAL      = speaks as a peer. May be bold or emotional but does not seek to lead or
                               control others. Protects, jokes, argues, or collaborates at the same level.
                               Examples: a loyal friend, a rival, a sarcastic colleague, a funny sidekick.
                  SUBMISSIVE = defers, seeks approval, avoids conflict, follows others' lead.
                               Examples: an anxious new employee, someone people-pleasing.

                Key rule: strong language, dramatic tone, or high confidence does NOT mean DOMINANT.
                Ask: does this person try to CONTROL others, or do they act AS AN EQUAL alongside them?

                Return JSON only:
                {
                  "vocabulary_tier": "SIMPLE|INTERMEDIATE|ADVANCED|TECHNICAL",
                  "sentence_structure": "SHORT_PUNCHY|LONG_COMPLEX|MIXED",
                  "emotional_range": "FLAT|MODERATE|EXPRESSIVE",
                  "power_dynamic": "DOMINANT|EQUAL|SUBMISSIVE",
                  "formality_level": 1-10,
                  "key_patterns": ["pattern1"],
                  "avoid_patterns": ["pattern1"],
                  "sample_phrases": ["phrase1"]
                }
                """.formatted(truncate(description, 2000));

        try {
            return llmClient.generateJson(prompt, LlmTier.FAST);
        } catch (com.stylecommunicator.exception.LlmUnavailableException e) {
            log.warn("LLM unavailable during style extraction, using defaults: {}", e.getMessage());
            return defaultExtraction();
        }
    }

    private Map<String, Object> defaultExtraction() {
        return Map.of(
                "vocabulary_tier", "INTERMEDIATE",
                "sentence_structure", "MIXED",
                "emotional_range", "MODERATE",
                "power_dynamic", "EQUAL",
                "formality_level", 5,
                "key_patterns", List.of("clear communication"),
                "avoid_patterns", List.of("vague language"),
                "sample_phrases", List.of("Let me be direct.")
        );
    }
    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }
    private StyleProfile mapToProfile(String name, String description, UUID createdBy, StyleSource source, Map<String, Object> extracted) {
        StyleProfile profile = new StyleProfile();
        profile.setName(name);
        profile.setRawDescription(description);
        profile.setCreatedBy(createdBy);
        profile.setSource(source);
        profile.setVocabularyTier(asString(extracted.get("vocabulary_tier")));
        profile.setSentenceStructure(asString(extracted.get("sentence_structure")));
        profile.setEmotionalRange(asString(extracted.get("emotional_range")));
        profile.setPowerDynamic(asString(extracted.get("power_dynamic")));
        profile.setFormalityLevel(asInt(extracted.get("formality_level"), 5));
        profile.setKeyPatterns(asStringList(extracted.get("key_patterns")));
        profile.setAvoidPatterns(asStringList(extracted.get("avoid_patterns")));
        profile.setSamplePhrases(asStringList(extracted.get("sample_phrases")));
        return profile;
    }

    private Optional<StyleProfile> findSimilarProfile(StyleProfile candidate) {
        double threshold = appProperties.getSimilarityThreshold();
        return styleProfileRepository.findAll().stream()
                .filter(p -> CosineSimilarityUtil.cosineSimilarity(candidate, p) > threshold)
                .findFirst();
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }



    private int asInt(Object value, int defaultVal) {
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultVal;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public StyleProfile getById(UUID styleProfileId) {
    return styleProfileRepository.findById(styleProfileId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Style not found"));
    }
}
