package com.regu.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One recorded answer per question per interview session.
 *
 * <p>Mapped to the {@code interview_answers} table created by Flyway V7.
 * Each row captures the user's raw input for a single question, the
 * LLM-normalised mapping result, and the obligations and flags that
 * were applied to the session as a consequence of this answer.
 *
 * <p>Note: {@code obligations} and {@code flags_applied} are PostgreSQL
 * native {@code VARCHAR(50)[]} arrays mapped as {@code String[]}.
 */
@Entity
@Table(name = "interview_answers")
public class InterviewAnswer {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** The owning session — mandatory, never null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    /**
     * Denormalised key of the question that was answered.
     * Stored directly rather than as a FK so that historical answers
     * remain readable even if question metadata is updated.
     */
    @Column(name = "question_key", nullable = false, length = 20)
    private String questionKey;

    /** Verbatim user input before LLM normalisation. */
    @Column(name = "raw_input", nullable = false, columnDefinition = "TEXT")
    private String rawInput;

    /**
     * Normalised answer value (snake_case option value) after LLM mapping.
     * Null if the answer could not be confidently mapped.
     */
    @Column(name = "mapped_answer", length = 50)
    private String mappedAnswer;

    /**
     * LLM confidence in the mapping, in the range [0.000, 1.000].
     * Stored as {@code NUMERIC(4,3)} in PostgreSQL.
     */
    @Column(name = "mapped_confidence", precision = 4, scale = 3)
    private BigDecimal mappedConfidence;

    /**
     * Obligation codes triggered by this answer, copied from the matched
     * option at write-time. Stored as PostgreSQL {@code VARCHAR(50)[]}.
     */
    @Column(name = "obligations", columnDefinition = "VARCHAR(50)[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] obligations;

    /**
     * Status flags applied to the session as a result of this answer
     * (e.g. {@code "high_risk"}, {@code "prohibited"}, {@code "fria_required"}).
     * Stored as PostgreSQL {@code VARCHAR(50)[]}.
     */
    @Column(name = "flags_applied", columnDefinition = "VARCHAR(50)[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] flagsApplied;

    @Column(name = "answered_at", nullable = false, updatable = false)
    private Instant answeredAt;

    // ------------------------------------------------------------------
    // Lifecycle hooks
    // ------------------------------------------------------------------

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (answeredAt == null) answeredAt = Instant.now();
    }

    // ------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------

    protected InterviewAnswer() { }

    public InterviewAnswer(InterviewSession session, String questionKey, String rawInput) {
        this.session = session;
        this.questionKey = questionKey;
        this.rawInput = rawInput;
    }

    // ------------------------------------------------------------------
    // Getters and setters
    // ------------------------------------------------------------------

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public InterviewSession getSession() { return session; }
    public void setSession(InterviewSession session) { this.session = session; }

    public String getQuestionKey() { return questionKey; }
    public void setQuestionKey(String questionKey) { this.questionKey = questionKey; }

    public String getRawInput() { return rawInput; }
    public void setRawInput(String rawInput) { this.rawInput = rawInput; }

    public String getMappedAnswer() { return mappedAnswer; }
    public void setMappedAnswer(String mappedAnswer) { this.mappedAnswer = mappedAnswer; }

    public BigDecimal getMappedConfidence() { return mappedConfidence; }
    public void setMappedConfidence(BigDecimal mappedConfidence) { this.mappedConfidence = mappedConfidence; }

    public String[] getObligations() { return obligations; }
    public void setObligations(String[] obligations) { this.obligations = obligations; }

    public String[] getFlagsApplied() { return flagsApplied; }
    public void setFlagsApplied(String[] flagsApplied) { this.flagsApplied = flagsApplied; }

    public Instant getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(Instant answeredAt) { this.answeredAt = answeredAt; }
}
