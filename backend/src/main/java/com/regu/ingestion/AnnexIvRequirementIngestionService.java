package com.regu.ingestion;

import com.fasterxml.jackson.core.type.TypeReference;
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

/**
 * Ingests Annex IV technical-documentation requirements into the
 * {@code annex_iv_requirements} table.
 *
 * <p>Reads {@code corpus/annex_iv/requirements.json} — a flat JSON array
 * where each element describes one atomic information requirement (e.g.
 * "Name of the provider", "Cybersecurity measures"). The section number is
 * parsed from the leading digit of the {@code requirement_id} field.
 *
 * <p>No embeddings are generated. This is structured reference data accessed
 * via {@code requirement_id} lookups and section-scoped queries, not semantic
 * similarity search.
 *
 * <p>Idempotent: rows whose {@code requirement_id} already exists in the
 * database are skipped without error, making re-runs safe.
 *
 * <p>Only active under the {@code ingest} Spring profile.
 */
@Service
@Profile("ingest")
public class AnnexIvRequirementIngestionService {

    private static final Logger log = LoggerFactory.getLogger(AnnexIvRequirementIngestionService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final TypeReference<List<Map<String, Object>>> LIST_TYPE =
            new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final Path                       requirementsFile;

    public AnnexIvRequirementIngestionService(
            NamedParameterJdbcTemplate jdbc,
            @Value("${regu.ingestion.corpus-root}") String corpusRoot) {
        this.jdbc             = jdbc;
        this.requirementsFile = Path.of(corpusRoot).resolve("annex_iv/requirements.json");
    }

    /**
     * Ingests all Annex IV requirements from the corpus JSON file.
     * Skips any {@code requirement_id} that is already present in the database.
     *
     * @return ingestion stats (inserted + skipped counts)
     * @throws IOException if the requirements file cannot be read
     */
    @Transactional
    public IngestStats ingestAll() throws IOException {
        if (!Files.exists(requirementsFile)) {
            log.warn("Annex IV requirements file not found at {} — skipping step", requirementsFile);
            return new IngestStats(0, 0);
        }
        if (Files.size(requirementsFile) == 0) {
            log.warn("Annex IV requirements file is empty — skipping step");
            return new IngestStats(0, 0);
        }

        List<Map<String, Object>> rows = OBJECT_MAPPER.readValue(requirementsFile.toFile(), LIST_TYPE);
        log.info("Parsed {} Annex IV requirements from {}", rows.size(), requirementsFile);

        if (rows.isEmpty()) {
            return new IngestStats(0, 0);
        }

        // Collect all requirement_ids to check which already exist
        List<String> allIds = rows.stream()
                .map(r -> (String) r.get("requirement_id"))
                .filter(Objects::nonNull)
                .toList();

        Set<String> existing = fetchExistingIds(allIds);
        log.info("{} requirement_id(s) already exist in DB — will skip", existing.size());

        int inserted = 0;
        int skipped  = 0;
        int order    = 0;

        for (Map<String, Object> row : rows) {
            String reqId = (String) row.get("requirement_id");
            if (reqId == null || reqId.isBlank()) {
                log.warn("Skipping row with missing requirement_id: {}", row);
                skipped++;
                continue;
            }
            if (existing.contains(reqId)) {
                log.debug("Skipping existing requirement_id='{}'", reqId);
                skipped++;
                continue;
            }

            short sectionNumber = parseSectionNumber(reqId);
            if (sectionNumber < 1 || sectionNumber > 9) {
                log.warn("Cannot parse section number from requirement_id='{}' — skipping", reqId);
                skipped++;
                continue;
            }

            insertRequirement(row, reqId, sectionNumber, order++);
            log.debug("Inserted annex_iv_requirement requirement_id='{}'", reqId);
            inserted++;
        }

        log.info("Annex IV requirements ingestion complete: {} inserted, {} skipped",
                inserted, skipped);
        return new IngestStats(inserted, skipped);
    }

    // ── Section number parsing ───────────────────────────────────────────

    /**
     * Parses the section number from the leading digit of a requirement_id.
     * For {@code "1_a_1"} returns {@code 1}; for {@code "9_2"} returns {@code 9}.
     *
     * @return the section number, or {@code -1} if the id is malformed
     */
    static short parseSectionNumber(String requirementId) {
        if (requirementId == null || requirementId.isBlank()) return -1;
        int sep = requirementId.indexOf('_');
        String digit = sep > 0 ? requirementId.substring(0, sep) : requirementId;
        try {
            return Short.parseShort(digit);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private Set<String> fetchExistingIds(List<String> ids) {
        if (ids.isEmpty()) return Set.of();
        String sql = "SELECT requirement_id FROM annex_iv_requirements " +
                     "WHERE requirement_id IN (:ids)";
        List<String> found = jdbc.queryForList(sql, Map.of("ids", ids), String.class);
        return new HashSet<>(found);
    }

    private void insertRequirement(Map<String, Object> row, String reqId,
                                   short sectionNumber, int displayOrder) {
        String sql = """
                INSERT INTO annex_iv_requirements
                    (requirement_id, section_number, category, entity_name,
                     extraction_target, fallback_prompt, display_order)
                VALUES
                    (:requirementId, :sectionNumber, :category, :entityName,
                     :extractionTarget, :fallbackPrompt, :displayOrder)
                """;

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("requirementId",    reqId)
                .addValue("sectionNumber",    sectionNumber)
                .addValue("category",         (String) row.get("category"))
                .addValue("entityName",       (String) row.get("entity_name"))
                .addValue("extractionTarget", (String) row.get("extraction_target"))
                .addValue("fallbackPrompt",   (String) row.get("fallback_prompt"))
                .addValue("displayOrder",     displayOrder);

        jdbc.update(sql, p);
    }

    // ── Result record ────────────────────────────────────────────────────

    /**
     * @param inserted number of new rows successfully inserted
     * @param skipped  number of rows skipped (already existed or malformed)
     */
    public record IngestStats(int inserted, int skipped) {}
}
