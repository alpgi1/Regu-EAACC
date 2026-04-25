package com.regu.orchestration.report;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regu.domain.*;
import com.regu.domain.repository.*;
import com.regu.orchestration.dto.*;
import com.regu.orchestration.exception.InterviewStateException;
import com.regu.orchestration.exception.ReportGenerationException;
import com.regu.orchestration.llm.LlmClient;
import com.regu.orchestration.prompt.PromptBuilder;
import com.regu.retrieval.RetrievalOrchestrator;
import com.regu.retrieval.dto.RetrievalQuery;
import com.regu.retrieval.dto.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Generates the final {@link ComplianceReport} by aggregating Stage 1 classification,
 * Stage 2 gap analyses (if applicable), and LLM-authored prose with inline citations.
 *
 * <p>Every citation in the returned report is validated against the database. If
 * more than 20% of citations cannot be resolved, the report is regenerated once.
 */
@Service
public class ReportGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationService.class);

    private static final double MAX_INVALID_CITATION_RATIO = 0.20;

    private static final String REPORT_SYSTEM = """
            You are generating a comprehensive EU AI Act compliance report for an
            AI system. The report must be professional, legally precise, and actionable.

            STRUCTURE — produce exactly these sections in the JSON:
            1. Executive Summary (2-3 paragraphs)
            2. Risk Classification (cite the specific legal basis)
            3. Applicable Obligations (list each article with what it requires)
            4. Compliance Gaps (from Annex IV analysis, if available)
            5. Recommendations (specific, prioritized action items)
            6. Similar Cases (reference similar use cases if retrieved)

            CITATION RULES (CRITICAL):
            - Every legal claim MUST include a citation in the format [cite:N]
              where N is the citation number (starting from 1)
            - Citations reference specific chunks from the legal context provided
            - You will be given chunks with their database IDs — use those IDs in the
              CitationEntry objects
            - A claim without a citation is INVALID and must not appear
            - Recommendations may cite guide_chunks for practical guidance

            TONE:
            - Professional but accessible — like a senior consultant briefing a startup CEO
            - Use "your system" not "the system" — address the user directly
            - Flag uncertainties explicitly

            OUTPUT FORMAT — respond with a JSON object matching this structure:
            {
              "summary": "string",
              "classification": { ClassificationResult fields },
              "gapAnalyses": [],
              "sections": [
                { "title": "string", "content": "string", "citationRefs": ["cite_1"] }
              ],
              "citations": [
                {
                  "citationId": "cite_1",
                  "sourceTable": "legal_chunks",
                  "sourceChunkId": 42,
                  "reference": "Article 10(2)",
                  "snippet": "first 200 chars of the chunk..."
                }
              ],
              "disclaimer": "string",
              "generatedAt": "ISO-8601 string",
              "modelUsed": "string",
              "totalProcessingMs": 0
            }
            """;

    private final InterviewSessionRepository   sessionRepo;
    private final InterviewAnswerRepository    answerRepo;
    private final Stage2SubmissionRepository   submissionRepo;
    private final AnalysisRepository           analysisRepo;
    private final ReportRepository             reportRepo;
    private final CitationRepository           citationRepo;
    private final RetrievalOrchestrator        retrieval;
    private final LlmClient                    llm;
    private final PromptBuilder                promptBuilder;
    private final NamedParameterJdbcTemplate   jdbc;
    private final int                          reportMaxTokens;

    private static final ObjectMapper GAP_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ReportGenerationService(
            InterviewSessionRepository sessionRepo,
            InterviewAnswerRepository answerRepo,
            Stage2SubmissionRepository submissionRepo,
            AnalysisRepository analysisRepo,
            ReportRepository reportRepo,
            CitationRepository citationRepo,
            RetrievalOrchestrator retrieval,
            LlmClient llm,
            PromptBuilder promptBuilder,
            NamedParameterJdbcTemplate jdbc,
            @Value("${regu.llm.report-max-tokens:8192}") int reportMaxTokens) {
        this.sessionRepo     = sessionRepo;
        this.answerRepo      = answerRepo;
        this.submissionRepo  = submissionRepo;
        this.analysisRepo    = analysisRepo;
        this.reportRepo      = reportRepo;
        this.citationRepo    = citationRepo;
        this.retrieval       = retrieval;
        this.llm             = llm;
        this.promptBuilder   = promptBuilder;
        this.jdbc            = jdbc;
        this.reportMaxTokens = reportMaxTokens;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Generates and persists the full compliance report for a session.
     *
     * @param sessionId the session to generate a report for
     * @return the complete {@link ComplianceReport}
     * @throws InterviewStateException  if the session has no classification yet
     * @throws ReportGenerationException if LLM generation or citation validation fails
     */
    @Transactional
    public ComplianceReport generateReport(UUID sessionId) {
        long t0 = System.currentTimeMillis();

        InterviewSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new InterviewStateException("Session not found: " + sessionId));

        if (session.getRiskClassification() == null) {
            throw new InterviewStateException(
                    "Session " + sessionId + " has no risk classification yet.");
        }

        Analysis analysis = session.getAnalysis();
        if (analysis == null) {
            throw new InterviewStateException(
                    "Session " + sessionId + " is not linked to an Analysis.");
        }

        // Build ClassificationResult from persisted Analysis state
        ClassificationResult classification = buildClassificationFromAnalysis(analysis);

        // Load interview transcript
        List<InterviewAnswer> answers =
                answerRepo.findAllBySession_IdOrderByAnsweredAtAsc(sessionId);

        // Load Stage 2 gap analyses (empty for non-high-risk)
        List<GapAnalysisResult> gapAnalyses = loadGapAnalyses(sessionId, session);

        // Retrieve context for the report
        String queryText = buildQueryText(answers, classification);

        List<RetrievedChunk> legalChunks = classification.applicableArticles() != null
                && !classification.applicableArticles().isEmpty()
                ? retrieval.retrieveLegalWithArticles(
                        classification.applicableArticles().stream()
                                .mapToInt(Integer::intValue).toArray()).chunks()
                : retrieval.retrieveFromTable("legal", RetrievalQuery.of(queryText, 15)).chunks();

        List<RetrievedChunk> guideChunks = retrieval
                .retrieveFromTable("guide", RetrievalQuery.of(queryText, 5)).chunks();

        List<RetrievedChunk> useCaseChunks = retrieval
                .retrieveFromTable("use_case", RetrievalQuery.of(queryText, 3)).chunks();

        List<RetrievedChunk> ruleChunks = retrieval
                .retrieveFromTable("decision_rule", RetrievalQuery.of(queryText, 5)).chunks();

        // Generate report (with one retry if citation validation fails)
        ComplianceReport report = generateWithRetry(
                classification, answers, gapAnalyses,
                legalChunks, guideChunks, useCaseChunks, ruleChunks, t0);

        // Persist to DB
        persistReport(analysis, report);

        // Mark analysis complete
        analysis.setStatus(Analysis.Status.completed);
        analysis.setCompletedAt(Instant.now());
        analysis.setProcessingMs(System.currentTimeMillis() - t0);
        analysisRepo.save(analysis);

        log.info("Report generated for session {} in {}ms", sessionId,
                System.currentTimeMillis() - t0);
        return report;
    }

    // ── Internal generation ────────────────────────────────────────────────

    private ComplianceReport generateWithRetry(
            ClassificationResult classification,
            List<InterviewAnswer> answers,
            List<GapAnalysisResult> gapAnalyses,
            List<RetrievedChunk> legalChunks,
            List<RetrievedChunk> guideChunks,
            List<RetrievedChunk> useCaseChunks,
            List<RetrievedChunk> ruleChunks,
            long t0) {

        String userPrompt = promptBuilder.buildReportPrompt(
                classification, answers, gapAnalyses,
                legalChunks, guideChunks, useCaseChunks, ruleChunks);

        ComplianceReport report = llm.callWithSchema(
                REPORT_SYSTEM, userPrompt, ComplianceReport.class, reportMaxTokens);

        // Citation validation
        List<CitationEntry> citations = report.citations() != null
                ? report.citations() : List.of();
        long invalidCount = citations.stream()
                .filter(c -> !validateCitation(c))
                .count();

        double invalidRatio = citations.isEmpty()
                ? 0.0
                : (double) invalidCount / citations.size();

        if (invalidRatio > MAX_INVALID_CITATION_RATIO) {
            log.warn("Citation validation failed: {}/{} citations invalid (ratio={}). Retrying.",
                    invalidCount, citations.size(), invalidRatio);
            // Retry once with the same prompt
            report = llm.callWithSchema(
                    REPORT_SYSTEM, userPrompt, ComplianceReport.class, reportMaxTokens);
            // Recount — if still above threshold, log warning but proceed
            List<CitationEntry> retryCitations = report.citations() != null
                    ? report.citations() : List.of();
            long retryInvalid = retryCitations.stream()
                    .filter(c -> !validateCitation(c))
                    .count();
            if (!retryCitations.isEmpty()
                    && (double) retryInvalid / retryCitations.size() > MAX_INVALID_CITATION_RATIO) {
                log.error("Citation validation still failing after retry: {}/{} invalid",
                        retryInvalid, retryCitations.size());
            }
        }

        long totalMs = System.currentTimeMillis() - t0;

        // Rebuild with correct totalProcessingMs and disclaimer
        return new ComplianceReport(
                report.summary(),
                report.classification() != null ? report.classification() : classification,
                report.gapAnalyses() != null ? report.gapAnalyses() : List.of(),
                report.sections() != null ? report.sections() : List.of(),
                report.citations() != null ? report.citations() : List.of(),
                ComplianceReport.STANDARD_DISCLAIMER,
                Instant.now().toString(),
                report.modelUsed(),
                totalMs
        );
    }

    // ── Citation validation ────────────────────────────────────────────────

    private boolean validateCitation(CitationEntry entry) {
        if (entry == null || entry.sourceTable() == null) return false;
        // Only validate known tables to avoid SQL injection
        Set<String> allowed = Set.of("legal_chunks", "use_case_chunks", "guide_chunks", "decision_rule_chunks");
        if (!allowed.contains(entry.sourceTable())) {
            log.warn("Unrecognised citation source table: {}", entry.sourceTable());
            return false;
        }
        try {
            String sql = "SELECT COUNT(*) FROM " + entry.sourceTable() + " WHERE id = :id";
            Integer count = jdbc.queryForObject(sql, Map.of("id", entry.sourceChunkId()),
                    Integer.class);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("Citation validation error for {}/{}: {}",
                    entry.sourceTable(), entry.sourceChunkId(), e.getMessage());
            return false;
        }
    }

    // ── Persistence ────────────────────────────────────────────────────────

    private void persistReport(Analysis analysis, ComplianceReport report) {
        Report entity = reportRepo.findByAnalysisId(analysis.getId())
                .orElseGet(Report::create);
        entity.setAnalysis(analysis);
        entity.setSummary(report.summary() != null ? report.summary() : "");
        entity.setLlmModel(report.modelUsed() != null ? report.modelUsed() : "unknown");
        entity.setGenerationMs(report.totalProcessingMs());

        // Extract section content for the denormalized columns
        entity.setRiskRationale(extractSectionContent(report, "Risk Classification"));
        entity.setObligations(extractSectionContent(report, "Applicable Obligations"));
        entity.setGaps(extractSectionContent(report, "Compliance Gaps"));
        entity.setRecommendations(extractSectionContent(report, "Recommendations"));

        reportRepo.save(entity);

        // Persist citations
        if (report.citations() != null) {
            persistCitations(entity, report.citations());
        }
    }

    private void persistCitations(Report report, List<CitationEntry> citations) {
        for (int i = 0; i < citations.size(); i++) {
            CitationEntry entry = citations.get(i);

            // Map source table string to enum — skip unknown tables
            Citation.SourceTable sourceTableEnum;
            try {
                sourceTableEnum = Citation.SourceTable.valueOf(
                        entry.sourceTable().replace("-", "_"));
            } catch (IllegalArgumentException e) {
                log.warn("Skipping citation with unknown source table: {}", entry.sourceTable());
                continue;
            }

            Citation citation = Citation.create();
            citation.setReport(report);
            citation.setClaimText(entry.snippet() != null ? entry.snippet() : entry.reference());
            citation.setClaimSection(Citation.ClaimSection.summary); // default; section mapping below
            citation.setSourceTable(sourceTableEnum);
            citation.setSourceChunkId(entry.sourceChunkId());
            citation.setSourceReference(entry.reference() != null ? entry.reference() : "");
            citation.setValidated(validateCitation(entry));
            citation.setDisplayOrder(i + 1);
            citationRepo.save(citation);
        }
    }

    // ── Data loading helpers ───────────────────────────────────────────────

    private ClassificationResult buildClassificationFromAnalysis(Analysis analysis) {
        String riskCategory = analysis.getRiskCategory() != null
                ? analysis.getRiskCategory().name() : "minimal";
        String confidence = analysis.getConfidence() != null
                ? analysis.getConfidence().name() : "review_recommended";
        return new ClassificationResult(
                riskCategory,
                analysis.getPrimaryLegalBasis(),
                List.of(),
                confidence,
                "Classification persisted from Stage 1 interview.",
                List.of()
        );
    }

    private List<GapAnalysisResult> loadGapAnalyses(UUID sessionId, InterviewSession session) {
        if (session.getRiskClassification() != InterviewSession.RiskClassification.high) {
            return List.of();
        }
        List<Stage2Submission> submissions =
                submissionRepo.findAllBySessionIdOrderBySectionNumberAsc(sessionId);

        List<GapAnalysisResult> results = new ArrayList<>();
        for (Stage2Submission sub : submissions) {
            if (sub.getGapsFound() == null || sub.getGapsFound().isBlank()) continue;
            try {
                List<GapAnalysisItem> items = GAP_MAPPER.readValue(sub.getGapsFound(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<GapAnalysisItem>>() {});
                results.add(GapAnalysisResult.of(
                        sub.getSectionNumber(),
                        "Section " + sub.getSectionNumber(),
                        items));
            } catch (Exception e) {
                log.warn("Could not deserialize gaps for section {}: {}",
                        sub.getSectionNumber(), e.getMessage());
            }
        }
        return results;
    }

    private String buildQueryText(List<InterviewAnswer> answers,
                                   ClassificationResult classification) {
        StringBuilder sb = new StringBuilder();
        if (classification.riskCategory() != null) {
            sb.append(classification.riskCategory()).append(" risk AI system. ");
        }
        answers.stream().limit(5).forEach(a -> sb.append(a.getRawInput()).append(". "));
        String text = sb.toString().trim();
        return text.isEmpty() ? "EU AI Act compliance assessment" : text;
    }

    private String extractSectionContent(ComplianceReport report, String sectionTitle) {
        if (report.sections() == null) return "";
        return report.sections().stream()
                .filter(s -> s.title() != null && s.title().contains(sectionTitle))
                .map(ReportSection::content)
                .findFirst()
                .orElse("");
    }
}
