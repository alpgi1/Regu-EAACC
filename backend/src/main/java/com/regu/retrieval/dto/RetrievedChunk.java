package com.regu.retrieval.dto;

import java.util.Map;

/**
 * A single chunk returned by the retrieval layer.
 *
 * <p>The {@code metadata} map carries table-specific fields that the LLM
 * orchestrator and report generator will need:
 * <ul>
 *   <li>{@code legal_chunks} — {@code article_number}, {@code paragraph_number},
 *       {@code article_title}, {@code risk_level}, {@code document_type},
 *       {@code citation_eligible}, {@code source_chunk_id}</li>
 *   <li>{@code use_case_chunks} — {@code use_case_id}, {@code sector},
 *       {@code risk_category}, {@code actor_role}</li>
 *   <li>{@code decision_rule_chunks} — {@code rule_id}, {@code chunk_type},
 *       {@code section}</li>
 *   <li>{@code guide_chunks} — {@code section_path}, {@code related_articles}</li>
 * </ul>
 *
 * @param chunkId           database primary key of the chunk
 * @param sourceTable       name of the table the chunk came from
 * @param content           raw chunk content (used for LLM context)
 * @param contentWithContext contextualised version (may be null for tables
 *                           that do not store a separate contextualised field)
 * @param similarityScore   cosine similarity or RRF score (higher = more relevant)
 * @param metadata          table-specific metadata fields
 */
public record RetrievedChunk(
        long chunkId,
        String sourceTable,
        String content,
        String contentWithContext,
        double similarityScore,
        Map<String, Object> metadata
) {}
