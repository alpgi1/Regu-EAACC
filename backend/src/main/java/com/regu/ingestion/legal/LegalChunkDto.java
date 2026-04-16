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
 * <p>Key JSON → Java field mappings:
 * <ul>
 *   <li>{@code article_title} → {@code title} (article title string)</li>
 *   <li>{@code source_document} → {@code source} (source document name)</li>
 *   <li>{@code chapter_number} → {@code chapterNumber} as {@code String}
 *       (Roman numerals: "I"–"X"); converted to {@code int} by the service
 *       before DB insertion</li>
 * </ul>
 *
 * <p>{@code source_chunk_id} is not present in the JSON files — the
 * {@link LegalChunkIngestionService} generates it from structural metadata
 * for idempotent re-ingestion.
 *
 * <p>Unknown fields are ignored so new extraction fields do not break
 * existing ingestion runs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LegalChunkDto(

        // ── Content ────────────────────────────────────────────────────────

        @JsonProperty("content")
        String content,

        // ── Type classification ────────────────────────────────────────────

        /** Granular: article_paragraph | annex_point | recital | definition_entry */
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

        /** Article title (JSON field: article_title) */
        @JsonProperty("article_title")
        String title,

        // ── Chapter / section fields ───────────────────────────────────────

        /**
         * Chapter identifier as Roman numeral string (JSON: "I"–"X").
         * Stored as INTEGER in the DB after conversion.
         */
        @JsonProperty("chapter_number")
        String chapterNumber,

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

        /** Point identifier within the annex (e.g. "1", "2a", "intro") */
        @JsonProperty("annex_point")
        String annexPoint,

        /** Named section within the annex (e.g. "A", "B", "1", "2") */
        @JsonProperty("annex_section")
        String annexSection,

        // ── Risk / compliance metadata ─────────────────────────────────────

        @JsonProperty("risk_level")
        String riskLevel,

        @JsonProperty("citation_eligible")
        Boolean citationEligible,

        // ── Source / versioning ────────────────────────────────────────────

        /** Source document name (JSON field: source_document) */
        @JsonProperty("source_document")
        String source,

        @JsonProperty("publish_date")
        String publishDate,

        @JsonProperty("version")
        String version,

        @JsonProperty("status")
        String status
) {}
