package com.regu.retrieval;

import com.regu.retrieval.dto.RetrievalQuery;
import com.regu.retrieval.dto.RetrievalResult;
import com.regu.retrieval.dto.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieves chunks from {@code use_case_chunks} using pure vector similarity.
 *
 * <p>Use case chunks are written in natural user-facing language
 * ("an HR agency uses AI to screen job applicants…"), which makes
 * semantic similarity the ideal search method — keyword overlap is unreliable
 * for bridging user descriptions to legal scenarios.
 *
 * <p>Supported filters: {@code risk_category}, {@code sector}, {@code actor_role}.
 */
@Service
public class UseCaseRetriever implements ChunkRetriever {

    private static final Logger log = LoggerFactory.getLogger(UseCaseRetriever.class);
    private static final String TABLE = "use_case_chunks";

    private final NamedParameterJdbcTemplate jdbc;
    private final QueryEmbeddingClient       embedder;

    public UseCaseRetriever(NamedParameterJdbcTemplate jdbc, QueryEmbeddingClient embedder) {
        this.jdbc    = jdbc;
        this.embedder = embedder;
    }

    @Override
    public String getSourceTable() { return TABLE; }

    @Override
    public RetrievalResult retrieve(RetrievalQuery query) {
        long t0 = System.currentTimeMillis();
        String vec = embedder.embedQueryForSql(query.queryText());
        Map<String, Object> filters = query.filters();

        StringBuilder sql = new StringBuilder("""
                SELECT id, content, scenario_name, scenario_domain,
                       risk_category, primary_legal_basis,
                       use_case_id, sector, actor_role, sme_privilege,
                       legal_basis,
                       1 - (embedding <=> :queryVector::vector) AS score
                FROM use_case_chunks
                WHERE 1=1
                """);

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("queryVector", vec)
                .addValue("limit", query.maxResults());

        if (filters.containsKey("risk_category")) {
            sql.append(" AND risk_category = :riskCategory");
            p.addValue("riskCategory", filters.get("risk_category"));
        }
        if (filters.containsKey("sector")) {
            sql.append(" AND sector = :sector");
            p.addValue("sector", filters.get("sector"));
        }
        if (filters.containsKey("actor_role")) {
            sql.append(" AND actor_role = :actorRole");
            p.addValue("actorRole", filters.get("actor_role"));
        }

        sql.append(" ORDER BY embedding <=> :queryVector::vector LIMIT :limit");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), p);
        List<RetrievedChunk> chunks = rows.stream().map(this::toChunk).toList();

        long ms = System.currentTimeMillis() - t0;
        log.info("Retrieved {} chunks from {} via vector in {}ms", chunks.size(), TABLE, ms);
        return new RetrievalResult(chunks, query.queryText(), ms, "vector");
    }

    private RetrievedChunk toChunk(Map<String, Object> row) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "use_case_id",       row, "use_case_id");
        putIfPresent(metadata, "scenario_name",      row, "scenario_name");
        putIfPresent(metadata, "sector",             row, "sector");
        putIfPresent(metadata, "risk_category",      row, "risk_category");
        putIfPresent(metadata, "actor_role",         row, "actor_role");
        putIfPresent(metadata, "sme_privilege",      row, "sme_privilege");
        putIfPresent(metadata, "legal_basis",        row, "legal_basis");
        putIfPresent(metadata, "primary_legal_basis", row, "primary_legal_basis");

        double score = row.get("score") instanceof Number n ? n.doubleValue() : 0.0;
        return new RetrievedChunk(
                ((Number) row.get("id")).longValue(),
                TABLE,
                (String) row.get("content"),
                null, // use_case_chunks has no content_with_context column
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
