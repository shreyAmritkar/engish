package com.stylecommunicator.entity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "practice_session")
public class PracticeSession {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Read-only navigation — see UserProgress.user for why this is separate from userId. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private AppUser user;

    @Column(name = "style_profile_id")
    private UUID styleProfileId;

    /** Read-only navigation onto the same FK StyleProfile already had via style_profile_id. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "style_profile_id", insertable = false, updatable = false)
    private StyleProfile styleProfile;

    private String situation;
    @Column(name = "required_word")
    private String requiredWord;
    @Column(name = "emotional_context")
    private String emotionalContext;
    @Column(name = "user_response")
    private String userResponse;

    /**
     * Storage shape stays a raw Map (zero schema migration). Callers should
     * use getFeedbackTyped()/setFeedbackTyped() via FeedbackJsonMapper
     * instead of touching this field directly — see FeedbackJsonMapper for
     * why the untyped edge is deliberately confined to persistence.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> feedback;

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

    // existing getters/setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public AppUser getUser() { return user; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getStyleProfileId() { return styleProfileId; }
    public StyleProfile getStyleProfile() { return styleProfile; }
    public void setStyleProfileId(UUID styleProfileId) { this.styleProfileId = styleProfileId; }
    public String getSituation() { return situation; }
    public void setSituation(String situation) { this.situation = situation; }
    public String getRequiredWord() { return requiredWord; }
    public void setRequiredWord(String requiredWord) { this.requiredWord = requiredWord; }
    public String getEmotionalContext() { return emotionalContext; }
    public void setEmotionalContext(String emotionalContext) { this.emotionalContext = emotionalContext; }
    public String getUserResponse() { return userResponse; }
    public void setUserResponse(String userResponse) { this.userResponse = userResponse; }
    public Map<String, Object> getFeedback() { return feedback; }
    public void setFeedback(Map<String, Object> feedback) { this.feedback = feedback; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}