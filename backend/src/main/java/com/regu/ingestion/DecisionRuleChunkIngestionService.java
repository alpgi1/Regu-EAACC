package com.regu.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Ingests FLI EU AI Act Compliance Checker decision rules into the
 * {@code decision_rule_chunks} table.
 *
 * <p>Reads all JSON files from
 * {@code corpus/decision_rules/fli_compliance_checker/} (excluding
 * {@code manifest.json}), generates {@code voyage-3-large} embeddings in one
 * batch, and inserts rows. Already-existing {@code rule_id} values are skipped
 * so the operation is safe to re-run.
 *
 * <p>Only active under the {@code ingest} Spring profile.
 */
@Service
@Profile("ingest")
public class DecisionRuleChunkIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DecisionRuleChunkIngestionService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final NamedParameterJdbcTemplate jdbc;
    private final VoyageEmbeddingClient      voyageClient;
    private final Path                       rulesDir;

    public DecisionRuleChunkIngestionService(
            NamedParameterJdbcTemplate jdbc,
            VoyageEmbeddingClient voyageClient,
            @Value("${regu.ingestion.corpus-root}") String corpusRoot) {
        this.jdbc         = jdbc;
        this.voyageClient = voyageClient;
        this.rulesDir     = Path.of(corpusRoot).resolve("decision_rules/fli_compliance_checker");
    }

    /**
     * Ingests all decision rule chunks. Skips any {@code rule_id} that already
     * exists in the database (idempotent).
     *
     * @return ingest stats with counts of inserted and skipped rows
     * @throws IOException if any corpus file cannot be read
     */
    @Transactional
    public IngestStats ingestAll() throws IOException {
        List<Path> files = Files.list(rulesDir)
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .filter(p -> !p.getFileName().toString().equals("manifest.json"))
                .sorted()
                .collect(Collectors.toList());

        log.info("Found {} decision rule JSON files in {}", files.size(), rulesDir);

        // Parse all files up front
        List<JsonNode> nodes   = new ArrayList<>(files.size());
        List<String>   ruleIds = new ArrayList<>(files.size());

        for (Path file : files) {
            JsonNode node = OBJECT_MAPPER.readTree(file.toFile());
            nodes.add(node);
            ruleIds.add(node.get("rule_id").asText());
        }

        // Identify which rule_ids are already in the DB
        Set<String> existing = fetchExistingRuleIds(ruleIds);
        log.info("{} rule_id(s) already exist — will skip", existing.size());

        // Separate new entries
        List<Integer> newIdx      = new ArrayList<>();
        List<String>  newContents = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            if (!existing.contains(ruleIds.get(i))) {
                newIdx.add(i);
                newContents.add(nodes.get(i).get("content").asText());
            }
        }

        if (newContents.isEmpty()) {
            log.info("All {} decision rule chunks already ingested — nothing to do", files.size());
            return new IngestStats(0, files.size());
        }

        // Batch embed (one API call for ≤128 texts)
        log.info("Generating embeddings for {} new decision rule chunks", newContents.size());
        List<float[]> embeddings = voyageClient.embedBatch(newContents);

        // Insert
        int inserted = 0;
        for (int i = 0; i < newIdx.size(); i++) {
            JsonNode node = nodes.get(newIdx.get(i));
            Long     id   = insertChunk(node, embeddings.get(i));
            log.debug("Inserted decision_rule_chunk rule_id='{}' db_id={}", node.get("rule_id").asText(), id);
            inserted++;
        }

        int skipped = files.size() - inserted;
        log.info("Decision rule ingestion complete: {} inserted, {} skipped", inserted, skipped);
        return new IngestStats(inserted, skipped);
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private Set<String> fetchExistingRuleIds(List<String> ruleIds) {
        if (ruleIds.isEmpty()) return Set.of();
        String sql    = "SELECT rule_id FROM decision_rule_chunks WHERE rule_id IN (:ruleIds)";
        List<String> found = jdbc.queryForList(sql, Map.of("ruleIds", ruleIds), String.class);
        return new HashSet<>(found);
    }

    private Long insertChunk(JsonNode node, float[] embedding) {
        String sql = """
                INSERT INTO decision_rule_chunks
                    (content, embedding, rule_id, chunk_type, section, section_path,
                     source_document, source_url, publisher, topic,
                     related_articles, related_annex_points,
                     publish_date, version, status)
                VALUES (:content, :embedding::vector, :ruleId, :chunkType, :section, :sectionPath,
                        :sourceDocument, :sourceUrl, :publisher, :topic,
                        :relatedArticles::integer[], :relatedAnnexPoints::text[],
                        :publishDate, :version, :status)
                RETURNING id
                """;

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("content",          node.get("content").asText())
                .addValue("embedding",         formatVector(embedding))
                .addValue("ruleId",            node.get("rule_id").asText())
                .addValue("chunkType",         node.get("chunk_type").asText())
                .addValue("section",           node.get("section").asText())
                .addValue("sectionPath",       textOrNull(node, "section_path"))
                .addValue("sourceDocument",    node.get("source_document").asText())
                .addValue("sourceUrl",         textOrNull(node, "source_url"))
                .addValue("publisher",         node.get("publisher").asText())
                .addValue("topic",             textOrNull(node, "topic"))
                .addValue("relatedArticles",   formatIntArray(node.get("related_articles")))
                .addValue("relatedAnnexPoints", formatStringArray(node.get("related_annex_points")))
                .addValue("publishDate",       LocalDate.parse(node.get("publish_date").asText()))
                .addValue("version",           node.get("version").asText())
                .addValue("status",            node.get("status").asText());

        return jdbc.queryForObject(sql, p, Long.class);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode val = node.get(field);
        return (val == null || val.isNull()) ? null : val.asText();
    }

    // ── pgvector / array formatting ──────────────────────────────────────

    /**
     * Formats a float[] as a pgvector text literal: {@code [f0,f1,...,fn-1]}.
     */
    static String formatVector(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    /**
     * Formats a JSON integer-array node as a PostgreSQL array literal:
     * {@code {1,2,3}} or {@code {}} for empty/null.
     */
    static String formatIntArray(JsonNode arr) {
        if (arr == null || arr.isNull() || arr.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < arr.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(arr.get(i).asInt());
        }
        return sb.append('}').toString();
    }

    /**
     * Formats a JSON string-array node as a PostgreSQL text-array literal:
     * {@code {"a","b"}} or {@code {}} for empty/null.
     * Double-quotes and backslashes inside values are escaped.
     */
    static String formatStringArray(JsonNode arr) {
        if (arr == null || arr.isNull() || arr.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < arr.size(); i++) {
            if (i > 0) sb.append(',');
            String val = arr.get(i).asText()
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"");
            sb.append('"').append(val).append('"');
        }
        return sb.append('}').toString();
    }

    // ── Result record ────────────────────────────────────────────────────

    public record IngestStats(int inserted, int skipped) {}
}
