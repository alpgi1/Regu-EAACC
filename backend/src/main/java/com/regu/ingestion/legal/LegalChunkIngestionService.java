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
 * generated {@code source_chunk_id} already exists in the database is skipped,
 * making the operation idempotent.
 *
 * <h3>source_chunk_id generation</h3>
 * <p>The JSON files do not carry a {@code source_chunk_id} field. The service
 * derives a stable ID from the chunk's structural position:
 * <ul>
 *   <li>Recital — {@code eu_ai_act_recital_NNN} (zero-padded to 3 digits)</li>
 *   <li>Article paragraph — {@code eu_ai_act_artN_paraN}</li>
 *   <li>Annex point (no section) — {@code eu_ai_act_annexN_ptP}</li>
 *   <li>Annex point (with section) — {@code eu_ai_act_annexN_sSEC_ptP}</li>
 *   <li>Other — {@code eu_ai_act_TYPE_artN_paraN}</li>
 * </ul>
 *
 * <h3>chapter_number conversion</h3>
 * <p>The JSON {@code chapter_number} field uses Roman numerals ("I"–"X").
 * The service converts these to integers before DB insertion (the
 * {@code legal_chunks.chapter_number} column is {@code INTEGER}).
 *
 * <h3>content_with_context construction</h3>
 * <ul>
 *   <li>Recital — {@code "[Recital N]\n\n{content}"}</li>
 *   <li>Article paragraph — {@code "[Article N — Title]\n[Chapter C — ChapterTitle, Section S]\n\nParagraph P:\n{content}"}</li>
 *   <li>Annex point — {@code "[Annex N, Section S, Point P]\n\n{content}"}</li>
 *   <li>Definition / other — raw {@code content}</li>
 * </ul>
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
     * generated {@code source_chunk_id} already exists in the database.
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

        // ── 1. Parse all chunks ───────────────────────────────────────────
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

        log.info("Total legal chunks parsed from files: {}", allChunks.size());

        if (allChunks.isEmpty()) {
            log.info("No chunks to ingest — all batch files are empty");
            return new IngestionResult(0, 0, 0, List.of());
        }

        // ── 2. Validate, generate IDs, separate new vs existing ───────────
        List<String>        errors       = new ArrayList<>();
        int                 invalidCount = 0;
        List<LegalChunkDto> candidates   = new ArrayList<>();
        List<String>        candidateIds = new ArrayList<>();

        // Track IDs seen in this run to de-duplicate across batch files
        Set<String> seenInRun = new LinkedHashSet<>();

        for (LegalChunkDto chunk : allChunks) {
            if (chunk.content() == null || chunk.content().isBlank()) {
                errors.add("Chunk has empty content: " + describeChunk(chunk));
                invalidCount++;
                continue;
            }
            String id = generateSourceChunkId(chunk);
            if (id == null) {
                errors.add("Cannot generate source_chunk_id for: " + describeChunk(chunk));
                invalidCount++;
                continue;
            }
            if (seenInRun.contains(id)) {
                log.debug("In-run duplicate skipped: {}", id);
                continue; // cross-file duplicate (e.g. recitals 177-180)
            }
            seenInRun.add(id);
            candidates.add(chunk);
            candidateIds.add(id);
        }

        Set<String> existing = fetchExistingIds(candidateIds);
        log.info("{} source_chunk_id(s) already exist in DB — will skip", existing.size());

        List<LegalChunkDto> toInsert   = new ArrayList<>();
        List<String>        insertIds  = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            if (!existing.contains(candidateIds.get(i))) {
                toInsert.add(candidates.get(i));
                insertIds.add(candidateIds.get(i));
            }
        }

        int skipped = candidates.size() - toInsert.size();

        if (toInsert.isEmpty()) {
            log.info("All valid legal chunks already ingested — nothing to do");
            return new IngestionResult(0, skipped, invalidCount, errors);
        }

        // ── 3. Build content_with_context for embedding ───────────────────
        List<String> contextualisedTexts = toInsert.stream()
                .map(LegalChunkIngestionService::buildContentWithContext)
                .collect(Collectors.toList());

        // ── 4. Batch embed ────────────────────────────────────────────────
        log.info("Generating embeddings for {} new legal chunks", toInsert.size());
        List<float[]> embeddings = voyageClient.embedBatch(contextualisedTexts);

        // ── 5. Insert ─────────────────────────────────────────────────────
        int inserted = 0;
        for (int i = 0; i < toInsert.size(); i++) {
            LegalChunkDto chunk = toInsert.get(i);
            String        id    = insertIds.get(i);
            try {
                insertChunk(chunk, id, contextualisedTexts.get(i), embeddings.get(i));
                log.debug("Inserted legal_chunk source_chunk_id='{}'", id);
                inserted++;
            } catch (Exception e) {
                String msg = "Insert failed for source_chunk_id='" + id + "': " + e.getMessage();
                log.warn(msg);
                errors.add(msg);
            }
        }

        log.info("Legal chunk ingestion complete: {} inserted, {} skipped, {} errors",
                inserted, skipped, errors.size());
        return new IngestionResult(inserted, skipped, errors.size(), errors);
    }

    // ── source_chunk_id generation ───────────────────────────────────────

    /**
     * Generates a stable {@code source_chunk_id} from the chunk's structural
     * metadata. Returns {@code null} if the metadata is insufficient to form
     * a unique key.
     */
    static String generateSourceChunkId(LegalChunkDto chunk) {
        String type = chunk.chunkType() != null ? chunk.chunkType() : "";
        return switch (type) {
            case "recital" -> {
                if (chunk.recitalNumber() == null) yield null;
                yield String.format("eu_ai_act_recital_%03d", chunk.recitalNumber());
            }
            case "article_paragraph" -> {
                if (chunk.articleNumber() == null || chunk.paragraphNumber() == null) yield null;
                yield String.format("eu_ai_act_art%d_para%d",
                        chunk.articleNumber(), chunk.paragraphNumber());
            }
            case "annex_point" -> buildAnnexId(chunk);
            default -> {
                // definition_entry or other
                if (chunk.articleNumber() == null || chunk.paragraphNumber() == null) yield null;
                String safeType = type.replaceAll("[^a-zA-Z0-9]", "_");
                yield String.format("eu_ai_act_%s_art%d_para%d",
                        safeType, chunk.articleNumber(), chunk.paragraphNumber());
            }
        };
    }

    private static String buildAnnexId(LegalChunkDto chunk) {
        String num = chunk.annexNumber() != null ? chunk.annexNumber() : "UNK";
        String pt  = chunk.annexPoint()  != null ? chunk.annexPoint()  : "0";
        if (chunk.annexSection() != null && !chunk.annexSection().isBlank()) {
            // sanitize section: keep alphanumerics only
            String sec = chunk.annexSection().replaceAll("[^a-zA-Z0-9]", "");
            return String.format("eu_ai_act_annex%s_s%s_pt%s", num, sec, pt);
        }
        return String.format("eu_ai_act_annex%s_pt%s", num, pt);
    }

    // ── content_with_context construction ────────────────────────────────

    /**
     * Builds the {@code content_with_context} string that gets embedded.
     * Contextualises the chunk within the EU AI Act structure so embeddings
     * carry document position, not just semantic content.
     */
    static String buildContentWithContext(LegalChunkDto chunk) {
        String type = chunk.documentType() != null ? chunk.documentType() : "";
        return switch (type) {
            case "recital" -> buildRecitalContext(chunk);
            case "article" -> buildArticleContext(chunk);
            case "annex"   -> buildAnnexContext(chunk);
            default        -> chunk.content(); // definition and others: no prefix
        };
    }

    private static String buildRecitalContext(LegalChunkDto chunk) {
        if (chunk.recitalNumber() != null) {
            return "[Recital " + chunk.recitalNumber() + "]\n\n" + chunk.content();
        }
        return chunk.content();
    }

    private static String buildArticleContext(LegalChunkDto chunk) {
        StringBuilder sb = new StringBuilder();

        // Line 1: article number + title
        if (chunk.articleNumber() != null) {
            sb.append("[Article ").append(chunk.articleNumber());
            if (chunk.title() != null && !chunk.title().isBlank()) {
                sb.append(" \u2014 ").append(chunk.title());
            }
            sb.append(']');
        }

        // Line 2: chapter + optional section
        if (chunk.chapterNumber() != null && !chunk.chapterNumber().isBlank()) {
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

        sb.append(chunk.content());
        return sb.toString();
    }

    private static String buildAnnexContext(LegalChunkDto chunk) {
        StringBuilder sb = new StringBuilder("[Annex");

        if (chunk.annexNumber() != null && !chunk.annexNumber().isBlank()) {
            sb.append(' ').append(chunk.annexNumber());
        }
        if (chunk.annexSection() != null && !chunk.annexSection().isBlank()) {
            sb.append(", Section ").append(chunk.annexSection());
        }
        if (chunk.annexPoint() != null && !chunk.annexPoint().isBlank()) {
            sb.append(", Point ").append(chunk.annexPoint());
        }
        sb.append(']');

        return sb + "\n\n" + chunk.content();
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private Set<String> fetchExistingIds(List<String> ids) {
        if (ids.isEmpty()) return Set.of();
        String       sql   = "SELECT source_chunk_id FROM legal_chunks WHERE source_chunk_id IN (:ids)";
        List<String> found = jdbc.queryForList(sql, Map.of("ids", ids), String.class);
        return new HashSet<>(found);
    }

    private void insertChunk(LegalChunkDto chunk, String sourceChunkId,
                             String contentWithContext, float[] embedding) {
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
                .addValue("content",            chunk.content())
                .addValue("contentWithContext",  contentWithContext)
                .addValue("embedding",           DecisionRuleChunkIngestionService.formatVector(embedding))
                .addValue("sourceChunkId",       sourceChunkId)
                .addValue("chunkType",           chunk.chunkType())
                .addValue("documentType",        chunk.documentType())
                .addValue("source",              chunk.source())
                .addValue("title",               chunk.title())
                .addValue("riskLevel",           chunk.riskLevel())
                .addValue("articleNumber",       chunk.articleNumber())
                .addValue("paragraphNumber",     chunk.paragraphNumber())
                .addValue("recitalNumber",       chunk.recitalNumber())
                .addValue("annexNumber",         chunk.annexNumber())
                .addValue("annexPoint",          chunk.annexPoint())
                .addValue("annexSection",        chunk.annexSection())
                .addValue("chapterNumber",       romanToInt(chunk.chapterNumber()))
                .addValue("chapterTitle",        chunk.chapterTitle())
                .addValue("sectionNumber",       chunk.sectionNumber())
                .addValue("sectionTitle",        chunk.sectionTitle())
                .addValue("citationEligible",    chunk.citationEligible() == null || chunk.citationEligible())
                .addValue("publishDate",         LocalDate.parse(chunk.publishDate()))
                .addValue("version",             chunk.version())
                .addValue("status",              chunk.status());

        jdbc.update(sql, p);
    }

    /**
     * Converts a Roman numeral string to an integer.
     * Handles numerals I–X as used for EU AI Act chapters.
     * Returns {@code null} if the input is null or blank.
     */
    static Integer romanToInt(String roman) {
        if (roman == null || roman.isBlank()) return null;
        Map<Character, Integer> values = Map.of(
                'I', 1, 'V', 5, 'X', 10, 'L', 50, 'C', 100
        );
        int result = 0, prev = 0;
        for (int i = roman.length() - 1; i >= 0; i--) {
            int curr = values.getOrDefault(roman.charAt(i), 0);
            result += curr < prev ? -curr : curr;
            prev   = curr;
        }
        return result == 0 ? null : result;
    }

    private static String describeChunk(LegalChunkDto c) {
        return "chunk_type=" + c.chunkType()
                + " article=" + c.articleNumber()
                + " para=" + c.paragraphNumber()
                + " recital=" + c.recitalNumber()
                + " annex=" + c.annexNumber()
                + "/" + c.annexPoint();
    }

    // ── Result record ────────────────────────────────────────────────────

    /**
     * @param inserted      number of new rows successfully inserted
     * @param skipped       number of rows skipped (already existed or in-run duplicate)
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
