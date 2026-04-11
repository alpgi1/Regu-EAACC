/**
 * Root package for document loading, chunking, and embedding pipelines.
 *
 * <p>Orchestrates the end-to-end ingestion flow: raw document input → text extraction
 * → chunking strategy (delegated to sub-packages) → embedding → vector store upsert.
 *
 * <p>Populated in Phase 3.
 */
package com.regu.ingestion;
