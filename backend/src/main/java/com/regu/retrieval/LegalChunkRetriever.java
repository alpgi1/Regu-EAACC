package com.regu.retrieval;

import com.regu.retrieval.dto.RetrievalQuery;
import com.regu.retrieval.dto.RetrievalResult;
import com.regu.retrieval.dto.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Retrieves chunks from {@code legal_chunks} using Reciprocal Rank Fusion (RRF)
 * hybrid search — combining pgvector cosine similarity with PostgreSQL full-text
 * search (tsvector / GIN index).
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Embed the query text via {@link QueryEmbeddingClient}.</li>
 *   <li>Run vector search: {@code ORDER BY embedding <=> :vec LIMIT k*2}.</li>
 *   <li>Run keyword search: {@code WHERE search_vector @@ plainto_tsquery(...)
 *       ORDER BY ts_rank(...) LIMIT k*2}.</li>
 *   <li>Merge via RRF: score = 1/(60+rank_v) + 1/(60+rank_kw). Chunks that
 *       appear in both lists receive the combined score.</li>
 *   <li>Return top-k by RRF score.</li>
 * </ol>
 *
 * <p>Metadata filters are applied to BOTH sub-queries before ranking so the
 * filter is respected in both retrieval paths.
 */
@Service
public class LegalChunkRetriever implements ChunkRetriever {

    private static final Logger log = LoggerFactory.getLogger(LegalChunkRetriever.class);
    private static final String TABLE  = "legal_chunks";
    /** RRF constant k — standard value recommended in the original paper. */
    private static final int    RRF_K  = 60;

    private final NamedParameterJdbcTemplate jdbc;
    private final QueryEmbeddingClient       embedder;

    public LegalChunkRetriever(NamedParameterJdbcTemplate jdbc, QueryEmbeddingClient embedder) {
        this.jdbc    = jdbc;
        this.embedder = embedder;
    }

    @Override
    public String getSourceTable() { return TABLE; }

    @Override
    public RetrievalResult retrieve(RetrievalQuery query) {
        long t0 = System.currentTimeMillis();
        String queryVec  = embedder.embedQueryForSql(query.queryText());
        int    k         = query.maxResults();
        int    fetchK    = k * 2; // fetch more for RRF merging

        Map<String, Object> filters = query.filters();

        // ── 1. Vector search ─────────────────────────────────────────────
        String vecSql = buildVectorSql(filters);
        MapSqlParameterSource vecParams = buildBaseParams(queryVec, fetchK, filters);
        List<Map<String, Object>> vecRows = jdbc.queryForList(vecSql, vecParams);

        // ── 2. Keyword search ─────────────────────────────────────────────
        String kwSql = buildKeywordSql(filters);
        MapSqlParameterSource kwParams = buildKeywordParams(query.queryText(), fetchK, filters);
        List<Map<String, Object>> kwRows = jdbc.queryForList(kwSql, kwParams);

        // ── 3. RRF fusion ─────────────────────────────────────────────────
        // Map: chunkId → {row, rrfScore}
        Map<Long, double[]> rrfScores = new LinkedHashMap<>();
        Map<Long, Map<String, Object>> rowByChunk = new LinkedHashMap<>();

        for (int rank = 0; rank < vecRows.size(); rank++) {
            Map<String, Object> row   = vecRows.get(rank);
            long                chunkId = ((Number) row.get("id")).longValue();
            double              score   = 1.0 / (RRF_K + rank + 1);
            rrfScores.computeIfAbsent(chunkId, id -> new double[]{0.0})[0] += score;
            rowByChunk.putIfAbsent(chunkId, row);
        }
        for (int rank = 0; rank < kwRows.size(); rank++) {
            Map<String, Object> row     = kwRows.get(rank);
            long                chunkId = ((Number) row.get("id")).longValue();
            double              score   = 1.0 / (RRF_K + rank + 1);
            rrfScores.computeIfAbsent(chunkId, id -> new double[]{0.0})[0] += score;
            rowByChunk.putIfAbsent(chunkId, row);
        }

        // Sort by RRF score desc, take top-k
        List<RetrievedChunk> chunks = rrfScores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
                .limit(k)
                .map(e -> toChunk(rowByChunk.get(e.getKey()), e.getValue()[0]))
                .toList();

        long ms = System.currentTimeMillis() - t0;
        log.info("Retrieved {} chunks from {} via hybrid-rrf in {}ms", chunks.size(), TABLE, ms);
        return new RetrievalResult(chunks, query.queryText(), ms, "hybrid-rrf");
    }

    // ── SQL builders ─────────────────────────────────────────────────────

    private String buildVectorSql(Map<String, Object> filters) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, content, content_with_context,
                       source_chunk_id, article_number, paragraph_number,
                       article_number as title_article, title, risk_level,
                       document_type, citation_eligible,
                       1 - (embedding <=> :queryVector::vector) AS score
                FROM legal_chunks
                WHERE 1=1
                """);
        appendFilters(sql, filters);
        sql.append(" ORDER BY embedding <=> :queryVector::vector LIMIT :limit");
        return sql.toString();
    }

    private String buildKeywordSql(Map<String, Object> filters) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, content, content_with_context,
                       source_chunk_id, article_number, paragraph_number,
                       title, risk_level, document_type, citation_eligible,
                       ts_rank(search_vector, plainto_tsquery('english', :queryText)) AS score
                FROM legal_chunks
                WHERE search_vector @@ plainto_tsquery('english', :queryText)
                """);
        appendFilters(sql, filters);
        sql.append(" ORDER BY ts_rank(search_vector, plainto_tsquery('english', :queryText)) DESC LIMIT :limit");
        return sql.toString();
    }

    private void appendFilters(StringBuilder sql, Map<String, Object> filters) {
        if (filters.containsKey("article_number"))
            sql.append(" AND article_number = :articleNumber");
        if (filters.containsKey("risk_level"))
            sql.append(" AND risk_level = :riskLevel");
        if (filters.containsKey("document_type"))
            sql.append(" AND document_type = :documentType");
        if (filters.containsKey("citation_eligible"))
            sql.append(" AND citation_eligible = :citationEligible");
    }

    private MapSqlParameterSource buildBaseParams(String vec, int limit,
                                                  Map<String, Object> filters) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("queryVector", vec)
                .addValue("limit", limit);
        addFilterParams(p, filters);
        return p;
    }

    private MapSqlParameterSource buildKeywordParams(String text, int limit,
                                                     Map<String, Object> filters) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("queryText", text)
                .addValue("limit", limit);
        addFilterParams(p, filters);
        return p;
    }

    private void addFilterParams(MapSqlParameterSource p, Map<String, Object> filters) {
        if (filters.containsKey("article_number"))
            p.addValue("articleNumber", filters.get("article_number"));
        if (filters.containsKey("risk_level"))
            p.addValue("riskLevel", filters.get("risk_level"));
        if (filters.containsKey("document_type"))
            p.addValue("documentType", filters.get("document_type"));
        if (filters.containsKey("citation_eligible"))
            p.addValue("citationEligible", filters.get("citation_eligible"));
    }

    // ── Row mapping ───────────────────────────────────────────────────────

    private RetrievedChunk toChunk(Map<String, Object> row, double score) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "source_chunk_id", row, "source_chunk_id");
        putIfPresent(metadata, "article_number",  row, "article_number");
        putIfPresent(metadata, "paragraph_number", row, "paragraph_number");
        putIfPresent(metadata, "article_title",   row, "title");
        putIfPresent(metadata, "risk_level",       row, "risk_level");
        putIfPresent(metadata, "document_type",    row, "document_type");
        putIfPresent(metadata, "citation_eligible", row, "citation_eligible");

        return new RetrievedChunk(
                ((Number) row.get("id")).longValue(),
                TABLE,
                (String) row.get("content"),
                (String) row.get("content_with_context"),
                score,
                metadata
        );
    }

    private void putIfPresent(Map<String, Object> target, String key,
                               Map<String, Object> source, String sourceKey) {
        Object val = source.get(sourceKey);
        if (val != null) target.put(key, val);
    }
}
