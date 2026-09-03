package com.stylecommunicator.entity;

import com.stylecommunicator.domain.StyleSource;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "style_profile")
public class StyleProfile {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    private StyleSource source;

    @Column(name = "created_by")
    private UUID createdBy;

    /**
     * Nullable — PRESET styles have no creator (see V9 migration: ON DELETE
     * SET NULL, not CASCADE, since a style shouldn't disappear just because
     * its original author's account is deleted while other users still
     * practice it).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private AppUser creator;

    @Column(name = "vocabulary_tier")
    private String vocabularyTier;

    @Column(name = "sentence_structure")
    private String sentenceStructure;

    @Column(name = "emotional_range")
    private String emotionalRange;

    @Column(name = "power_dynamic")
    private String powerDynamic;

    @Column(name = "formality_level")
    private Integer formalityLevel;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "key_patterns", columnDefinition = "text[]")
    private List<String> keyPatterns;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "avoid_patterns", columnDefinition = "text[]")
    private List<String> avoidPatterns;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "sample_phrases", columnDefinition = "text[]")
    private List<String> samplePhrases;

    @Column(name = "raw_description")
    private String rawDescription;

    @Column(name = "compressed_prompt")
    private String compressedPrompt;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public StyleSource getSource() { return source; }
    public void setSource(StyleSource source) { this.source = source; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public AppUser getCreator() { return creator; }
    public String getVocabularyTier() { return vocabularyTier; }
    public void setVocabularyTier(String vocabularyTier) { this.vocabularyTier = vocabularyTier; }
    public String getSentenceStructure() { return sentenceStructure; }
    public void setSentenceStructure(String sentenceStructure) { this.sentenceStructure = sentenceStructure; }
    public String getEmotionalRange() { return emotionalRange; }
    public void setEmotionalRange(String emotionalRange) { this.emotionalRange = emotionalRange; }
    public String getPowerDynamic() { return powerDynamic; }
    public void setPowerDynamic(String powerDynamic) { this.powerDynamic = powerDynamic; }
    public Integer getFormalityLevel() { return formalityLevel; }
    public void setFormalityLevel(Integer formalityLevel) { this.formalityLevel = formalityLevel; }
    public List<String> getKeyPatterns() { return keyPatterns; }
    public void setKeyPatterns(List<String> keyPatterns) { this.keyPatterns = keyPatterns; }
    public List<String> getAvoidPatterns() { return avoidPatterns; }
    public void setAvoidPatterns(List<String> avoidPatterns) { this.avoidPatterns = avoidPatterns; }
    public List<String> getSamplePhrases() { return samplePhrases; }
    public void setSamplePhrases(List<String> samplePhrases) { this.samplePhrases = samplePhrases; }
    public String getRawDescription() { return rawDescription; }
    public void setRawDescription(String rawDescription) { this.rawDescription = rawDescription; }
    public String getCompressedPrompt() { return compressedPrompt; }
    public void setCompressedPrompt(String compressedPrompt) { this.compressedPrompt = compressedPrompt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}