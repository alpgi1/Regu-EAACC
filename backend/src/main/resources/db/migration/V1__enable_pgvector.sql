-- V1: Enable the pgvector extension.
--
-- This migration is idempotent: it will succeed whether or not the extension
-- is already installed. In development, the extension was installed manually
-- during step 1.6 (see docs/phase1-pgvector-verification.md). In production,
-- this migration is the canonical source of truth.
--
-- pgvector provides the `vector` data type and similarity operators used
-- by the retrieval layer (added in Phase 2).

CREATE EXTENSION IF NOT EXISTS vector;
