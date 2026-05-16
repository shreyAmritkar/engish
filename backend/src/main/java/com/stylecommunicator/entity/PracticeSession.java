package com.stylecommunicator.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "practice_session")
public class PracticeSession {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "style_profile_id")
    private UUID styleProfileId;

    private String situation;
    @Column(name = "required_word")
    private String requiredWord;
    @Column(name = "emotional_context")
    private String emotionalContext;
    @Column(name = "user_response")
    private String userResponse;

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

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getStyleProfileId() { return styleProfileId; }
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
