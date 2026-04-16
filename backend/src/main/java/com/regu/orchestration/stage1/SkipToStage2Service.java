package com.regu.orchestration.stage1;

import com.regu.domain.Analysis;
import com.regu.domain.InterviewSession;
import com.regu.domain.repository.AnalysisRepository;
import com.regu.domain.repository.InterviewSessionRepository;
import com.regu.orchestration.dto.ClassificationResult;
import com.regu.orchestration.exception.InterviewStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Handles the "skip to Stage 2" fast-path where the user declares their system
 * is high-risk without going through the full Stage 1 interview.
 *
 * <p>The resulting {@link ClassificationResult} uses {@code confidence = "user_declared"}
 * to signal that this was not an assessed classification. The {@link Analysis} entity
 * is updated with {@code confidence = review_recommended} (the closest mapped enum value).
 */
@Service
public class SkipToStage2Service {

    private static final Logger log = LoggerFactory.getLogger(SkipToStage2Service.class);

    private final InterviewSessionRepository sessionRepo;
    private final AnalysisRepository         analysisRepo;

    public SkipToStage2Service(InterviewSessionRepository sessionRepo,
                                AnalysisRepository analysisRepo) {
        this.sessionRepo  = sessionRepo;
        this.analysisRepo = analysisRepo;
    }

    /**
     * Marks the session as high-risk (user-declared) and advances it to Stage 2.
     *
     * <p>No LLM call is made. The returned {@link ClassificationResult} has
     * {@code confidence = "user_declared"} so the report clearly reflects that
     * this classification was self-reported, not assessed.
     *
     * @param sessionId the active Stage 1 session to skip
     * @return a minimal ClassificationResult ready for Stage 2 intake
     */
    @Transactional
    public ClassificationResult skipToStage2(UUID sessionId) {
        InterviewSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new InterviewStateException(
                        "Session not found: " + sessionId));

        if (session.getStatus() != InterviewSession.Status.active
                && session.getStatus() != InterviewSession.Status.stage1_complete) {
            throw new InterviewStateException(
                    "Cannot skip to Stage 2 from session status: " + session.getStatus());
        }

        // Update session
        session.setRiskClassification(InterviewSession.RiskClassification.high);
        session.setStatus(InterviewSession.Status.stage1_complete);
        sessionRepo.save(session);

        // Update linked Analysis
        Analysis analysis = session.getAnalysis();
        if (analysis != null) {
            analysis.setRiskCategory(Analysis.RiskCategory.high);
            // user_declared is not in the enum — map to review_recommended so the
            // report clearly flags this classification as self-reported.
            analysis.setConfidence(Analysis.Confidence.review_recommended);
            analysis.setPrimaryLegalBasis("User-declared high-risk system (Annex III)");
            analysis.setStatus(Analysis.Status.processing);
            analysisRepo.save(analysis);
        }

        log.info("Session {} fast-tracked to Stage 2 (user-declared high-risk)", sessionId);

        return new ClassificationResult(
                "high",
                "User-declared high-risk system (Annex III)",
                List.of(9, 10, 11, 12, 13, 14, 15),   // standard high-risk obligation articles
                "user_declared",
                "The user has declared this system as high-risk under Annex III of the EU AI Act " +
                "and opted to proceed directly to the Annex IV technical documentation assessment. " +
                "This classification was not independently assessed.",
                List.of()
        );
    }
}
