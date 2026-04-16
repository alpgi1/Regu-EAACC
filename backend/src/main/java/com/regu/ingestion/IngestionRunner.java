package com.regu.ingestion;

import com.regu.ingestion.legal.LegalChunkIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Command-line runner that orchestrates the full corpus ingestion pipeline.
 *
 * <p>Activated only when the {@code ingest} Spring profile is present, so it
 * never fires during normal application startup or test runs. Start ingestion
 * with:
 * <pre>
 *   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,ingest
 * </pre>
 * or equivalently:
 * <pre>
 *   SPRING_PROFILES_ACTIVE=dev,ingest ./mvnw spring-boot:run
 * </pre>
 *
 * <p>Sequence (all steps run in order; failure in any step logs the error
 * and halts — subsequent steps do not run):
 * <ol>
 *   <li>Ingest {@code legal_chunks} (EU AI Act batch JSON, with embeddings)</li>
 *   <li>Ingest {@code decision_rule_chunks} (40 FLI JSON files, with embeddings)</li>
 *   <li>Ingest {@code interview_questions} (15 Stage 1 JSON files, no embeddings)</li>
 *   <li>FK back-fill: {@code interview_questions.linked_rule_chunk}</li>
 *   <li>Verify {@code annex_iv_sections} — 9 rows must exist (seeded in V10)</li>
 *   <li>Ingest {@code annex_iv_requirements} (corpus/annex_iv/requirements.json)</li>
 *   <li>Ingest {@code use_case_chunks} (corpus/use_cases/scenarios.json, with embeddings)</li>
 * </ol>
 *
 * <p>Each service is idempotent — re-running skips already-ingested rows.
 */
@Component
@Profile("ingest")
public class IngestionRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestionRunner.class);

    private final LegalChunkIngestionService            legalService;
    private final DecisionRuleChunkIngestionService     decisionRuleService;
    private final InterviewQuestionIngestionService     questionService;
    private final AnnexIvRequirementIngestionService    annexIvService;
    private final UseCaseIngestionService               useCaseService;
    private final NamedParameterJdbcTemplate            jdbc;

    public IngestionRunner(
            LegalChunkIngestionService legalService,
            DecisionRuleChunkIngestionService decisionRuleService,
            InterviewQuestionIngestionService questionService,
            AnnexIvRequirementIngestionService annexIvService,
            UseCaseIngestionService useCaseService,
            NamedParameterJdbcTemplate jdbc) {
        this.legalService       = legalService;
        this.decisionRuleService = decisionRuleService;
        this.questionService    = questionService;
        this.annexIvService     = annexIvService;
        this.useCaseService     = useCaseService;
        this.jdbc               = jdbc;
    }

    @Override
    public void run(String... args) {
        log.info("=== REGU corpus ingestion starting ===");

        // ── Step 1: legal_chunks ─────────────────────────────────────────
        LegalChunkIngestionService.IngestionResult legalResult;
        try {
            legalResult = legalService.ingestAll();
        } catch (Exception e) {
            log.error("INGESTION HALTED — legal_chunks ingestion failed: {}", e.getMessage(), e);
            return;
        }
        if (legalResult.errors() > 0) {
            log.warn("  legal_chunks ingestion had {} error(s):", legalResult.errors());
            legalResult.errorMessages().forEach(msg -> log.warn("    - {}", msg));
        }

        // ── Step 2: decision_rule_chunks ─────────────────────────────────
        DecisionRuleChunkIngestionService.IngestStats drStats;
        try {
            drStats = decisionRuleService.ingestAll();
        } catch (Exception e) {
            log.error("INGESTION HALTED — decision_rule_chunks ingestion failed: {}", e.getMessage(), e);
            return;
        }

        // ── Step 3: interview_questions ──────────────────────────────────
        InterviewQuestionIngestionService.IngestStats iqStats;
        try {
            iqStats = questionService.ingestAll();
        } catch (Exception e) {
            log.error("INGESTION HALTED — interview_questions ingestion failed: {}", e.getMessage(), e);
            return;
        }

        // ── Step 4: FK back-fill ─────────────────────────────────────────
        int backFills;
        try {
            backFills = questionService.backFillForeignKeys();
        } catch (Exception e) {
            log.error("INGESTION HALTED — FK back-fill failed: {}", e.getMessage(), e);
            return;
        }

        // ── Step 5: Verify annex_iv_sections (seeded by V10 migration) ───
        long sectionCount;
        try {
            sectionCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM annex_iv_sections",
                    Map.of(),
                    Long.class);
        } catch (Exception e) {
            log.error("INGESTION HALTED — annex_iv_sections count check failed: {}", e.getMessage(), e);
            return;
        }
        if (sectionCount != 9) {
            log.error("INGESTION HALTED — annex_iv_sections has {} rows, expected 9. " +
                      "Verify V10 migration applied correctly.", sectionCount);
            return;
        }
        log.info("  annex_iv_sections    : {} rows verified", sectionCount);

        // ── Step 6: annex_iv_requirements ───────────────────────────────
        AnnexIvRequirementIngestionService.IngestStats annexStats;
        try {
            annexStats = annexIvService.ingestAll();
        } catch (Exception e) {
            log.error("INGESTION HALTED — annex_iv_requirements ingestion failed: {}", e.getMessage(), e);
            return;
        }

        // ── Step 7: use_case_chunks ──────────────────────────────────────
        UseCaseIngestionService.IngestStats ucStats;
        try {
            ucStats = useCaseService.ingestAll();
        } catch (Exception e) {
            log.error("INGESTION HALTED — use_case_chunks ingestion failed: {}", e.getMessage(), e);
            return;
        }

        // ── Summary ──────────────────────────────────────────────────────
        log.info("=== REGU corpus ingestion complete ===");
        log.info("  legal_chunks         : {} inserted, {} skipped, {} errors",
                legalResult.inserted(), legalResult.skipped(), legalResult.errors());
        log.info("  decision_rule_chunks : {} inserted, {} skipped",
                drStats.inserted(), drStats.skipped());
        log.info("  interview_questions  : {} inserted, {} skipped",
                iqStats.inserted(), iqStats.skipped());
        log.info("  FK back-fills        : {} completed", backFills);
        log.info("  annex_iv_sections    : {} rows in DB", sectionCount);
        log.info("  annex_iv_requirements: {} inserted, {} skipped",
                annexStats.inserted(), annexStats.skipped());
        log.info("  use_case_chunks      : {} inserted, {} skipped",
                ucStats.inserted(), ucStats.skipped());
    }
}
