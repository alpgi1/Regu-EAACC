package com.regu.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One Annex IV section definition for the Stage 2 technical-documentation assessment.
 *
 * <p>Mapped to the {@code annex_iv_sections} table created by Flyway V8.
 * This is static reference data — rows are populated via the ingestion pipeline
 * when Annex IV content is ready and are never written by user requests.
 *
 * <p>Note: {@code linked_articles} is a PostgreSQL {@code INTEGER[]} array
 * mapped as {@code int[]} via Hibernate's array type support.
 */
@Entity
@Table(name = "annex_iv_sections")
public class AnnexIvSection {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Annex IV section number (1–9). UNIQUE in the database.
     * Referenced directly by {@code stage2_submissions.section_number}.
     */
    @Column(name = "section_number", nullable = false)
    private Integer sectionNumber;

    @Column(name = "section_key", nullable = false, length = 40)
    private String sectionKey;

    @Column(name = "display_title", nullable = false, columnDefinition = "TEXT")
    private String displayTitle;

    @Column(name = "instructions", nullable = false, columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "hint_text", columnDefinition = "TEXT")
    private String hintText;

    @Column(name = "required", nullable = false)
    private boolean required = true;

    @Column(name = "linked_articles", columnDefinition = "INTEGER[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private int[] linkedArticles;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ------------------------------------------------------------------
    // Lifecycle hooks
    // ------------------------------------------------------------------

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    // ------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------

    protected AnnexIvSection() { }

    public AnnexIvSection(Integer sectionNumber, String sectionKey,
                          String displayTitle, String instructions) {
        this.sectionNumber = sectionNumber;
        this.sectionKey = sectionKey;
        this.displayTitle = displayTitle;
        this.instructions = instructions;
    }

    // ------------------------------------------------------------------
    // Getters and setters
    // ------------------------------------------------------------------

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Integer getSectionNumber() { return sectionNumber; }
    public void setSectionNumber(Integer sectionNumber) { this.sectionNumber = sectionNumber; }

    public String getSectionKey() { return sectionKey; }
    public void setSectionKey(String sectionKey) { this.sectionKey = sectionKey; }

    public String getDisplayTitle() { return displayTitle; }
    public void setDisplayTitle(String displayTitle) { this.displayTitle = displayTitle; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public String getHintText() { return hintText; }
    public void setHintText(String hintText) { this.hintText = hintText; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public int[] getLinkedArticles() { return linkedArticles; }
    public void setLinkedArticles(int[] linkedArticles) { this.linkedArticles = linkedArticles; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
