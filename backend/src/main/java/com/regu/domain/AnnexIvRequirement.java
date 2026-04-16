package com.regu.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One granular Annex IV technical-documentation requirement.
 *
 * <p>Mapped to the {@code annex_iv_requirements} table created by Flyway V10.
 * Each row represents a specific piece of information (e.g. "Name of the provider",
 * "Cybersecurity measures") that must appear in a high-risk AI provider's
 * technical dossier under Article 11 / Annex IV.
 *
 * <p>During Stage 2, the compliance engine checks whether each
 * {@code extractionTarget} is present in the user's uploaded document.
 * If not, {@code fallbackPrompt} is surfaced as a follow-up question.
 *
 * <p>This is static reference data — rows are populated by the ingestion
 * pipeline and are never written by user requests.
 */
@Entity
@Table(name = "annex_iv_requirements")
public class AnnexIvRequirement {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Stable Annex IV identifier (e.g. {@code "1_a_1"}, {@code "2_b_3"}).
     * The leading digit maps to {@code annex_iv_sections.section_number}.
     */
    @Column(name = "requirement_id", nullable = false, unique = true, length = 20)
    private String requirementId;

    /**
     * Section number (1–9) — FK to {@code annex_iv_sections.section_number}.
     * Mapped as {@code Short} to match the {@code SMALLINT} column —
     * Hibernate 7 enforces strict JDBC type alignment.
     */
    @Column(name = "section_number", nullable = false)
    private Short sectionNumber;

    /**
     * Lazy association to the parent section for richer context lookups.
     * Not always needed — prefer {@link #sectionNumber} for simple queries.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_number", insertable = false, updatable = false)
    private AnnexIvSection section;

    /** Annex IV category label, e.g. {@code "1_a (Intended Purpose, Provider and Version)"}. */
    @Column(name = "category", nullable = false, length = 200)
    private String category;

    /** Human-readable name of the information item, e.g. {@code "Name of the provider"}. */
    @Column(name = "entity_name", nullable = false, length = 200)
    private String entityName;

    /**
     * Description of what to look for in the user's uploaded technical
     * documentation. Used by the document analysis pipeline.
     */
    @Column(name = "extraction_target", nullable = false, columnDefinition = "TEXT")
    private String extractionTarget;

    /**
     * Question to ask the user when the extraction target is not found in the
     * uploaded document. Drives the Stage 2 Q&amp;A follow-up flow.
     */
    @Column(name = "fallback_prompt", nullable = false, columnDefinition = "TEXT")
    private String fallbackPrompt;

    /** Whether this requirement may be omitted without causing non-compliance. */
    @Column(name = "is_optional", nullable = false)
    private boolean optional = false;

    /**
     * Presentation order within the section. Assigned at ingestion time based
     * on position in the corpus JSON array.
     */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

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

    protected AnnexIvRequirement() { }

    public AnnexIvRequirement(String requirementId, Short sectionNumber,
                               String category, String entityName,
                               String extractionTarget, String fallbackPrompt,
                               int displayOrder) {
        this.requirementId    = requirementId;
        this.sectionNumber    = sectionNumber;
        this.category         = category;
        this.entityName       = entityName;
        this.extractionTarget = extractionTarget;
        this.fallbackPrompt   = fallbackPrompt;
        this.displayOrder     = displayOrder;
    }

    // ------------------------------------------------------------------
    // Getters and setters
    // ------------------------------------------------------------------

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getRequirementId() { return requirementId; }
    public void setRequirementId(String requirementId) { this.requirementId = requirementId; }

    public Short getSectionNumber() { return sectionNumber; }
    public void setSectionNumber(Short sectionNumber) { this.sectionNumber = sectionNumber; }

    public AnnexIvSection getSection() { return section; }
    public void setSection(AnnexIvSection section) { this.section = section; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }

    public String getExtractionTarget() { return extractionTarget; }
    public void setExtractionTarget(String extractionTarget) { this.extractionTarget = extractionTarget; }

    public String getFallbackPrompt() { return fallbackPrompt; }
    public void setFallbackPrompt(String fallbackPrompt) { this.fallbackPrompt = fallbackPrompt; }

    public boolean isOptional() { return optional; }
    public void setOptional(boolean optional) { this.optional = optional; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
