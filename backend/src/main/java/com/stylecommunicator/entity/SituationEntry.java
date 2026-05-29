package com.stylecommunicator.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * One row in the situation_bank table.
 * Sourced from: SEED (original JSON), NEWS, ADVICE, WIKIPEDIA, or LLM fallback.
 */
@Entity
@Table(name = "situation_bank")
public class SituationEntry {

    @Id
    @GeneratedValue
    private UUID id;

    /** DOMINANT | EQUAL | SUBMISSIVE */
    @Column(nullable = false)
    private String power;

    /** A1 | A2 | B1 | B2 */
    @Column(nullable = false)
    private String level;

    @Column(nullable = false, length = 500)
    private String text;

    /** SEED | NEWS | ADVICE | WIKIPEDIA | LLM */
    @Column(nullable = false)
    private String source;

    @Column(name = "use_count", nullable = false)
    private int useCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    // ── Getters / setters ─────────────────────────────────────────────────

    public UUID getId()                       { return id; }
    public void setId(UUID id)                { this.id = id; }

    public String getPower()                  { return power; }
    public void setPower(String power)        { this.power = power; }

    public String getLevel()                  { return level; }
    public void setLevel(String level)        { this.level = level; }

    public String getText()                   { return text; }
    public void setText(String text)          { this.text = text; }

    public String getSource()                 { return source; }
    public void setSource(String source)      { this.source = source; }

    public int getUseCount()                  { return useCount; }
    public void setUseCount(int useCount)     { this.useCount = useCount; }

    public Instant getCreatedAt()             { return createdAt; }
    public void setCreatedAt(Instant t)       { this.createdAt = t; }
}
