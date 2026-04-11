/**
 * Ingestion logic specific to the EU AI Act legal text.
 *
 * <p>Handles structure-aware chunking by article, paragraph, and annex point.
 * Each chunk carries parent context (article title) as metadata to preserve
 * semantic integrity during retrieval.
 *
 * <p>Populated in Phase 3.
 */
package com.regu.ingestion.law;
