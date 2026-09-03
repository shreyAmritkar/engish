package com.stylecommunicator.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "user_progress")
public class UserProgress {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    /**
     * Read-only navigation to the owning AppUser, mapped onto the same
     * user_id column. userId above remains the actual @Id and stays the
     * primary way this entity is looked up (findById(userId) etc,
     * unchanged everywhere it's already used) — this association exists so
     * the one-to-one relationship is explicit in the object model too, not
     * only enforced by the FK constraint added in V9. insertable/updatable
     * are false because userId (via the plain @Id field) remains the single
     * source of truth for writes; this field is populated on read only.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private AppUser user;

    @Column(name = "current_level")
    private int currentLevel = 1;

    @Column(name = "total_sessions")
    private int totalSessions = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "average_scores", columnDefinition = "jsonb")
    private Map<String, Double> averageScores;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "weak_areas", columnDefinition = "text[]")
    private List<String> weakAreas;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "strong_areas", columnDefinition = "text[]")
    private List<String> strongAreas;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "habit_flags", columnDefinition = "text[]")
    private List<String> habitFlags;

    @Column(name = "last_updated")
    private Instant lastUpdated = Instant.now();

    @PrePersist
    @PreUpdate
    public void touch() {
        lastUpdated = Instant.now();
    }

    @Version
    private Long version;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public AppUser getUser() { return user; }
    public int getCurrentLevel() { return currentLevel; }
    public void setCurrentLevel(int currentLevel) { this.currentLevel = currentLevel; }
    public int getTotalSessions() { return totalSessions; }
    public void setTotalSessions(int totalSessions) { this.totalSessions = totalSessions; }
    public Map<String, Double> getAverageScores() { return averageScores; }
    public void setAverageScores(Map<String, Double> averageScores) { this.averageScores = averageScores; }
    public List<String> getWeakAreas() { return weakAreas; }
    public void setWeakAreas(List<String> weakAreas) { this.weakAreas = weakAreas; }
    public List<String> getStrongAreas() { return strongAreas; }
    public void setStrongAreas(List<String> strongAreas) { this.strongAreas = strongAreas; }
    public List<String> getHabitFlags() { return habitFlags; }
    public void setHabitFlags(List<String> habitFlags) { this.habitFlags = habitFlags; }
    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
}