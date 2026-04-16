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
 * Retrieves chunks from {@code decision_rule_chunks} using vector similarity
 * with optional metadata filters.
 *
 * <p>Decision rule chunks encode the FLI EU AI Act Compliance Checker logic —
 * questions, outcomes, obligations, and exceptions. Vector search identifies
 * semantically relevant rules; metadata filters narrow results to specific
 * rule types ({@code chunk_type}) or topic sections.
 *
 * <p>Supported filters:
 * <ul>
 *   <li>{@code chunk_type} — one of {@code question | outcome | obligation | exception}</li>
 *   <li>{@code section} — rule section identifier</li>
 *   <li>{@code related_articles} — integer list; uses {@code &&} array overlap</li>
 * </ul>
 */
@Service
public class DecisionRuleRetriever implements ChunkRetriever {

    private static final Logger log = LoggerFactory.getLogger(DecisionRuleRetriever.class);
    private static final String TABLE = "decision_rule_chunks";

    private final NamedParameterJdbcTemplate jdbc;
    private final QueryEmbeddingClient       embedder;

    public DecisionRuleRetriever(NamedParameterJdbcTemplate jdbc, QueryEmbeddingClient embedder) {
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
                SELECT id, content, rule_id, chunk_type,
                       section, section_path, topic,
                       related_articles, related_annex_points,
                       source_document, publisher,
                       1 - (embedding <=> :queryVector::vector) AS score
                FROM decision_rule_chunks
                WHERE 1=1
                """);

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("queryVector", vec)
                .addValue("limit", query.maxResults());

        if (filters.containsKey("chunk_type")) {
            sql.append(" AND chunk_type = :chunkType");
            p.addValue("chunkType", filters.get("chunk_type"));
        }
        if (filters.containsKey("section")) {
            sql.append(" AND section = :section");
            p.addValue("section", filters.get("section"));
        }
        if (filters.containsKey("related_articles")) {
            sql.append(" AND related_articles && :articles::integer[]");
            p.addValue("articles", buildIntArrayLiteral(filters.get("related_articles")));
        }

        sql.append(" ORDER BY embedding <=> :queryVector::vector LIMIT :limit");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), p);
        List<RetrievedChunk> chunks = rows.stream().map(this::toChunk).toList();

        long ms = System.currentTimeMillis() - t0;
        String strategy = filters.isEmpty() ? "vector" : "vector+metadata";
        log.info("Retrieved {} chunks from {} via {} in {}ms", chunks.size(), TABLE, strategy, ms);
        return new RetrievalResult(chunks, query.queryText(), ms, strategy);
    }

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
        putIfPresent(metadata, "rule_id",               row, "rule_id");
        putIfPresent(metadata, "chunk_type",             row, "chunk_type");
        putIfPresent(metadata, "section",                row, "section");
        putIfPresent(metadata, "section_path",           row, "section_path");
        putIfPresent(metadata, "topic",                  row, "topic");
        putIfPresent(metadata, "related_articles",       row, "related_articles");
        putIfPresent(metadata, "related_annex_points",   row, "related_annex_points");
        putIfPresent(metadata, "source_document",        row, "source_document");
        putIfPresent(metadata, "publisher",              row, "publisher");

        double score = row.get("score") instanceof Number n ? n.doubleValue() : 0.0;
        return new RetrievedChunk(
                ((Number) row.get("id")).longValue(),
                TABLE,
                (String) row.get("content"),
                null, // decision_rule_chunks has no content_with_context
                score,
                metadata
        );
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
