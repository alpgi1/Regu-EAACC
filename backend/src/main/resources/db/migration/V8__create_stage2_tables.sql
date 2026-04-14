-- V8: Create Stage 2 tables for Annex IV technical-documentation assessment.
--
-- Stage 2 is entered only by sessions that reach a 'high_risk' classification
-- in Stage 1. It assesses compliance with Article 11 / Annex IV by collecting
-- documentation for each of the nine Annex IV sections, either via document
-- upload or LLM-guided Q&A.
--
-- Tables:
--   annex_iv_sections    — static reference data (one row per Annex IV section)
--   stage2_submissions   — one row per section per session; captures mode + result

-- ============================================================
-- Table: annex_iv_sections
-- ============================================================
-- Static reference data. Populated via ingestion pipeline when Annex IV
-- content is ready. Left empty for now.

CREATE TABLE annex_iv_sections (
    id              UUID     PRIMARY KEY DEFAULT gen_random_uuid(),
    section_number  SMALLINT NOT NULL UNIQUE CHECK (section_number BETWEEN 1 AND 9),
    section_key     VARCHAR(40) NOT NULL UNIQUE,
    display_title   TEXT     NOT NULL,
    instructions    TEXT     NOT NULL,
    hint_text       TEXT,
    required        BOOLEAN  NOT NULL DEFAULT TRUE,
    linked_articles INTEGER[],
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_annex_section_number ON annex_iv_sections(section_number);

COMMENT ON TABLE annex_iv_sections IS
    'Annex IV (Article 11) section definitions for Stage 2 technical
     documentation assessment. Populated via ingestion pipeline.';

-- ============================================================
-- Table: stage2_submissions
-- ============================================================
-- One row per Annex IV section per Stage 2 session.
-- mode = 'upload' when the user provided a document.
-- mode = 'qa'     when information was collected via LLM conversation.
--
-- gaps_found shape: [{gap_description, severity, article_ref, source_mode}]
-- qa_transcript shape: [{role, content}]

CREATE TABLE stage2_submissions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID        NOT NULL REFERENCES interview_sessions(id),
    section_number  SMALLINT    NOT NULL REFERENCES annex_iv_sections(section_number),

    mode            VARCHAR(10) NOT NULL
                    CHECK (mode IN ('upload', 'qa')),

    -- Upload mode fields (null when mode = 'qa')
    file_name       TEXT,
    file_content    TEXT,

    -- QA mode fields (null when mode = 'upload')
    -- Stores full conversation history as [{role, content}]
    qa_transcript   JSONB,

    -- Analysis result (populated after LLM analysis)
    analysis_status VARCHAR(20) NOT NULL DEFAULT 'pending'
                    CHECK (analysis_status IN (
                        'pending', 'analysing', 'complete', 'skipped'
                    )),

    -- Gap analysis result
    -- Shape: [{gap_description, severity, article_ref, source_mode}]
    -- source_mode is always carried through to indicate 'upload' or 'qa'
    -- so the report generator can flag QA-sourced findings
    gaps_found      JSONB,

    compliance_note TEXT,
    analysed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (session_id, section_number)
);

CREATE INDEX idx_s2s_session_id      ON stage2_submissions(session_id);
CREATE INDEX idx_s2s_section_number  ON stage2_submissions(section_number);
CREATE INDEX idx_s2s_analysis_status ON stage2_submissions(analysis_status);

COMMENT ON TABLE stage2_submissions IS
    'One row per Annex IV section per Stage 2 session. Mode = upload
     when user provided a document; mode = qa when information was
     collected via LLM conversation. The report generator flags
     QA-sourced findings with lower evidential weight.';

COMMENT ON COLUMN stage2_submissions.qa_transcript IS
    'Full LLM conversation history for QA mode: [{role, content}].
     Stored for audit trail and used as context during report generation.';

COMMENT ON COLUMN stage2_submissions.gaps_found IS
    'Gap analysis results: [{gap_description, severity, article_ref,
     source_mode}]. source_mode mirrors the submission mode so report
     generation can distinguish document-evidenced vs QA-inferred gaps.';
