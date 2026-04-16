package com.regu.ingestion.legal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regu.ingestion.DecisionRuleChunkIngestionService;
import com.regu.ingestion.VoyageEmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Ingests EU AI Act legal text chunks into the {@code legal_chunks} table.
 *
 * <p>Reads all {@code batch_*.json} files under
 * {@code corpus/legal/raw_extraction/}, generates {@code voyage-3-large}
 * embeddings in batches of up to 128, and inserts rows. Any chunk whose
 * {@code source_chunk_id} already exists in the database is skipped so the
 * operation is idempotent.
 *
 * <p>{@code content_with_context} is constructed from the chunk's structural
 * metadata — it is <em>not</em> read from the JSON file:
 * <ul>
 *   <li>Recital: {@code "[Recital N]\n\n{content}"}</li>
 *   <li>Article paragraph: {@code "[Article N — Title]\n[Chapter C, Section S]\n\nParagraph P:\n{content}"}</li>
 *   <li>Annex point: {@code "[Annex N, Section S, Point P]\n\n{content}"}</li>
 *   <li>Definition: {@code "[Definition]\n\n{content}"}</li>
 * </ul>
 *
 * <p>Individual chunk errors are captured and returned in the result rather
 * than halting the entire batch. A chunk with a missing {@code source_chunk_id}
 * or {@code content} is skipped with an error entry.
 *
 * <p>Only active under the {@code ingest} Spring profile.
 */
@Service
@Profile("ingest")
public class LegalChunkIngestionService {

    private static final Logger       log           = LoggerFactory.getLogger(LegalChunkIngestionService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final TypeReference<List<LegalChunkDto>> CHUNK_LIST_TYPE =
            new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final VoyageEmbeddingClient      voyageClient;
    private final Path                       rawExtractionDir;

    public LegalChunkIngestionService(
            NamedParameterJdbcTemplate jdbc,
            VoyageEmbeddingClient voyageClient,
            @Value("${regu.ingestion.corpus-root}") String corpusRoot) {
        this.jdbc             = jdbc;
        this.voyageClient     = voyageClient;
        this.rawExtractionDir = Path.of(corpusRoot).resolve("legal/raw_extraction");
    }

    /**
     * Ingests all legal chunks from all batch files. Skips any chunk whose
     * {@code source_chunk_id} already exists in the database (idempotent).
     *
     * @return ingestion result with counts and per-chunk error details
     * @throws IOException if the raw_extraction directory cannot be listed
     */
    @Transactional
    public IngestionResult ingestAll() throws IOException {
        List<Path> files = Files.list(rawExtractionDir)
                .filter(p -> p.getFileName().toString().startsWith("batch_")
                        && p.getFileName().toString().endsWith(".json"))
                .sorted()
                .collect(Collectors.toList());

        log.info("Found {} batch JSON files in {}", files.size(), rawExtractionDir);

        // ── 1. Parse all chunks from all files ───────────────────────────
        List<LegalChunkDto> allChunks = new ArrayList<>();
        for (Path file : files) {
            if (Files.size(file) == 0) {
                log.warn("Skipping empty batch file: {}", file.getFileName());
                continue;
            }
            List<LegalChunkDto> batch = OBJECT_MAPPER.readValue(file.toFile(), CHUNK_LIST_TYPE);
            log.info("  {} — {} chunks", file.getFileName(), batch.size());
            allChunks.addAll(batch);
        }

        log.info("Total legal chunks parsed: {}", allChunks.size());

        if (allChunks.isEmpty()) {
            log.info("No chunks to ingest — all batch files are empty");
            return new IngestionResult(0, 0, 0, List.of());
        }

        // ── 2. Validate and separate new vs existing ─────────────────────
        List<String>        errors        = new ArrayList<>();
        List<LegalChunkDto> validNew      = new ArrayList<>();
        int                 invalidCount  = 0;

        // Collect all source_chunk_ids from valid chunks to check existing
        List<String> allIds = new ArrayList<>();
        for (LegalChunkDto chunk : allChunks) {
            if (chunk.sourceChunkId() == null || chunk.sourceChunkId().isBlank()) {
                errors.add("Chunk missing source_chunk_id (document_type=" + chunk.documentType()
                        + ", article=" + chunk.articleNumber() + ", recital=" + chunk.recitalNumber() + ")");
                invalidCount++;
                continue;
            }
            if (chunk.content() == null || chunk.content().isBlank()) {
                errors.add("Chunk has empty content: source_chunk_id=" + chunk.sourceChunkId());
                invalidCount++;
                continue;
            }
            allIds.add(chunk.sourceChunkId());
        }

        Set<String> existing = fetchExistingIds(allIds);
        log.info("{} source_chunk_id(s) already exist — will skip", existing.size());

        for (LegalChunkDto chunk : allChunks) {
            if (chunk.sourceChunkId() == null || chunk.sourceChunkId().isBlank()) continue;
            if (chunk.content()       == null || chunk.content().isBlank())       continue;
            if (!existing.contains(chunk.sourceChunkId())) {
                validNew.add(chunk);
            }
        }

        int skipped = allChunks.size() - invalidCount - validNew.size();

        if (validNew.isEmpty()) {
            log.info("All {} valid legal chunks already ingested — nothing to do",
                    allChunks.size() - invalidCount);
            return new IngestionResult(0, skipped, invalidCount, errors);
        }

        // ── 3. Build content_with_context strings for embedding ───────────
        List<String> contextualisedTexts = validNew.stream()
                .map(LegalChunkIngestionService::buildContentWithContext)
                .collect(Collectors.toList());

        // ── 4. Batch embed ────────────────────────────────────────────────
        log.info("Generating embeddings for {} new legal chunks", validNew.size());
        List<float[]> embeddings = voyageClient.embedBatch(contextualisedTexts);

        // ── 5. Insert ─────────────────────────────────────────────────────
        int inserted = 0;
        for (int i = 0; i < validNew.size(); i++) {
            LegalChunkDto chunk = validNew.get(i);
            try {
                insertChunk(chunk, contextualisedTexts.get(i), embeddings.get(i));
                log.debug("Inserted legal_chunk source_chunk_id='{}'", chunk.sourceChunkId());
                inserted++;
            } catch (Exception e) {
                String msg = "Insert failed for source_chunk_id='" + chunk.sourceChunkId()
                        + "': " + e.getMessage();
                log.warn(msg);
                errors.add(msg);
            }
        }

        log.info("Legal chunk ingestion complete: {} inserted, {} skipped, {} errors",
                inserted, skipped, errors.size());
        return new IngestionResult(inserted, skipped, errors.size(), errors);
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private Set<String> fetchExistingIds(List<String> ids) {
        if (ids.isEmpty()) return Set.of();
        String       sql   = "SELECT source_chunk_id FROM legal_chunks WHERE source_chunk_id IN (:ids)";
        List<String> found = jdbc.queryForList(sql, Map.of("ids", ids), String.class);
        return new HashSet<>(found);
    }

    private void insertChunk(LegalChunkDto chunk, String contentWithContext, float[] embedding) {
        String sql = """
                INSERT INTO legal_chunks
                    (content, content_with_context, embedding,
                     source_chunk_id, chunk_type, document_type,
                     source, title, risk_level,
                     article_number, paragraph_number,
                     recital_number,
                     annex_number, annex_point, annex_section,
                     chapter_number, chapter_title,
                     section_number, section_title,
                     citation_eligible,
                     publish_date, version, status)
                VALUES
                    (:content, :contentWithContext, :embedding::vector,
                     :sourceChunkId, :chunkType, :documentType,
                     :source, :title, :riskLevel,
                     :articleNumber, :paragraphNumber,
                     :recitalNumber,
                     :annexNumber, :annexPoint, :annexSection,
                     :chapterNumber, :chapterTitle,
                     :sectionNumber, :sectionTitle,
                     :citationEligible,
                     :publishDate, :version, :status)
                """;

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("content",          chunk.content())
                .addValue("contentWithContext", contentWithContext)
                .addValue("embedding",         DecisionRuleChunkIngestionService.formatVector(embedding))
                .addValue("sourceChunkId",     chunk.sourceChunkId())
                .addValue("chunkType",         chunk.chunkType())
                .addValue("documentType",      chunk.documentType())
                .addValue("source",            chunk.source())
                .addValue("title",             chunk.title())
                .addValue("riskLevel",         chunk.riskLevel())
                .addValue("articleNumber",     chunk.articleNumber())
                .addValue("paragraphNumber",   chunk.paragraphNumber())
                .addValue("recitalNumber",     chunk.recitalNumber())
                .addValue("annexNumber",       chunk.annexNumber())
                .addValue("annexPoint",        chunk.annexPoint())
                .addValue("annexSection",      chunk.annexSection())
                .addValue("chapterNumber",     chunk.chapterNumber())
                .addValue("chapterTitle",      chunk.chapterTitle())
                .addValue("sectionNumber",     chunk.sectionNumber())
                .addValue("sectionTitle",      chunk.sectionTitle())
                .addValue("citationEligible",  chunk.citationEligible() == null || chunk.citationEligible())
                .addValue("publishDate",       LocalDate.parse(chunk.publishDate()))
                .addValue("version",           chunk.version())
                .addValue("status",            chunk.status());

        jdbc.update(sql, p);
    }

    /**
     * Builds the {@code content_with_context} string that gets embedded.
     * The prefix contextualises the chunk within the EU AI Act structure so
     * that embeddings carry document position, not just semantic content.
     */
    static String buildContentWithContext(LegalChunkDto chunk) {
        String type    = chunk.documentType() != null ? chunk.documentType() : "";
        String content = chunk.content();

        return switch (type) {
            case "recital" -> buildRecitalContext(chunk, content);
            case "article" -> buildArticleContext(chunk, content);
            case "annex"   -> buildAnnexContext(chunk, content);
            default        -> content; // definition_entry and unknowns: no prefix
        };
    }

    private static String buildRecitalContext(LegalChunkDto chunk, String content) {
        if (chunk.recitalNumber() != null) {
            return "[Recital " + chunk.recitalNumber() + "]\n\n" + content;
        }
        return content;
    }

    private static String buildArticleContext(LegalChunkDto chunk, String content) {
        StringBuilder sb = new StringBuilder();

        // Line 1: article + title
        if (chunk.articleNumber() != null) {
            sb.append("[Article ").append(chunk.articleNumber());
            if (chunk.title() != null && !chunk.title().isBlank()) {
                sb.append(" \u2014 ").append(chunk.title());
            }
            sb.append(']');
        }

        // Line 2: chapter + optional section
        if (chunk.chapterNumber() != null) {
            sb.append('\n').append("[Chapter ").append(chunk.chapterNumber());
            if (chunk.chapterTitle() != null && !chunk.chapterTitle().isBlank()) {
                sb.append(" \u2014 ").append(chunk.chapterTitle());
            }
            if (chunk.sectionTitle() != null && !chunk.sectionTitle().isBlank()) {
                sb.append(", Section ").append(chunk.sectionTitle());
            }
            sb.append(']');
        }

        if (!sb.isEmpty()) sb.append("\n\n");

        // Paragraph label
        if (chunk.paragraphNumber() != null) {
            sb.append("Paragraph ").append(chunk.paragraphNumber()).append(":\n");
        }

        sb.append(content);
        return sb.toString();
    }

    private static String buildAnnexContext(LegalChunkDto chunk, String content) {
        StringBuilder sb = new StringBuilder("[Annex");

        if (chunk.annexNumber() != null && !chunk.annexNumber().isBlank()) {
            sb.append(' ').append(chunk.annexNumber());
        }
        if (chunk.annexSection() != null && !chunk.annexSection().isBlank()) {
            sb.append(", ").append(chunk.annexSection());
        }
        if (chunk.annexPoint() != null && !chunk.annexPoint().isBlank()) {
            sb.append(", Point ").append(chunk.annexPoint());
        }
        sb.append(']');

        return sb + "\n\n" + content;
    }

    // ── Result record ────────────────────────────────────────────────────

    /**
     * Ingestion result returned by {@link #ingestAll()}.
     *
     * @param inserted      number of new rows successfully inserted
     * @param skipped       number of rows skipped (already existed)
     * @param errors        number of chunks that could not be ingested
     * @param errorMessages per-chunk error descriptions
     */
    public record IngestionResult(
            int          inserted,
            int          skipped,
            int          errors,
            List<String> errorMessages
    ) {}
}
