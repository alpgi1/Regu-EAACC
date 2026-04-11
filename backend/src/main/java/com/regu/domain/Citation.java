package com.regu.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A claim in a report linked to a specific chunk that supports it.
 *
 * <p>Uses a polymorphic reference (sourceTable + sourceChunkId) to point
 * into one of the three vector tables. Not a real foreign key — validated
 * at the application layer in Phase 6.
 */
@Entity
@Table(name = "citation")
public class Citation {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(name = "claim_text", nullable = false, columnDefinition = "TEXT")
    private String claimText;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_section", nullable = false, length = 50)
    private ClaimSection claimSection;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_table", nullable = false, length = 20)
    private SourceTable sourceTable;

    @Column(name = "source_chunk_id", nullable = false)
    private long sourceChunkId;

    @Column(name = "source_reference", nullable = false, length = 200)
    private String sourceReference;

    @Column(name = "validated", nullable = false)
    private boolean validated = false;

    @Column(name = "validation_notes", columnDefinition = "TEXT")
    private String validationNotes;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum ClaimSection { risk_rationale, obligations, gaps, recommendations, summary }
    public enum SourceTable { legal_chunks, use_case_chunks, guide_chunks }

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    protected Citation() { }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Report getReport() { return report; }
    public void setReport(Report report) { this.report = report; }

    public String getClaimText() { return claimText; }
    public void setClaimText(String claimText) { this.claimText = claimText; }

    public ClaimSection getClaimSection() { return claimSection; }
    public void setClaimSection(ClaimSection claimSection) { this.claimSection = claimSection; }

    public SourceTable getSourceTable() { return sourceTable; }
    public void setSourceTable(SourceTable sourceTable) { this.sourceTable = sourceTable; }

    public long getSourceChunkId() { return sourceChunkId; }
    public void setSourceChunkId(long sourceChunkId) { this.sourceChunkId = sourceChunkId; }

    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String sourceReference) { this.sourceReference = sourceReference; }

    public boolean isValidated() { return validated; }
    public void setValidated(boolean validated) { this.validated = validated; }

    public String getValidationNotes() { return validationNotes; }
    public void setValidationNotes(String validationNotes) { this.validationNotes = validationNotes; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
