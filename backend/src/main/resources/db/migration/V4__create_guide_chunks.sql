-- V4: Create guide_chunks table for business guidance documents.
--
-- This table stores chunks of practical guidance documents: Commission
-- guidelines, GPAI Code of Practice, AI Office Q&A, and similar "how-to"
-- materials. These guides tell users HOW to comply with obligations,
-- whereas legal_chunks tells them WHAT the obligations are.
--
-- Chunking strategy: header-aware, section-based, with overlap for long
-- sections. Each chunk typically 400-600 tokens.
--
-- Retrieval strategy: vector similarity + metadata filter on related_articles.
-- Example Phase 4 query: "find guide chunks related to Article 10 (data
-- governance) that match the query 'how to document training data'".

CREATE TABLE guide_chunks (
    id                    BIGSERIAL PRIMARY KEY,

    -- Chunk content
    content               TEXT NOT NULL,

    -- Vector embedding (1024-dim Voyage vectors)
    embedding             vector(1024) NOT NULL,

    -- Source identification
    source_document       VARCHAR(255) NOT NULL,
    source_url            VARCHAR(500),
    publisher             VARCHAR(100) NOT NULL,

    -- Structural context within the source document
    section_title         VARCHAR(500),
    section_path          VARCHAR(500),
    chunk_index           INTEGER NOT NULL,

    -- Topic and relationships
    topic                 VARCHAR(100),
    related_articles      INTEGER[] NOT NULL DEFAULT '{}',
    guidance_type         VARCHAR(50) NOT NULL,

    -- Versioning
    publish_date          DATE NOT NULL,
    version               VARCHAR(20) NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'final',

    -- Timestamps
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT chk_guide_status CHECK (
        status IN ('draft', 'final', 'superseded')
    ),
    CONSTRAINT chk_guide_type CHECK (
        guidance_type IN ('commission_guideline', 'code_of_practice', 'faq', 'technical_standard', 'industry_guide')
    )
);

-- Vector index (HNSW) for semantic search
CREATE INDEX idx_guide_chunks_embedding ON guide_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- GIN index for fast array overlap on related_articles
-- Enables Phase 4 queries like: WHERE related_articles && ARRAY[10,14]
CREATE INDEX idx_guide_chunks_related_articles ON guide_chunks
    USING gin (related_articles);

-- Metadata filter indexes
CREATE INDEX idx_guide_chunks_topic ON guide_chunks (topic)
    WHERE topic IS NOT NULL;
CREATE INDEX idx_guide_chunks_guidance_type ON guide_chunks (guidance_type);
CREATE INDEX idx_guide_chunks_source ON guide_chunks (source_document);

-- Comments
COMMENT ON TABLE guide_chunks IS 'Chunks of practical guidance documents (Commission guidelines, Code of Practice, FAQs)';
COMMENT ON COLUMN guide_chunks.content IS 'Header-aware section chunk, typically 400-600 tokens';
COMMENT ON COLUMN guide_chunks.related_articles IS 'EU AI Act articles this guidance applies to (used for metadata-filtered retrieval)';
COMMENT ON COLUMN guide_chunks.section_path IS 'Hierarchical section path within source doc, e.g., "Chapter 3 > Section 2 > Data Quality"';
COMMENT ON COLUMN guide_chunks.chunk_index IS 'Sequential index within the source document for reassembly if needed';
COMMENT ON COLUMN guide_chunks.guidance_type IS 'Category of guidance: official Commission, code of practice, FAQ, standard, or industry';
