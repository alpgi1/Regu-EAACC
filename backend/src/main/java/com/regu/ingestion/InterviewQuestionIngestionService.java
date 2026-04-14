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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Ingests Stage 1 interview questions into the {@code interview_questions}
 * table, then back-fills the {@code linked_rule_chunk} FK from
 * {@code decision_rule_chunks}.
 *
 * <p><strong>Ingestion</strong>: reads the 15 JSON files under
 * {@code corpus/interview_questions/stage1/}, inserts each question with
 * {@code linked_rule_chunk = NULL}, skipping any {@code question_key} that
 * already exists (idempotent).
 *
 * <p><strong>FK back-fill</strong>: re-reads the JSON files to obtain each
 * question's {@code linked_rule_chunk_ref}, queries
 * {@code decision_rule_chunks} for the corresponding BIGINT {@code id}, then
 * {@code UPDATE}s each row in a single transaction. Logs every successful
 * update.
 *
 * <p>No embedding is generated — {@code interview_questions} has no vector
 * column.
 *
 * <p>Only active under the {@code ingest} Spring profile.
 */
@Service
@Profile("ingest")
public class InterviewQuestionIngestionService {

    private static final Logger log = LoggerFactory.getLogger(InterviewQuestionIngestionService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper               objectMapper;
    private final Path                       questionsDir;

    public InterviewQuestionIngestionService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper,
            @Value("${regu.ingestion.corpus-root}") String corpusRoot) {
        this.jdbc         = jdbc;
        this.objectMapper = objectMapper;
        this.questionsDir = Path.of(corpusRoot).resolve("interview_questions/stage1");
    }

    /**
     * Ingests all Stage 1 interview questions. Skips any {@code question_key}
     * that already exists (idempotent).
     *
     * @return ingest stats with counts of inserted and skipped rows
     * @throws IOException if any corpus file cannot be read
     */
    @Transactional
    public IngestStats ingestAll() throws IOException {
        List<Path> files = Files.list(questionsDir)
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .sorted()
                .collect(Collectors.toList());

        log.info("Found {} interview question JSON files in {}", files.size(), questionsDir);

        List<JsonNode> nodes = new ArrayList<>(files.size());
        List<String>   keys  = new ArrayList<>(files.size());

        for (Path file : files) {
            JsonNode node = objectMapper.readTree(file.toFile());
            nodes.add(node);
            keys.add(node.get("question_key").asText());
        }

        Set<String> existing = fetchExistingKeys(keys);
        log.info("{} question_key(s) already exist — will skip", existing.size());

        int inserted = 0;
        for (int i = 0; i < nodes.size(); i++) {
            if (!existing.contains(keys.get(i))) {
                insertQuestion(nodes.get(i));
                log.debug("Inserted interview_question question_key='{}'", keys.get(i));
                inserted++;
            }
        }

        int skipped = files.size() - inserted;
        log.info("Interview question ingestion complete: {} inserted, {} skipped", inserted, skipped);
        return new IngestStats(inserted, skipped);
    }

    /**
     * Back-fills {@code interview_questions.linked_rule_chunk} for every
     * question whose JSON file contains a non-null
     * {@code linked_rule_chunk_ref}.
     *
     * <p>Re-reads the corpus files to obtain the ref mapping, queries
     * {@code decision_rule_chunks} for the matching BIGINT {@code id}, then
     * issues one UPDATE per question — all within a single transaction.
     *
     * @return number of rows updated
     * @throws IOException if any corpus file cannot be read
     */
    @Transactional
    public int backFillForeignKeys() throws IOException {
        // 1. Build question_key → linked_rule_chunk_ref from the JSON files
        List<Path> files = Files.list(questionsDir)
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .sorted()
                .collect(Collectors.toList());

        Map<String, String> questionKeyToRef = new LinkedHashMap<>();
        for (Path file : files) {
            JsonNode node = objectMapper.readTree(file.toFile());
            String   key  = node.get("question_key").asText();
            JsonNode ref  = node.get("linked_rule_chunk_ref");
            if (ref != null && !ref.isNull()) {
                questionKeyToRef.put(key, ref.asText());
            }
        }

        if (questionKeyToRef.isEmpty()) {
            log.info("No linked_rule_chunk_ref values found — FK back-fill skipped");
            return 0;
        }

        // 2. Resolve rule_ids → BIGINT ids from decision_rule_chunks
        Collection<String> refs = questionKeyToRef.values();
        String lookupSql = "SELECT rule_id, id FROM decision_rule_chunks WHERE rule_id IN (:ruleIds)";
        Map<String, Long> ruleIdToChunkId = new HashMap<>();
        jdbc.query(lookupSql, Map.of("ruleIds", refs), rs -> {
            ruleIdToChunkId.put(rs.getString("rule_id"), rs.getLong("id"));
        });

        log.info("Resolved {} of {} rule_id refs to decision_rule_chunks ids",
                ruleIdToChunkId.size(), questionKeyToRef.size());

        // 3. Update interview_questions
        String updateSql = """
                UPDATE interview_questions
                   SET linked_rule_chunk = :chunkId
                 WHERE question_key = :questionKey
                """;

        int updated = 0;
        for (Map.Entry<String, String> entry : questionKeyToRef.entrySet()) {
            String questionKey = entry.getKey();
            String ruleRef     = entry.getValue();
            Long   chunkId     = ruleIdToChunkId.get(ruleRef);

            if (chunkId == null) {
                log.warn("FK back-fill: no decision_rule_chunk found for ref='{}' (question_key='{}') — skipping",
                        ruleRef, questionKey);
                continue;
            }

            int rows = jdbc.update(updateSql,
                    new MapSqlParameterSource()
                            .addValue("chunkId",     chunkId)
                            .addValue("questionKey", questionKey));

            if (rows == 1) {
                log.debug("FK back-fill: question_key='{}' → decision_rule_chunks.id={}", questionKey, chunkId);
                updated++;
            } else {
                log.warn("FK back-fill: UPDATE for question_key='{}' affected {} rows (expected 1)",
                        questionKey, rows);
            }
        }

        log.info("FK back-fill complete: {} interview_question(s) updated", updated);
        return updated;
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private Set<String> fetchExistingKeys(List<String> keys) {
        if (keys.isEmpty()) return Set.of();
        String       sql   = "SELECT question_key FROM interview_questions WHERE question_key IN (:keys)";
        List<String> found = jdbc.queryForList(sql, Map.of("keys", keys), String.class);
        return new HashSet<>(found);
    }

    private void insertQuestion(JsonNode node) throws com.fasterxml.jackson.core.JsonProcessingException {
        String sql = """
                INSERT INTO interview_questions
                    (question_key, stage, section, display_text, hint_text,
                     answers, preconditions, linked_rule_chunk,
                     linked_articles, is_terminal)
                VALUES (:questionKey, :stage, :section, :displayText, :hintText,
                        :answers::jsonb, :preconditions::jsonb, NULL,
                        :linkedArticles::integer[], :isTerminal)
                """;

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("questionKey",    node.get("question_key").asText())
                .addValue("stage",          node.get("stage").asInt())
                .addValue("section",        node.get("section").asText())
                .addValue("displayText",    node.get("display_text").asText())
                .addValue("hintText",       textOrNull(node, "hint_text"))
                .addValue("answers",        objectMapper.writeValueAsString(node.get("answers")))
                .addValue("preconditions",  jsonOrNull(node, "preconditions"))
                .addValue("linkedArticles", DecisionRuleChunkIngestionService.formatIntArray(node.get("linked_articles")))
                .addValue("isTerminal",     node.get("is_terminal").asBoolean());

        jdbc.update(sql, p);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode val = node.get(field);
        return (val == null || val.isNull()) ? null : val.asText();
    }

    private String jsonOrNull(JsonNode node, String field)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) return null;
        return objectMapper.writeValueAsString(val);
    }

    // ── Result record ────────────────────────────────────────────────────

    public record IngestStats(int inserted, int skipped) {}
}
