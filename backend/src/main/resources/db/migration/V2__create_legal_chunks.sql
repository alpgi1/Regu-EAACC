-- V2: Create legal_chunks table for EU AI Act legal text.
--
-- This table stores chunks of the EU AI Act (articles, paragraphs, annex points)
-- with vector embeddings for semantic search and tsvector for keyword search.
-- Retrieval in Phase 4 will combine both approaches (hybrid search).
--
-- Chunking strategy: structure-aware, one paragraph per chunk. Each chunk
-- carries its parent article context (prefixed in content_with_context) so
-- embeddings capture semantic meaning without orphaned fragments.
--
-- Embedding model: Voyage voyage-3-large, 1024 dimensions (Matryoshka).

CREATE TABLE legal_chunks (
    id                    BIGSERIAL PRIMARY KEY,

    -- Raw content (what the LLM will see when this chunk is retrieved)
    content               TEXT NOT NULL,

    -- Content with parent article context prefixed (what gets embedded)
    -- Example: "[Article 10 — Data and data governance]\n[Paragraph 2]\n..."
    content_with_context  TEXT NOT NULL,

    -- Vector embedding of content_with_context (1024-dim Voyage vectors)
    embedding             vector(1024) NOT NULL,

    -- Structural metadata (what part of the AI Act this chunk came from)
    source                VARCHAR(255) NOT NULL,
    document_type         VARCHAR(50)  NOT NULL,
    article_number        INTEGER,
    paragraph_number      INTEGER,
    annex_number          VARCHAR(10),
    annex_point           VARCHAR(50),
    title                 VARCHAR(500),

    -- Risk-related metadata (populated where applicable)
    risk_level            VARCHAR(20),

    -- Versioning metadata (for corpus freshness tracking)
    publish_date          DATE NOT NULL,
    version               VARCHAR(20) NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'final',

    -- Full-text search vector (generated from content)
    search_vector         tsvector GENERATED ALWAYS AS (to_tsvector('english', content)) STORED,

    -- Timestamps
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT chk_legal_document_type CHECK (
        document_type IN ('article', 'annex', 'recital', 'definition')
    ),
    CONSTRAINT chk_legal_status CHECK (
        status IN ('draft', 'final', 'superseded')
    ),
    CONSTRAINT chk_legal_risk_level CHECK (
        risk_level IS NULL OR risk_level IN ('unacceptable', 'high', 'limited', 'minimal')
    )
);

-- Vector index (HNSW) for fast similarity search
-- Using cosine distance operator since Voyage embeddings are cosine-normalized
CREATE INDEX idx_legal_chunks_embedding ON legal_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- Full-text search index (GIN on tsvector)
CREATE INDEX idx_legal_chunks_search ON legal_chunks USING gin(search_vector);

-- Metadata filter indexes (for deterministic lookups in Phase 4)
CREATE INDEX idx_legal_chunks_article ON legal_chunks (article_number)
    WHERE article_number IS NOT NULL;

CREATE INDEX idx_legal_chunks_annex ON legal_chunks (annex_number, annex_point)
    WHERE annex_number IS NOT NULL;

CREATE INDEX idx_legal_chunks_document_type ON legal_chunks (document_type);
CREATE INDEX idx_legal_chunks_risk_level ON legal_chunks (risk_level)
    WHERE risk_level IS NOT NULL;

-- Comment annotations for future maintainers
COMMENT ON TABLE legal_chunks IS 'Chunks of the EU AI Act legal text with embeddings and full-text search';
COMMENT ON COLUMN legal_chunks.content IS 'Raw chunk content shown to the LLM at retrieval time';
COMMENT ON COLUMN legal_chunks.content_with_context IS 'Chunk content with parent article context prefixed; this is what gets embedded';
COMMENT ON COLUMN legal_chunks.embedding IS 'Voyage voyage-3-large 1024-dim embedding of content_with_context';
COMMENT ON COLUMN legal_chunks.search_vector IS 'Generated tsvector for PostgreSQL full-text keyword search';
