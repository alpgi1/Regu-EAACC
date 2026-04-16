package com.regu.retrieval;

import com.regu.retrieval.dto.RetrievalQuery;
import com.regu.retrieval.dto.RetrievalResult;
import com.regu.retrieval.dto.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Single entry point for all retrieval operations in Phase 4.
 *
 * <p>Provides four public methods:
 * <ol>
 *   <li>{@link #retrieveFromAll} — parallel query across all four tables with
 *       result merging and deduplication.</li>
 *   <li>{@link #retrieveFromTable} — targeted retrieval against one table by name.</li>
 *   <li>{@link #retrieveLegalWithArticles} — metadata-only lookup of all
 *       legal_chunks whose article_number appears in the supplied list.
 *       No embedding — used in Phase 5 after risk classification.</li>
 *   <li>{@link #retrieveByRuleIds} — deterministic lookup of decision_rule_chunks
 *       by stable rule_id. No embedding — used for linked_decision_rules resolution.</li>
 * </ol>
 *
 * <p>All methods are stateless and thread-safe.
 */
@Service
public class RetrievalOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RetrievalOrchestrator.class);

    /** Thread pool for parallel table searches. Named for diagnosis in thread dumps. */
    private static final ExecutorService RETRIEVAL_POOL =
            Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "retrieval-pool");
                t.setDaemon(true);
                return t;
            });

    private final LegalChunkRetriever    legalRetriever;
    private final UseCaseRetriever       useCaseRetriever;
    private final GuideChunkRetriever    guideRetriever;
    private final DecisionRuleRetriever  decisionRuleRetriever;
    private final NamedParameterJdbcTemplate jdbc;

    /** Map for O(1) routing by table name. */
    private final Map<String, ChunkRetriever> retrieverByTable;

    public RetrievalOrchestrator(
            LegalChunkRetriever legalRetriever,
            UseCaseRetriever useCaseRetriever,
            GuideChunkRetriever guideRetriever,
            DecisionRuleRetriever decisionRuleRetriever,
            NamedParameterJdbcTemplate jdbc) {
        this.legalRetriever       = legalRetriever;
        this.useCaseRetriever     = useCaseRetriever;
        this.guideRetriever       = guideRetriever;
        this.decisionRuleRetriever = decisionRuleRetriever;
        this.jdbc                 = jdbc;

        Map<String, ChunkRetriever> map = new LinkedHashMap<>();
        map.put(legalRetriever.getSourceTable(),        legalRetriever);
        map.put(useCaseRetriever.getSourceTable(),      useCaseRetriever);
        map.put(guideRetriever.getSourceTable(),        guideRetriever);
        map.put(decisionRuleRetriever.getSourceTable(), decisionRuleRetriever);
        this.retrieverByTable = Collections.unmodifiableMap(map);
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Runs all four retrievers in parallel and merges their results into a
     * single ranked list. Chunks are de-duplicated by (sourceTable, chunkId).
     * The merged list is sorted by similarity score descending.
     *
     * @param query the retrieval request
     * @return merged, deduplicated result set
     */
    public RetrievalResult retrieveFromAll(RetrievalQuery query) {
        long t0 = System.currentTimeMillis();
        log.info("Parallel retrieval from all tables for query: [{}]", query.queryText());

        // Embed the query ONCE here — but since each retriever calls embedder independently,
        // we let them do it (each gets the same text, Voyage is fast for single queries).
        // In Phase 5, we can refactor to pass the vector directly.
        List<CompletableFuture<RetrievalResult>> futures = retrieverByTable.values().stream()
                .map(retriever -> CompletableFuture.supplyAsync(
                        () -> safeRetrieve(retriever, query), RETRIEVAL_POOL))
                .toList();

        List<RetrievalResult> results = futures.stream()
                .map(f -> {
                    try { return f.get(10, TimeUnit.SECONDS); }
                    catch (Exception e) {
                        log.warn("Retriever timed out or failed: {}", e.getMessage());
                        return RetrievalResult.empty(query.queryText(), 0, "error");
                    }
                })
                .toList();

        // Flatten, deduplicate, sort by score
        Set<String> seen = new LinkedHashSet<>();
        List<RetrievedChunk> merged = results.stream()
                .flatMap(r -> r.chunks().stream())
                .filter(c -> seen.add(c.sourceTable() + ":" + c.chunkId()))
                .sorted(Comparator.comparingDouble(RetrievedChunk::similarityScore).reversed())
                .limit(query.maxResults() * retrieverByTable.size()) // generous cap
                .toList();

        long ms = System.currentTimeMillis() - t0;
        log.info("Parallel retrieval complete: {} merged chunks in {}ms", merged.size(), ms);
        return new RetrievalResult(merged, query.queryText(), ms, "parallel-all");
    }

    /**
     * Routes a query to a single retriever by table name alias.
     *
     * <p>Recognised aliases: {@code legal}, {@code use_case}, {@code guide},
     * {@code decision_rule} (short forms accepted, plus full table names).
     *
     * @param tableName the target table or alias
     * @param query     the retrieval request
     * @return result from the specified retriever
     * @throws IllegalArgumentException if {@code tableName} is not recognised
     */
    public RetrievalResult retrieveFromTable(String tableName, RetrievalQuery query) {
        ChunkRetriever retriever = resolveRetriever(tableName);
        return retriever.retrieve(query);
    }

    /**
     * Metadata-only lookup of {@code legal_chunks} by article number.
     * No embedding is generated. Returns all chunks (all paragraphs) for the
     * requested articles, ordered by article_number, paragraph_number.
     *
     * <p>Used in Phase 5 Stage 4 after risk classification to pull the full
     * obligation text for Articles 9–15 (High Risk), Article 50 (Transparency), etc.
     *
     * @param articleNumbers EU AI Act article numbers
     * @return RetrievalResult with matching chunks, strategy = "metadata"
     */
    public RetrievalResult retrieveLegalWithArticles(int[] articleNumbers) {
        long t0 = System.currentTimeMillis();
        if (articleNumbers == null || articleNumbers.length == 0) {
            return RetrievalResult.empty("", 0, "metadata");
        }

        List<Integer> nums = new ArrayList<>();
        for (int n : articleNumbers) nums.add(n);

        String sql = """
                SELECT id, content, content_with_context,
                       source_chunk_id, article_number, paragraph_number,
                       title, risk_level, document_type, citation_eligible
                FROM legal_chunks
                WHERE article_number IN (:articleNumbers)
                ORDER BY article_number, paragraph_number
                """;

        List<Map<String, Object>> rows = jdbc.queryForList(sql, Map.of("articleNumbers", nums));
        List<RetrievedChunk> chunks = rows.stream().map(row -> {
            Map<String, Object> meta = new LinkedHashMap<>();
            putIfPresent(meta, "source_chunk_id",  row, "source_chunk_id");
            putIfPresent(meta, "article_number",   row, "article_number");
            putIfPresent(meta, "paragraph_number", row, "paragraph_number");
            putIfPresent(meta, "article_title",    row, "title");
            putIfPresent(meta, "risk_level",       row, "risk_level");
            putIfPresent(meta, "document_type",    row, "document_type");
            putIfPresent(meta, "citation_eligible", row, "citation_eligible");

            return new RetrievedChunk(
                    ((Number) row.get("id")).longValue(),
                    "legal_chunks",
                    (String) row.get("content"),
                    (String) row.get("content_with_context"),
                    1.0, // metadata lookup — no similarity score
                    meta
            );
        }).toList();

        long ms = System.currentTimeMillis() - t0;
        log.info("Metadata lookup: retrieved {} legal_chunks for articles {} in {}ms",
                chunks.size(), nums, ms);
        return new RetrievalResult(chunks, "article_lookup:" + nums, ms, "metadata");
    }

    /**
     * Deterministic lookup of {@code decision_rule_chunks} by stable rule_id.
     * No embedding. Returns exactly the chunks matching the given rule IDs,
     * preserving the order of the input list.
     *
     * <p>Used for resolving {@code use_case_chunks.linked_decision_rules}
     * references without semantic search in Phase 5.
     *
     * @param ruleIds list of stable rule identifiers (e.g. {@code "fli_q_hr4"})
     * @return chunks matching the given rule IDs (may be fewer than requested
     *         if some IDs are not found in the database)
     */
    public List<RetrievedChunk> retrieveByRuleIds(List<String> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) return List.of();

        String sql = """
                SELECT id, content, rule_id, chunk_type,
                       section, section_path, topic,
                       related_articles, related_annex_points,
                       source_document, publisher
                FROM decision_rule_chunks
                WHERE rule_id IN (:ruleIds)
                """;

        List<Map<String, Object>> rows = jdbc.queryForList(sql, Map.of("ruleIds", ruleIds));

        // Return in the same order as the input list
        Map<String, Map<String, Object>> byRuleId = rows.stream()
                .collect(Collectors.toMap(
                        r -> (String) r.get("rule_id"),
                        r -> r,
                        (a, b) -> a));

        return ruleIds.stream()
                .filter(byRuleId::containsKey)
                .map(id -> {
                    Map<String, Object> row = byRuleId.get(id);
                    Map<String, Object> meta = new LinkedHashMap<>();
                    putIfPresent(meta, "rule_id",             row, "rule_id");
                    putIfPresent(meta, "chunk_type",          row, "chunk_type");
                    putIfPresent(meta, "section",             row, "section");
                    putIfPresent(meta, "section_path",        row, "section_path");
                    putIfPresent(meta, "topic",               row, "topic");
                    putIfPresent(meta, "related_articles",    row, "related_articles");
                    putIfPresent(meta, "related_annex_points", row, "related_annex_points");
                    putIfPresent(meta, "source_document",     row, "source_document");
                    putIfPresent(meta, "publisher",           row, "publisher");
                    return new RetrievedChunk(
                            ((Number) row.get("id")).longValue(),
                            "decision_rule_chunks",
                            (String) row.get("content"),
                            null,
                            1.0,
                            meta
                    );
                })
                .toList();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private ChunkRetriever resolveRetriever(String alias) {
        return switch (alias.toLowerCase(Locale.ROOT)) {
            case "legal", "legal_chunks"                -> legalRetriever;
            case "use_case", "use_cases", "use_case_chunks" -> useCaseRetriever;
            case "guide", "guides", "guide_chunks"      -> guideRetriever;
            case "decision_rule", "decision_rules",
                 "decision_rule_chunks"                 -> decisionRuleRetriever;
            default -> throw new IllegalArgumentException(
                    "Unknown retrieval table: '" + alias + "'. " +
                    "Valid values: legal, use_case, guide, decision_rule");
        };
    }

    private RetrievalResult safeRetrieve(ChunkRetriever retriever, RetrievalQuery query) {
        try {
            return retriever.retrieve(query);
        } catch (Exception e) {
            log.error("Retriever for {} failed: {}", retriever.getSourceTable(), e.getMessage(), e);
            return RetrievalResult.empty(query.queryText(), 0, "error");
        }
    }

    private void putIfPresent(Map<String, Object> target, String key,
                               Map<String, Object> source, String sourceKey) {
        Object val = source.get(sourceKey);
        if (val != null) target.put(key, convertArray(val));
    }

    private Object convertArray(Object val) {
        if (val instanceof Array sqlArr) {
            try {
                Object[] raw = (Object[]) sqlArr.getArray();
                List<Object> list = new ArrayList<>(raw.length);
                for (Object o : raw) list.add(o);
                return list;
            } catch (SQLException e) {
                return val;
            }
        }
        return val;
    }
}
