package com.regu.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

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
 *   <li>Ingest {@code decision_rule_chunks} (40 FLI JSON files, with embeddings)</li>
 *   <li>Ingest {@code interview_questions} (15 Stage 1 JSON files, no embeddings)</li>
 *   <li>FK back-fill: set {@code interview_questions.linked_rule_chunk} from the
 *       {@code linked_rule_chunk_ref} field in each question's JSON file</li>
 * </ol>
 *
 * <p>Each service is idempotent — re-running skips already-ingested rows without
 * error.
 */
@Component
@Profile("ingest")
public class IngestionRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestionRunner.class);

    private final DecisionRuleChunkIngestionService  decisionRuleService;
    private final InterviewQuestionIngestionService  questionService;

    public IngestionRunner(
            DecisionRuleChunkIngestionService decisionRuleService,
            InterviewQuestionIngestionService questionService) {
        this.decisionRuleService = decisionRuleService;
        this.questionService     = questionService;
    }

    @Override
    public void run(String... args) {
        log.info("=== REGU corpus ingestion starting ===");

        // ── Step 1: decision_rule_chunks ─────────────────────────────────
        DecisionRuleChunkIngestionService.IngestStats drStats;
        try {
            drStats = decisionRuleService.ingestAll();
        } catch (Exception e) {
            log.error("INGESTION HALTED — decision_rule_chunks ingestion failed: {}", e.getMessage(), e);
            return;
        }

        // ── Step 2: interview_questions ──────────────────────────────────
        InterviewQuestionIngestionService.IngestStats iqStats;
        try {
            iqStats = questionService.ingestAll();
        } catch (Exception e) {
            log.error("INGESTION HALTED — interview_questions ingestion failed: {}", e.getMessage(), e);
            return;
        }

        // ── Step 3: FK back-fill ─────────────────────────────────────────
        int backFills;
        try {
            backFills = questionService.backFillForeignKeys();
        } catch (Exception e) {
            log.error("INGESTION HALTED — FK back-fill failed: {}", e.getMessage(), e);
            return;
        }

        // ── Summary ──────────────────────────────────────────────────────
        log.info("=== REGU corpus ingestion complete ===");
        log.info("  decision_rule_chunks : {} inserted, {} skipped",
                drStats.inserted(), drStats.skipped());
        log.info("  interview_questions  : {} inserted, {} skipped",
                iqStats.inserted(), iqStats.skipped());
        log.info("  FK back-fills        : {} completed", backFills);
    }
}
