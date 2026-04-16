package com.regu.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * The final compliance report generated for an analysis.
 *
 * <p>1:1 relationship with Analysis — each analysis has at most one report.
 * Mapped to the "report" table created by Flyway V5.
 */
@Entity
@Table(name = "report")
public class Report {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false, unique = true)
    private Analysis analysis;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "risk_rationale", nullable = false, columnDefinition = "TEXT")
    private String riskRationale;

    @Column(name = "obligations", nullable = false, columnDefinition = "TEXT")
    private String obligations;

    @Column(name = "gaps", columnDefinition = "TEXT")
    private String gaps;

    @Column(name = "recommendations", columnDefinition = "TEXT")
    private String recommendations;

    @Column(name = "llm_model", nullable = false, length = 100)
    private String llmModel;

    @Column(name = "generation_ms", nullable = false)
    private long generationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    protected Report() { }

    /** Factory for service-layer creation. JPA requires the no-arg constructor to remain protected. */
    public static Report create() { return new Report(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Analysis getAnalysis() { return analysis; }
    public void setAnalysis(Analysis analysis) { this.analysis = analysis; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getRiskRationale() { return riskRationale; }
    public void setRiskRationale(String riskRationale) { this.riskRationale = riskRationale; }

    public String getObligations() { return obligations; }
    public void setObligations(String obligations) { this.obligations = obligations; }

    public String getGaps() { return gaps; }
    public void setGaps(String gaps) { this.gaps = gaps; }

    public String getRecommendations() { return recommendations; }
    public void setRecommendations(String recommendations) { this.recommendations = recommendations; }

    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String llmModel) { this.llmModel = llmModel; }

    public long getGenerationMs() { return generationMs; }
    public void setGenerationMs(long generationMs) { this.generationMs = generationMs; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
