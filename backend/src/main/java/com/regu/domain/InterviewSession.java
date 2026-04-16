package com.regu.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * An interview session tracking one user's guided compliance interview attempt.
 *
 * <p>Mapped to the {@code interview_sessions} table created by Flyway V7.
 * A session exists per user analysis attempt and records the current position
 * in the Stage 1 (FLI flowchart) or Stage 2 (Annex IV) decision tree, along
 * with all flags accumulated as answers are recorded.
 *
 * <p>Note: {@code entity_types} and {@code active_flags} are PostgreSQL
 * native {@code VARCHAR[]} / {@code TEXT[]} arrays mapped as {@code String[]}.
 * Hibernate resolves the SQL array type from the column definition.
 */
@Entity
@Table(name = "interview_sessions")
public class InterviewSession {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Nullable FK to the owning {@link Analysis}.
     * A session may be created before an analysis record is persisted.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id")
    private Analysis analysis;

    /** Current interview stage: 1 = risk classification, 2 = Annex IV gap assessment. */
    @Column(name = "stage", nullable = false)
    private short stage = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status = Status.active;

    /** The {@code question_key} of the next question to present to the user. */
    @Column(name = "current_question_key", length = 20)
    private String currentQuestionKey;

    /**
     * Operator roles identified at question E1
     * (e.g. {@code "provider"}, {@code "deployer"}, {@code "distributor"}).
     * Stored as a PostgreSQL {@code VARCHAR(30)[]}.
     */
    @Column(name = "entity_types", columnDefinition = "VARCHAR(30)[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] entityTypes;

    /**
     * Flags accumulated throughout the session, e.g.
     * {@code "high_risk"}, {@code "prohibited"}, {@code "fria_required"}.
     * Stored as a PostgreSQL {@code VARCHAR(50)[]}.
     */
    @Column(name = "active_flags", columnDefinition = "VARCHAR(50)[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] activeFlags;

    /**
     * Final Stage 1 risk classification.
     * Populated when {@code status} reaches {@code stage1_complete} or {@code complete}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_classification", length = 20)
    private RiskClassification riskClassification;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ------------------------------------------------------------------
    // Enums
    // ------------------------------------------------------------------

    public enum Status {
        active,
        stage1_complete,
        stage2_started,
        complete,
        abandoned
    }

    public enum RiskClassification {
        unacceptable,
        high,
        limited,
        minimal,
        out_of_scope
    }

    // ------------------------------------------------------------------
    // Lifecycle hooks
    // ------------------------------------------------------------------

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------

    protected InterviewSession() { }

    /** Factory for service/test-layer creation (JPA requires protected no-arg constructor). */
    public static InterviewSession create() { return new InterviewSession(); }

    /**
     * Creates a session linked to an existing analysis.
     * Use the protected no-arg constructor (via JPA) or the factory method
     * in the service layer for sessions without a pre-existing analysis.
     */
    public InterviewSession(Analysis analysis) {
        this.analysis = analysis;
    }

    // ------------------------------------------------------------------
    // Getters and setters
    // ------------------------------------------------------------------

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Analysis getAnalysis() { return analysis; }
    public void setAnalysis(Analysis analysis) { this.analysis = analysis; }

    public short getStage() { return stage; }
    public void setStage(short stage) { this.stage = stage; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getCurrentQuestionKey() { return currentQuestionKey; }
    public void setCurrentQuestionKey(String currentQuestionKey) { this.currentQuestionKey = currentQuestionKey; }

    public String[] getEntityTypes() { return entityTypes; }
    public void setEntityTypes(String[] entityTypes) { this.entityTypes = entityTypes; }

    public String[] getActiveFlags() { return activeFlags; }
    public void setActiveFlags(String[] activeFlags) { this.activeFlags = activeFlags; }

    public RiskClassification getRiskClassification() { return riskClassification; }
    public void setRiskClassification(RiskClassification riskClassification) {
        this.riskClassification = riskClassification;
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
