-- V3: Create use_case_chunks table for curated scenario examples.
--
-- This table stores hand-curated use case scenarios that bridge the gap
-- between user language ("HR screening system") and legal language
-- ("employment, workers management and access to self-employment").
-- Each use case is an atomic unit — one scenario per chunk, never split.
--
-- Retrieval strategy: pure vector similarity. Use cases are written in
-- natural user-facing language, so semantic search works well without
-- keyword augmentation.
--
-- mapped_articles uses integer[] so Phase 4 can do fast array overlap
-- queries like: WHERE mapped_articles && ARRAY[9,10,14].

CREATE TABLE use_case_chunks (
    id                    BIGSERIAL PRIMARY KEY,

    -- Full scenario text (this is what gets embedded AND what the LLM sees)
    content               TEXT NOT NULL,

    -- Vector embedding of content (1024-dim Voyage vectors)
    embedding             vector(1024) NOT NULL,

    -- Scenario identification
    scenario_name         VARCHAR(200) NOT NULL,
    scenario_domain       VARCHAR(100) NOT NULL,

    -- Classification metadata (hand-labeled by curator)
    risk_category         VARCHAR(20) NOT NULL,
    primary_legal_basis   VARCHAR(100),
    mapped_articles       INTEGER[] NOT NULL DEFAULT '{}',
    mapped_annex_points   VARCHAR(50)[] NOT NULL DEFAULT '{}',

    -- Provenance
    source                VARCHAR(100) NOT NULL DEFAULT 'curated',
    curator_notes         TEXT,

    -- Versioning
    version               VARCHAR(20) NOT NULL DEFAULT '1.0',

    -- Timestamps
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT chk_usecase_risk_category CHECK (
        risk_category IN ('unacceptable', 'high', 'limited', 'minimal')
    ),
    CONSTRAINT chk_usecase_source CHECK (
        source IN ('curated', 'llm_seeded', 'user_submitted')
    ),
    CONSTRAINT uq_usecase_scenario_name UNIQUE (scenario_name)
);

-- Vector index (HNSW) for semantic search
CREATE INDEX idx_use_case_chunks_embedding ON use_case_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- GIN index for fast array overlap queries on mapped_articles
-- Enables: WHERE mapped_articles && ARRAY[9,10,14]
CREATE INDEX idx_use_case_chunks_mapped_articles ON use_case_chunks
    USING gin (mapped_articles);

-- Metadata filter indexes
CREATE INDEX idx_use_case_chunks_risk_category ON use_case_chunks (risk_category);
CREATE INDEX idx_use_case_chunks_domain ON use_case_chunks (scenario_domain);

-- Comments
COMMENT ON TABLE use_case_chunks IS 'Curated scenario examples bridging user language to EU AI Act legal language';
COMMENT ON COLUMN use_case_chunks.content IS 'Full scenario text — both embedded and shown to the LLM';
COMMENT ON COLUMN use_case_chunks.mapped_articles IS 'Integer array of EU AI Act article numbers this scenario relates to';
COMMENT ON COLUMN use_case_chunks.mapped_annex_points IS 'String array of Annex III points (e.g., "III.4.a") this scenario relates to';
COMMENT ON COLUMN use_case_chunks.source IS 'How this use case was created: hand-curated, LLM-seeded, or user-submitted';
