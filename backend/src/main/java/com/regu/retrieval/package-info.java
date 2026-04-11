/**
 * Retrieval services that query the vector store and full-text search index.
 *
 * <p>Combines vector similarity search (semantic) with keyword search (BM25/full-text)
 * using a hybrid scoring strategy. Returns ranked, de-duplicated document chunks
 * ready for prompt assembly.
 *
 * <p>Populated in Phase 4.
 */
package com.regu.retrieval;
