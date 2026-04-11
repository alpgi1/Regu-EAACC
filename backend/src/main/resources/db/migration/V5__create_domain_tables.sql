-- V5: Create domain tables for user analyses, reports, and citations.
--
-- Domain model:
--   analysis  — one user submission (text or uploaded file)
--   report    — one compliance report produced for an analysis (1:1)
--   citation  — one claim in a report that cites a specific chunk (many per report)
--
-- Relationships:
--   analysis 1 ─── 1 report
--   report   1 ─── N citations
--
-- Primary keys are UUIDs so IDs are safe to expose in the public API and
-- work cleanly across distributed deployments.

-- ============================================================
-- Table: analysis
-- ============================================================
CREATE TABLE analysis (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- User input
    input_text            TEXT NOT NULL,
    input_source          VARCHAR(20) NOT NULL,
    original_filename     VARCHAR(255),

    -- Processing state
    status                VARCHAR(20) NOT NULL DEFAULT 'pending',
    error_message         TEXT,

    -- Classification outcome (populated after Phase 5 pipeline completes)
    risk_category         VARCHAR(20),
    primary_legal_basis   VARCHAR(100),
    confidence            VARCHAR(20),

    -- Processing metrics
    retry_count           INTEGER NOT NULL DEFAULT 0,
    processing_ms         BIGINT,

    -- Timestamps
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at            TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,

    -- Constraints
    CONSTRAINT chk_analysis_input_source CHECK (
        input_source IN ('text_paste', 'file_upload')
    ),
    CONSTRAINT chk_analysis_status CHECK (
        status IN ('pending', 'processing', 'completed', 'failed')
    ),
    CONSTRAINT chk_analysis_risk_category CHECK (
        risk_category IS NULL OR
        risk_category IN ('unacceptable', 'high', 'limited', 'minimal')
    ),
    CONSTRAINT chk_analysis_confidence CHECK (
        confidence IS NULL OR
        confidence IN ('high', 'medium', 'low', 'review_recommended')
    )
);

-- Indexes for analysis
CREATE INDEX idx_analysis_status ON analysis (status);
CREATE INDEX idx_analysis_created_at ON analysis (created_at DESC);
CREATE INDEX idx_analysis_risk_category ON analysis (risk_category)
    WHERE risk_category IS NOT NULL;

COMMENT ON TABLE analysis IS 'One user-submitted analysis request and its processing state';
COMMENT ON COLUMN analysis.input_text IS 'Full user input (either pasted text or extracted from uploaded file)';
COMMENT ON COLUMN analysis.input_source IS 'How input arrived: text_paste or file_upload';
COMMENT ON COLUMN analysis.retry_count IS 'Number of self-check retries the pipeline performed (max 2)';
COMMENT ON COLUMN analysis.confidence IS 'Final confidence of the classification (review_recommended = flag for human review)';

-- ============================================================
-- Table: report
-- ============================================================
CREATE TABLE report (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analysis_id           UUID NOT NULL UNIQUE,

    -- Structured report content
    summary               TEXT NOT NULL,
    risk_rationale        TEXT NOT NULL,
    obligations           TEXT NOT NULL,
    gaps                  TEXT,
    recommendations       TEXT,

    -- Metadata
    llm_model             VARCHAR(100) NOT NULL,
    generation_ms         BIGINT NOT NULL,

    -- Timestamps
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Foreign key
    CONSTRAINT fk_report_analysis
        FOREIGN KEY (analysis_id)
        REFERENCES analysis(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_report_analysis_id ON report (analysis_id);
CREATE INDEX idx_report_created_at ON report (created_at DESC);

COMMENT ON TABLE report IS 'The final compliance report generated for an analysis';
COMMENT ON COLUMN report.analysis_id IS '1:1 relationship with analysis — each analysis has at most one report';
COMMENT ON COLUMN report.obligations IS 'Structured obligations section (format decided in Phase 6, stored as text for flexibility)';
COMMENT ON COLUMN report.llm_model IS 'Which LLM produced this report (for provenance and reproducibility)';

-- ============================================================
-- Table: citation
-- ============================================================
CREATE TABLE citation (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id             UUID NOT NULL,

    -- What the citation supports
    claim_text            TEXT NOT NULL,
    claim_section         VARCHAR(50) NOT NULL,

    -- What it cites (one of three vector tables)
    source_table          VARCHAR(20) NOT NULL,
    source_chunk_id       BIGINT NOT NULL,
    source_reference      VARCHAR(200) NOT NULL,

    -- Validation state (set by Phase 6 post-processing)
    validated             BOOLEAN NOT NULL DEFAULT FALSE,
    validation_notes      TEXT,

    -- Ordering within the report
    display_order         INTEGER NOT NULL,

    -- Timestamp
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT fk_citation_report
        FOREIGN KEY (report_id)
        REFERENCES report(id)
        ON DELETE CASCADE,
    CONSTRAINT chk_citation_source_table CHECK (
        source_table IN ('legal_chunks', 'use_case_chunks', 'guide_chunks')
    ),
    CONSTRAINT chk_citation_claim_section CHECK (
        claim_section IN ('risk_rationale', 'obligations', 'gaps', 'recommendations', 'summary')
    )
);

CREATE INDEX idx_citation_report_id ON citation (report_id);
CREATE INDEX idx_citation_source_table ON citation (source_table);
CREATE INDEX idx_citation_display_order ON citation (report_id, display_order);

COMMENT ON TABLE citation IS 'A claim in a report linked to a specific chunk that supports it';
COMMENT ON COLUMN citation.source_table IS 'Which vector table the cited chunk lives in (polymorphic reference)';
COMMENT ON COLUMN citation.source_chunk_id IS 'bigserial id from the source vector table';
COMMENT ON COLUMN citation.source_reference IS 'Human-readable reference (e.g., "AI Act Article 10(2)" or "GPAI Code §3.2")';
COMMENT ON COLUMN citation.validated IS 'Set to true after Phase 6 post-processing confirms the chunk still exists';
