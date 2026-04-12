-- V6: Create decision_rule_chunks table for procedural compliance logic.
--
-- This table stores chunks of decision-making content from compliance
-- flowcharts and procedural guides. Unlike legal_chunks (which holds the
-- normative law), use_case_chunks (concrete scenarios), and guide_chunks
-- (practical how-to instructions), this table holds the CONDITIONAL DECISION
-- LOGIC that connects user input to classification outcomes.
--
-- Example content:
--   - "If your AI system falls within an Annex III high-risk category, you
--     must then assess whether it poses significant risk of harm under
--     Article 6(3). Exceptions exist for narrow procedural tasks..."
--   - "When a deployer modifies an AI system in any of the following ways,
--     they become legally treated as a provider under Article 25..."
--
-- Initial source: Future of Life Institute EU AI Act Compliance Checker.
-- Future sources may include other compliance flowcharts, decision trees,
-- and structured Q&A guides.
--
-- Retrieval strategy: vector similarity + metadata filter on chunk_type
-- (used by Phase 5 to bring in decision logic alongside law and use cases).

CREATE TABLE decision_rule_chunks (
    id                    BIGSERIAL PRIMARY KEY,

    -- Chunk content (this is what gets embedded AND shown to the LLM)
    content               TEXT NOT NULL,

    -- Vector embedding (1024-dim Voyage vectors)
    embedding             vector(1024) NOT NULL,

    -- Stable identifier from the source document
    -- Example: "fli_q_hr4", "fli_outcome_become_provider", "fli_obligation_provider_high_risk"
    rule_id               VARCHAR(100) NOT NULL,

    -- Type of decision content (one of four kinds)
    chunk_type            VARCHAR(20) NOT NULL,

    -- Hierarchical placement in the source document
    section               VARCHAR(50) NOT NULL,
    section_path          VARCHAR(500),

    -- Source identification
    source_document       VARCHAR(255) NOT NULL,
    source_url            VARCHAR(500),
    publisher             VARCHAR(100) NOT NULL,

    -- Topical classification
    topic                 VARCHAR(100),
    related_articles      INTEGER[] NOT NULL DEFAULT '{}',
    related_annex_points  VARCHAR(50)[] NOT NULL DEFAULT '{}',

    -- Versioning
    publish_date          DATE NOT NULL,
    version               VARCHAR(20) NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'final',

    -- Timestamps
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT chk_decision_chunk_type CHECK (
        chunk_type IN ('question', 'outcome', 'obligation', 'exception')
    ),
    CONSTRAINT chk_decision_status CHECK (
        status IN ('draft', 'final', 'superseded')
    ),
    CONSTRAINT uq_decision_rule_id UNIQUE (rule_id, source_document, version)
);

-- Vector index (HNSW) for semantic search
CREATE INDEX idx_decision_rule_chunks_embedding ON decision_rule_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- GIN index for fast array overlap queries on related_articles
-- Enables Phase 5 queries like: WHERE related_articles && ARRAY[6,9,10]
CREATE INDEX idx_decision_rule_chunks_related_articles ON decision_rule_chunks
    USING gin (related_articles);

-- GIN index on related_annex_points for similar overlap queries
CREATE INDEX idx_decision_rule_chunks_annex_points ON decision_rule_chunks
    USING gin (related_annex_points);

-- Metadata filter indexes
CREATE INDEX idx_decision_rule_chunks_chunk_type ON decision_rule_chunks (chunk_type);
CREATE INDEX idx_decision_rule_chunks_section ON decision_rule_chunks (section);
CREATE INDEX idx_decision_rule_chunks_topic ON decision_rule_chunks (topic)
    WHERE topic IS NOT NULL;
CREATE INDEX idx_decision_rule_chunks_source ON decision_rule_chunks (source_document);

-- Comments
COMMENT ON TABLE decision_rule_chunks IS 'Procedural decision logic for EU AI Act compliance (flowcharts, decision trees, structured Q&A)';
COMMENT ON COLUMN decision_rule_chunks.content IS 'Decision rule prose — both embedded and shown to the LLM';
COMMENT ON COLUMN decision_rule_chunks.rule_id IS 'Stable identifier from the source document (e.g., "fli_q_hr4"); used by use_cases.linked_decision_rules for deterministic lookup';
COMMENT ON COLUMN decision_rule_chunks.chunk_type IS 'Kind of decision content: question (decision point), outcome (status change), obligation (requirement), or exception (exclusion/carve-out)';
COMMENT ON COLUMN decision_rule_chunks.section IS 'Top-level section of the source document, e.g., "high_risk_status", "scope", "rules_for_particular_types"';
COMMENT ON COLUMN decision_rule_chunks.section_path IS 'Hierarchical path within the source document for human-readable references';
COMMENT ON COLUMN decision_rule_chunks.related_articles IS 'EU AI Act articles this rule references (used for metadata-filtered retrieval and use case linking)';
COMMENT ON COLUMN decision_rule_chunks.related_annex_points IS 'EU AI Act annex points this rule references (e.g., "III.4.a")';
