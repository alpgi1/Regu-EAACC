package com.regu.api.dto;

/** A resolved citation linking a report claim to a source chunk in the knowledge base. */
public record CitationDto(
        String citationId,
        String sourceTable,
        long sourceChunkId,
        String reference,
        String snippet
) {}
