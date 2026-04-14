package com.regu.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One Annex IV section submission for a Stage 2 interview session.
 *
 * <p>Mapped to the {@code stage2_submissions} table created by Flyway V8.
 * One row exists per Annex IV section per session. The {@code mode} field
 * distinguishes document-upload submissions from LLM Q&A submissions.
 *
 * <p>Design notes:
 * <ul>
 *   <li>{@code sessionId} is stored as a raw UUID — no {@code @ManyToOne},
 *       consistent with the polymorphic-reference pattern used in {@link Citation}.</li>
 *   <li>{@code sectionNumber} is stored as a raw Integer, not a FK mapping.</li>
 *   <li>{@code qaTranscript} and {@code gapsFound} are JSONB columns stored as
 *       String with {@code @JdbcTypeCode(SqlTypes.JSON)} so Hibernate handles
 *       the JSONB JDBC type correctly during schema validation.</li>
 * </ul>
 */
@Entity
@Table(name = "stage2_submissions")
public class Stage2Submission {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * FK to {@code interview_sessions.id} — stored as UUID, no {@code @ManyToOne}.
     * Matches the design of {@link Citation#sourceChunkId}: store the reference
     * directly rather than loading a full entity graph.
     */
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    /**
     * FK to {@code annex_iv_sections.section_number} — stored as Integer.
     * Combined with {@code sessionId} the pair is UNIQUE in the database.
     */
    @Column(name = "section_number", nullable = false)
    private Integer sectionNumber;

    /**
     * Submission mode: {@code "upload"} or {@code "qa"}.
     * Validated at the service layer; the CHECK constraint in the DB is the
     * authoritative enforcement point.
     */
    @Column(name = "mode", nullable = false, length = 10)
    private String mode;

    // -- Upload mode fields (null when mode = 'qa') ----------------------

    @Column(name = "file_name", columnDefinition = "TEXT")
    private String fileName;

    @Column(name = "file_content", columnDefinition = "TEXT")
    private String fileContent;

    // -- QA mode fields (null when mode = 'upload') ----------------------

    /**
     * Full LLM conversation history: [{role, content}].
     * Stored as raw JSONB text. Null when mode = 'upload'.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "qa_transcript")
    private String qaTranscript;

    // -- Analysis result -------------------------------------------------

    /**
     * Analysis lifecycle: {@code "pending"} → {@code "analysing"} →
     * {@code "complete"} or {@code "skipped"}.
     */
    @Column(name = "analysis_status", nullable = false, length = 20)
    private String analysisStatus = "pending";

    /**
     * Gap analysis results: [{gap_description, severity, article_ref, source_mode}].
     * Populated after LLM analysis. Null until analysis completes.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gaps_found")
    private String gapsFound;

    @Column(name = "compliance_note", columnDefinition = "TEXT")
    private String complianceNote;

    @Column(name = "analysed_at")
    private Instant analysedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    protected Stage2Submission() { }

    public Stage2Submission(UUID sessionId, Integer sectionNumber, String mode) {
        this.sessionId = sessionId;
        this.sectionNumber = sectionNumber;
        this.mode = mode;
    }

    // ------------------------------------------------------------------
    // Getters and setters
    // ------------------------------------------------------------------

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public Integer getSectionNumber() { return sectionNumber; }
    public void setSectionNumber(Integer sectionNumber) { this.sectionNumber = sectionNumber; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileContent() { return fileContent; }
    public void setFileContent(String fileContent) { this.fileContent = fileContent; }

    public String getQaTranscript() { return qaTranscript; }
    public void setQaTranscript(String qaTranscript) { this.qaTranscript = qaTranscript; }

    public String getAnalysisStatus() { return analysisStatus; }
    public void setAnalysisStatus(String analysisStatus) { this.analysisStatus = analysisStatus; }

    public String getGapsFound() { return gapsFound; }
    public void setGapsFound(String gapsFound) { this.gapsFound = gapsFound; }

    public String getComplianceNote() { return complianceNote; }
    public void setComplianceNote(String complianceNote) { this.complianceNote = complianceNote; }

    public Instant getAnalysedAt() { return analysedAt; }
    public void setAnalysedAt(Instant analysedAt) { this.analysedAt = analysedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
