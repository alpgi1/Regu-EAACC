package com.regu.orchestration.dto;

/**
 * A resolved citation inside a compliance report.
 *
 * @param citationId  unique within the report, e.g. "cite_1"
 * @param sourceTable e.g. "legal_chunks", "guide_chunks"
 * @param sourceChunkId database primary key of the cited chunk
 * @param reference   human-readable label, e.g. "Article 10(2)"
 * @param snippet     first ~200 characters of the cited chunk content
 */
public record CitationEntry(
        String citationId,
        String sourceTable,
        long sourceChunkId,
        String reference,
        String snippet
) {}
