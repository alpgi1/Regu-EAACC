package com.regu.orchestration;

import com.regu.domain.Analysis;
import com.regu.domain.InterviewSession;
import com.regu.domain.repository.AnalysisRepository;
import com.regu.domain.repository.InterviewSessionRepository;
import com.regu.orchestration.dto.*;
import com.regu.orchestration.exception.InterviewStateException;
import com.regu.orchestration.report.ReportGenerationService;
import com.regu.orchestration.stage1.NextQuestionResponse;
import com.regu.orchestration.stage1.SkipToStage2Service;
import com.regu.orchestration.stage1.Stage1InterviewService;
import com.regu.orchestration.stage2.Stage2OrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Top-level orchestrator that wires Stage 1 interview, Stage 2 gap assessment,
 * and report generation into a single coherent flow.
 *
 * <p>This is the primary entry point for the API layer. All business logic
 * is delegated to the specialized service components.
 *
 * <p>Q&A answer collection for Stage 2 is stateful within a session+section pair.
 * Partial answers are accumulated in {@link #pendingQaAnswers} until the caller
 * signals section completion by calling {@link #finalizeQASection}.
 */
@Service
public class ComplianceAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceAnalysisService.class);

    /** In-memory accumulator: (sessionId:sectionNumber) → (requirementId → answerText). */
    private final Map<String, Map<String, String>> pendingQaAnswers = new ConcurrentHashMap<>();

    private final AnalysisRepository         analysisRepo;
    private final InterviewSessionRepository sessionRepo;
    private final Stage1InterviewService     stage1;
    private final SkipToStage2Service        skipService;
    private final Stage2OrchestratorService  stage2;
    private final ReportGenerationService    reportService;

    public ComplianceAnalysisService(
            AnalysisRepository analysisRepo,
            InterviewSessionRepository sessionRepo,
            Stage1InterviewService stage1,
            SkipToStage2Service skipService,
            Stage2OrchestratorService stage2,
            ReportGenerationService reportService) {
        this.analysisRepo  = analysisRepo;
        this.sessionRepo   = sessionRepo;
        this.stage1        = stage1;
        this.skipService   = skipService;
        this.stage2        = stage2;
        this.reportService = reportService;
    }

    // ── Stage 1: start and drive the interview ─────────────────────────────

    /**
     * Creates a new {@link Analysis} and {@link InterviewSession}, starts Stage 1,
     * and returns the first question (always E1).
     */
    @Transactional
    public AnalysisSession startAnalysis() {
        Analysis analysis = new Analysis("interview_session_started",
                Analysis.InputSource.text_paste);
        analysis.setStatus(Analysis.Status.processing);
        analysisRepo.save(analysis);

        Stage1InterviewService.InterviewSessionWithQuestion result =
                stage1.startInterview(analysis);

        InterviewSession session = result.session();
        log.info("Analysis {} started, session {}", analysis.getId(), session.getId());

        return new AnalysisSession(
                session.getId(),
                analysis.getId(),
                session.getStatus().name(),
                result.question().questionKey(),
                result.question().displayText(),
                result.question().hintText(),
                result.question().answersJson()
        );
    }

    /**
     * Records an answer and advances the interview. Returns one of:
     * <ul>
     *   <li>{@code phase="interview"} — more questions remain.</li>
     *   <li>{@code phase="stage2_required"} — high-risk classification, Stage 2 needed.</li>
     *   <li>{@code phase="complete"} — non-high-risk; report generated inline.</li>
     * </ul>
     */
    @Transactional
    public NextStepResponse processAnswer(UUID sessionId, String questionKey, String answer) {
        NextQuestionResponse response = stage1.answerAndGetNext(sessionId, questionKey, answer);

        if (!response.classified()) {
            return new NextStepResponse(
                    "interview",
                    response.nextQuestion().questionKey(),
                    response.nextQuestion().displayText(),
                    response.nextQuestion().hintText(),
                    response.nextQuestion().answersJson(),
                    false, null, null
            );
        }

        ClassificationResult classification = response.classification();

        if ("high".equals(classification.riskCategory())) {
            log.info("Session {} classified as high-risk — Stage 2 required", sessionId);
            return new NextStepResponse(
                    "stage2_required",
                    null, null, null, null,
                    true, classification, null
            );
        }

        // Not high-risk — return classification only; report can be generated on demand via POST /report
        log.info("Session {} classified as {} — returning classification, skipping inline report",
                sessionId, classification.riskCategory());
        return new NextStepResponse("complete", null, null, null, null,
                false, classification, null);
    }

    /**
     * Fast-tracks to Stage 2 with a user-declared high-risk classification. No LLM call.
     */
    @Transactional
    public AnalysisSession skipToHighRisk(UUID sessionId) {
        skipService.skipToStage2(sessionId);

        InterviewSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new InterviewStateException("Session not found: " + sessionId));

        UUID analysisId = session.getAnalysis() != null
                ? session.getAnalysis().getId() : null;

        return new AnalysisSession(
                session.getId(),
                analysisId,
                session.getStatus().name(),
                null, null, null, null
        );
    }

    // ── Stage 2: Annex IV gap assessment ───────────────────────────────────

    /** Validates the session is high-risk and returns the full Stage 2 section list. */
    @Transactional
    public Stage2Status startStage2(UUID sessionId) {
        return stage2.startStage2(sessionId);
    }

    /** Processes a full document upload for one Annex IV section. */
    @Transactional
    public GapAnalysisResult submitDocument(UUID sessionId, int sectionNumber,
                                             String documentText) {
        return stage2.processDocumentUpload(sessionId, sectionNumber, documentText);
    }

    /**
     * Accumulates one Q&amp;A answer for a section. Returns the next unanswered
     * requirement, or {@code null} when all requirements for the section are answered
     * (caller should then invoke {@link #finalizeQASection}).
     */
    @Transactional
    public NextRequirementResponse submitQAAnswer(UUID sessionId, int sectionNumber,
                                                   String requirementId, String answer) {
        String key = sessionId + ":" + sectionNumber;
        pendingQaAnswers.computeIfAbsent(key, k -> new HashMap<>())
                .put(requirementId, answer);

        try {
            return stage2.getNextRequirement(sessionId, sectionNumber);
        } catch (InterviewStateException e) {
            // All requirements answered — caller should call finalizeQASection
            return null;
        }
    }

    /**
     * Finalises Q&A for a section by running LLM gap analysis on all collected answers.
     * Clears the in-memory accumulator for this section.
     */
    @Transactional
    public GapAnalysisResult finalizeQASection(UUID sessionId, int sectionNumber) {
        String key = sessionId + ":" + sectionNumber;
        Map<String, String> answers = pendingQaAnswers.remove(key);
        if (answers == null) answers = Map.of();
        return stage2.processQAAnswers(sessionId, sectionNumber, answers);
    }

    // ── Report generation ────────────────────────────────────────────────��─

    /**
     * Generates the final compliance report. For high-risk systems, call after
     * Stage 2 is complete (or partially complete — missing sections will be flagged).
     */
    @Transactional
    public ComplianceReport finalizeAndGenerateReport(UUID sessionId) {
        return reportService.generateReport(sessionId);
    }
}
