package com.regu.ingestion.legal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents one legal chunk entry from a batch extraction JSON file.
 *
 * <p>Batch files live under
 * {@code corpus/legal/raw_extraction/batch_NN_*.json} and contain a
 * JSON array of objects conforming to this schema.
 *
 * <p>Field mapping (JSON → Java):
 * <ul>
 *   <li>{@code source_chunk_id} — stable ID for idempotent re-ingestion
 *       (e.g. {@code eu_ai_act_recital_001}, {@code eu_ai_act_art10_para2})</li>
 *   <li>{@code chunk_type} — granular type: {@code article_paragraph},
 *       {@code annex_point}, {@code recital_text}, {@code definition_entry}</li>
 *   <li>{@code document_type} — coarse type stored in {@code legal_chunks}:
 *       {@code article}, {@code annex}, {@code recital}, {@code definition}</li>
 *   <li>{@code content} — raw paragraph text shown to the LLM at retrieval</li>
 * </ul>
 *
 * <p>All nullable fields are represented as {@code null} in JSON (not omitted)
 * so that Jackson deserialization is unambiguous. Unknown fields are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LegalChunkDto(

        // ── Idempotency key ────────────────────────────────────────────────

        @JsonProperty("source_chunk_id")
        String sourceChunkId,

        // ── Content ────────────────────────────────────────────────────────

        @JsonProperty("content")
        String content,

        // ── Type classification ────────────────────────────────────────────

        /** Granular: article_paragraph | annex_point | recital_text | definition_entry */
        @JsonProperty("chunk_type")
        String chunkType,

        /** Coarse: article | annex | recital | definition (matches DB CHECK constraint) */
        @JsonProperty("document_type")
        String documentType,

        // ── Recital fields ─────────────────────────────────────────────────

        @JsonProperty("recital_number")
        Integer recitalNumber,

        // ── Article / paragraph fields ─────────────────────────────────────

        @JsonProperty("article_number")
        Integer articleNumber,

        @JsonProperty("paragraph_number")
        Integer paragraphNumber,

        /** Article title (e.g. "Data and data governance") */
        @JsonProperty("title")
        String title,

        // ── Chapter / section fields ───────────────────────────────────────

        @JsonProperty("chapter_number")
        Integer chapterNumber,

        @JsonProperty("chapter_title")
        String chapterTitle,

        @JsonProperty("section_number")
        Integer sectionNumber,

        @JsonProperty("section_title")
        String sectionTitle,

        // ── Annex fields ───────────────────────────────────────────────────

        /** Roman-numeral annex identifier (e.g. "I", "III", "IV") */
        @JsonProperty("annex_number")
        String annexNumber,

        /** Point identifier within the annex (e.g. "1", "2a") */
        @JsonProperty("annex_point")
        String annexPoint,

        /** Named section within the annex (e.g. "Section A") */
        @JsonProperty("annex_section")
        String annexSection,

        // ── Risk / compliance metadata ─────────────────────────────────────

        @JsonProperty("risk_level")
        String riskLevel,

        @JsonProperty("citation_eligible")
        Boolean citationEligible,

        // ── Source / versioning ────────────────────────────────────────────

        @JsonProperty("source")
        String source,

        @JsonProperty("publish_date")
        String publishDate,

        @JsonProperty("version")
        String version,

        @JsonProperty("status")
        String status
) {}
