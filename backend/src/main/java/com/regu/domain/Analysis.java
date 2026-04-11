package com.regu.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A user-submitted analysis request and its processing state.
 *
 * <p>Mapped to the "analysis" table created by Flyway V5. An analysis
 * represents one click of "analyze this system" by a user and tracks the
 * full lifecycle from submission through classification to completion.
 */
@Entity
@Table(name = "analysis")
public class Analysis {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "input_text", nullable = false, columnDefinition = "TEXT")
    private String inputText;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_source", nullable = false, length = 20)
    private InputSource inputSource;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.pending;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_category", length = 20)
    private RiskCategory riskCategory;

    @Column(name = "primary_legal_basis", length = 100)
    private String primaryLegalBasis;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", length = 20)
    private Confidence confidence;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "processing_ms")
    private Long processingMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public enum InputSource { text_paste, file_upload }
    public enum Status { pending, processing, completed, failed }
    public enum RiskCategory { unacceptable, high, limited, minimal }
    public enum Confidence { high, medium, low, review_recommended }

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    protected Analysis() { }

    public Analysis(String inputText, InputSource inputSource) {
        this.inputText = inputText;
        this.inputSource = inputSource;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getInputText() { return inputText; }
    public void setInputText(String inputText) { this.inputText = inputText; }

    public InputSource getInputSource() { return inputSource; }
    public void setInputSource(InputSource inputSource) { this.inputSource = inputSource; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public RiskCategory getRiskCategory() { return riskCategory; }
    public void setRiskCategory(RiskCategory riskCategory) { this.riskCategory = riskCategory; }

    public String getPrimaryLegalBasis() { return primaryLegalBasis; }
    public void setPrimaryLegalBasis(String primaryLegalBasis) { this.primaryLegalBasis = primaryLegalBasis; }

    public Confidence getConfidence() { return confidence; }
    public void setConfidence(Confidence confidence) { this.confidence = confidence; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public Long getProcessingMs() { return processingMs; }
    public void setProcessingMs(Long processingMs) { this.processingMs = processingMs; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
