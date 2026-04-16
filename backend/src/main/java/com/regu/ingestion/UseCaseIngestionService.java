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
import java.util.stream.Collectors;

/**
 * Ingests curated use case scenarios into the {@code use_case_chunks} table.
 *
 * <p>Reads {@code corpus/use_cases/scenarios.json} — a JSON array where
 * each element describes one industry scenario with its EU AI Act classification,
 * legal basis, actor role, and a compliance roadmap.
 *
 * <p>The {@code searchable_context} field is embedded via Voyage AI
 * {@code voyage-3-large} (1024 dims) and stored in the {@code embedding}
 * column for semantic similarity search.
 *
 * <p>Field mapping from JSON → {@code use_case_chunks}:
 * <ul>
 *   <li>{@code use_case_id}          → {@code use_case_id} and {@code scenario_name} (idempotency key)</li>
 *   <li>{@code searchable_context}   → {@code content} (embedded) and {@code content_with_context}</li>
 *   <li>{@code metadata.sector}      → {@code scenario_domain} and {@code sector}</li>
 *   <li>{@code metadata.ai_act_classification} → {@code risk_category} (normalised)</li>
 *   <li>{@code metadata.legal_basis} → {@code legal_basis} and {@code primary_legal_basis}</li>
 *   <li>{@code metadata.actor_role}  → {@code actor_role}</li>
 *   <li>{@code metadata.sme_privilege_applicable} → {@code sme_privilege}</li>
 *   <li>{@code compliance_roadmap}   → {@code compliance_roadmap} (serialised JSON array)</li>
 * </ul>
 *
 * <p>Idempotent: skips any row whose {@code scenario_name} (= use_case_id)
 * already exists in the database.
 *
 * <p>Only active under the {@code ingest} Spring profile.
 */
@Service
@Profile("ingest")
public class UseCaseIngestionService {

    private static final Logger log = LoggerFactory.getLogger(UseCaseIngestionService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private static final TypeReference<List<Map<String, Object>>> LIST_TYPE =
            new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final VoyageEmbeddingClient      voyageClient;
    private final Path                       scenariosFile;

    public UseCaseIngestionService(
            NamedParameterJdbcTemplate jdbc,
            VoyageEmbeddingClient voyageClient,
            @Value("${regu.ingestion.corpus-root}") String corpusRoot) {
        this.jdbc          = jdbc;
        this.voyageClient  = voyageClient;
        this.scenariosFile = Path.of(corpusRoot).resolve("use_cases/scenarios.json");
    }

    /**
     * Ingests all use case scenarios. Skips any scenario whose
     * {@code use_case_id} already exists as {@code scenario_name} in the DB.
     *
     * @return ingestion stats (inserted + skipped counts)
     * @throws IOException if the scenarios file cannot be read
     */
    @Transactional
    public IngestStats ingestAll() throws IOException {
        if (!Files.exists(scenariosFile)) {
            log.warn("Use case scenarios file not found at {} — skipping step", scenariosFile);
            return new IngestStats(0, 0);
        }
        if (Files.size(scenariosFile) == 0) {
            log.warn("Use case scenarios file is empty — skipping step");
            return new IngestStats(0, 0);
        }

        List<Map<String, Object>> scenarios = OBJECT_MAPPER.readValue(scenariosFile.toFile(), LIST_TYPE);
        log.info("Parsed {} use case scenarios from {}", scenarios.size(), scenariosFile);

        if (scenarios.isEmpty()) {
            return new IngestStats(0, 0);
        }

        // ── 1. Identify new vs. existing ─────────────────────────────────
        List<String> allIds = scenarios.stream()
                .map(s -> (String) s.get("use_case_id"))
                .filter(Objects::nonNull)
                .toList();

        Set<String> existing = fetchExistingIds(allIds);
        log.info("{} use_case_id(s) already exist — will skip", existing.size());

        List<Map<String, Object>> toInsert = scenarios.stream()
                .filter(s -> {
                    String id = (String) s.get("use_case_id");
                    return id != null && !existing.contains(id);
                })
                .collect(Collectors.toList());

        int skipped = scenarios.size() - toInsert.size();

        if (toInsert.isEmpty()) {
            log.info("All {} use case scenarios already ingested — nothing to do", scenarios.size());
            return new IngestStats(0, skipped);
        }

        // ── 2. Extract content for embedding ─────────────────────────────
        List<String> contents = toInsert.stream()
                .map(s -> (String) s.get("searchable_context"))
                .collect(Collectors.toList());

        // ── 3. Batch embed ────────────────────────────────────────────────
        log.info("Generating embeddings for {} new use case scenarios", toInsert.size());
        List<float[]> embeddings = voyageClient.embedBatch(contents);

        // ── 4. Insert ─────────────────────────────────────────────────────
        int inserted = 0;
        for (int i = 0; i < toInsert.size(); i++) {
            Map<String, Object> scenario = toInsert.get(i);
            String useCaseId = (String) scenario.get("use_case_id");
            try {
                insertScenario(scenario, contents.get(i), embeddings.get(i));
                log.debug("Inserted use_case_chunk use_case_id='{}'", useCaseId);
                inserted++;
            } catch (Exception e) {
                log.warn("Insert failed for use_case_id='{}': {}", useCaseId, e.getMessage());
            }
        }

        log.info("Use case ingestion complete: {} inserted, {} skipped", inserted, skipped);
        return new IngestStats(inserted, skipped);
    }

    // ── Classification normalisation ─────────────────────────────────────

    /**
     * Maps the human-readable {@code ai_act_classification} value from the
     * corpus metadata to the DB enum values accepted by the
     * {@code chk_usecase_risk_category} CHECK constraint
     * ({@code unacceptable | high | limited | minimal}).
     */
    static String normaliseRiskCategory(String classification) {
        if (classification == null) return "minimal";
        return switch (classification.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "unacceptable risk", "unacceptable" -> "unacceptable";
            case "high-risk", "high risk", "high"    -> "high";
            case "limited risk", "limited",
                 "transparency risk"                 -> "limited";
            // catch-alls: minimal/no risk, exempt/r&d, and any unknown label
            default                                  -> "minimal";
        };
    }

    /**
     * Attempts to extract an article number from a legal_basis string such as
     * {@code "Article 50"} or {@code "Article 57 / Article 58"}.
     * Returns an empty array if no article number can be found.
     */
    static String extractMappedArticles(String legalBasis) {
        if (legalBasis == null || legalBasis.isBlank()) return "{}";
        List<Integer> nums = new ArrayList<>();
        // Match "Article NN" or "Article NN(N)"
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("Article\\s+(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                                       .matcher(legalBasis);
        while (m.find()) {
            try { nums.add(Integer.parseInt(m.group(1))); } catch (NumberFormatException ignored) {}
        }
        // "Annex III" section 6 → Article 6 (rough mapping for the 3 affected use cases)
        // Kept minimal — full article mapping belongs to a later phase.
        if (nums.isEmpty()) return "{}";
        return "{" + nums.stream().map(Object::toString).collect(Collectors.joining(",")) + "}";
    }

    /**
     * Extracts annex references (e.g. {@code {"III"}}) from a legal_basis string
     * like {@code "Annex III"} or {@code "Annex III / Article 6(1)"}.
     */
    static String extractMappedAnnexPoints(String legalBasis) {
        if (legalBasis == null || legalBasis.isBlank()) return "{}";
        List<String> annexes = new ArrayList<>();
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("Annex\\s+([IVXLCDM]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                                       .matcher(legalBasis);
        while (m.find()) {
            annexes.add(m.group(1).toUpperCase(java.util.Locale.ROOT));
        }
        if (annexes.isEmpty()) return "{}";
        return "{" + annexes.stream()
                .map(a -> "\"" + a.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",")) + "}";
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private Set<String> fetchExistingIds(List<String> ids) {
        if (ids.isEmpty()) return Set.of();
        String sql = "SELECT scenario_name FROM use_case_chunks WHERE scenario_name IN (:ids)";
        List<String> found = jdbc.queryForList(sql, Map.of("ids", ids), String.class);
        return new HashSet<>(found);
    }

    @SuppressWarnings("unchecked")
    private void insertScenario(Map<String, Object> scenario,
                                String content, float[] embedding) throws Exception {
        Map<String, Object> metadata = (Map<String, Object>) scenario.getOrDefault("metadata", Map.of());

        String useCaseId       = (String) scenario.get("use_case_id");
        String sector          = (String) metadata.getOrDefault("sector", "");
        String classification  = (String) metadata.getOrDefault("ai_act_classification", "");
        String legalBasis      = (String) metadata.getOrDefault("legal_basis", "");
        String actorRole       = (String) metadata.getOrDefault("actor_role", "");
        Object smePriv         = metadata.getOrDefault("sme_privilege_applicable", false);
        boolean smePrivilege   = smePriv instanceof Boolean b ? b : Boolean.parseBoolean(smePriv.toString());

        String riskCategory    = normaliseRiskCategory(classification);
        String mappedArticles  = extractMappedArticles(legalBasis);
        String mappedAnnex     = extractMappedAnnexPoints(legalBasis);

        // Serialise the compliance_roadmap array to a JSON string
        Object roadmapRaw = scenario.get("compliance_roadmap");
        String roadmapJson = roadmapRaw != null
                ? OBJECT_MAPPER.writeValueAsString(roadmapRaw)
                : null;

        String sql = """
                INSERT INTO use_case_chunks
                    (content, embedding,
                     scenario_name, scenario_domain,
                     risk_category, primary_legal_basis,
                     mapped_articles, mapped_annex_points,
                     source,
                     use_case_id, sector, actor_role, sme_privilege,
                     compliance_roadmap, legal_basis)
                VALUES
                    (:content, :embedding::vector,
                     :scenarioName, :scenarioDomain,
                     :riskCategory, :primaryLegalBasis,
                     :mappedArticles::integer[], :mappedAnnexPoints::text[],
                     :source,
                     :useCaseId, :sector, :actorRole, :smePrivilege,
                     :complianceRoadmap, :legalBasis)
                """;

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("content",           content)
                .addValue("embedding",         DecisionRuleChunkIngestionService.formatVector(embedding))
                .addValue("scenarioName",      useCaseId)
                .addValue("scenarioDomain",    sector)
                .addValue("riskCategory",      riskCategory)
                .addValue("primaryLegalBasis", legalBasis)
                .addValue("mappedArticles",    mappedArticles)
                .addValue("mappedAnnexPoints", mappedAnnex)
                .addValue("source",            "curated")
                .addValue("useCaseId",         useCaseId)
                .addValue("sector",            sector)
                .addValue("actorRole",         actorRole)
                .addValue("smePrivilege",      smePrivilege)
                .addValue("complianceRoadmap", roadmapJson)
                .addValue("legalBasis",        legalBasis);

        jdbc.update(sql, p);
    }

    // ── Result record ────────────────────────────────────────────────────

    /**
     * @param inserted number of new rows successfully inserted
     * @param skipped  number of rows skipped (already existed or file missing)
     */
    public record IngestStats(int inserted, int skipped) {}
}
