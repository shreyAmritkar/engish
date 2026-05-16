package com.stylecommunicator.entity;

import com.stylecommunicator.domain.CommunityCardStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "community_style_card")
public class CommunityStyleCard {

    @Id
    private UUID id;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "character_name")
    private String characterName;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> excerpts;

    @Column(name = "ai_extracted_profile_id")
    private UUID aiExtractedProfileId;

    private int votes;

    @Enumerated(EnumType.STRING)
    private CommunityCardStatus status = CommunityCardStatus.PENDING;

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
    public UUID getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(UUID submittedBy) { this.submittedBy = submittedBy; }
    public String getCharacterName() { return characterName; }
    public void setCharacterName(String characterName) { this.characterName = characterName; }
    public List<String> getExcerpts() { return excerpts; }
    public void setExcerpts(List<String> excerpts) { this.excerpts = excerpts; }
    public UUID getAiExtractedProfileId() { return aiExtractedProfileId; }
    public void setAiExtractedProfileId(UUID aiExtractedProfileId) { this.aiExtractedProfileId = aiExtractedProfileId; }
    public int getVotes() { return votes; }
    public void setVotes(int votes) { this.votes = votes; }
    public CommunityCardStatus getStatus() { return status; }
    public void setStatus(CommunityCardStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
