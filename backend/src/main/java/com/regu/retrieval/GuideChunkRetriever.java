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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieves chunks from {@code guide_chunks} using vector similarity with an
 * optional metadata filter on {@code related_articles}.
 *
 * <p>When a {@code related_articles} filter is provided (as a
 * {@code List<Integer>} or {@code int[]} value), the query restricts results
 * to chunks whose {@code related_articles} array overlaps with the requested
 * articles using PostgreSQL's {@code &&} array operator. This lets Phase 5
 * pull guidance specifically scoped to the obligation articles identified for
 * the system under review.
 */
@Service
public class GuideChunkRetriever implements ChunkRetriever {

    private static final Logger log = LoggerFactory.getLogger(GuideChunkRetriever.class);
    private static final String TABLE = "guide_chunks";

    private final NamedParameterJdbcTemplate jdbc;
    private final QueryEmbeddingClient       embedder;

    public GuideChunkRetriever(NamedParameterJdbcTemplate jdbc, QueryEmbeddingClient embedder) {
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
                SELECT id, content, content_with_context,
                       source, title, section_path, related_articles,
                       1 - (embedding <=> :queryVector::vector) AS score
                FROM guide_chunks
                WHERE 1=1
                """);

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("queryVector", vec)
                .addValue("limit", query.maxResults());

        if (filters.containsKey("related_articles")) {
            sql.append(" AND related_articles && :articles::integer[]");
            p.addValue("articles", buildIntArrayLiteral(filters.get("related_articles")));
        }

        sql.append(" ORDER BY embedding <=> :queryVector::vector LIMIT :limit");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), p);
        List<RetrievedChunk> chunks = rows.stream().map(this::toChunk).toList();

        long ms = System.currentTimeMillis() - t0;
        String strategy = filters.containsKey("related_articles") ? "vector+metadata" : "vector";
        log.info("Retrieved {} chunks from {} via {} in {}ms", chunks.size(), TABLE, strategy, ms);
        return new RetrievalResult(chunks, query.queryText(), ms, strategy);
    }

    /**
     * Converts the filter value into a PostgreSQL array literal string
     * ({@code {9,10,14}}) for use with the {@code ::integer[]} cast.
     * Accepts {@code List<?>}, {@code int[]}, or {@code Integer[]}.
     */
    @SuppressWarnings("unchecked")
    private String buildIntArrayLiteral(Object value) {
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(((Number) list.get(i)).intValue());
            }
            return sb.append('}').toString();
        }
        if (value instanceof int[] arr) {
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(arr[i]);
            }
            return sb.append('}').toString();
        }
        return "{}";
    }

    private RetrievedChunk toChunk(Map<String, Object> row) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "source",           row, "source");
        putIfPresent(metadata, "title",            row, "title");
        putIfPresent(metadata, "section_path",     row, "section_path");
        putIfPresent(metadata, "related_articles", row, "related_articles");

        double score = row.get("score") instanceof Number n ? n.doubleValue() : 0.0;
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
        if (val != null) target.put(key, convertArray(val));
    }

    /**
     * Converts a PostgreSQL {@link java.sql.Array} result to a plain Java List
     * to avoid Jackson nesting-depth exceptions during serialization.
     */
    private Object convertArray(Object val) {
        if (val instanceof Array sqlArr) {
            try {
                Object[] raw = (Object[]) sqlArr.getArray();
                List<Object> list = new ArrayList<>(raw.length);
                for (Object o : raw) list.add(o);
                return list;
            } catch (SQLException e) {
                return val; // fall back to original if conversion fails
            }
        }
        return val;
    }
}
