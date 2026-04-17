package com.regu.api;

import com.regu.api.dto.*;
import com.regu.api.mapper.InterviewMapper;
import com.regu.domain.InterviewSession;
import com.regu.domain.repository.InterviewAnswerRepository;
import com.regu.domain.repository.InterviewSessionRepository;
import com.regu.orchestration.ComplianceAnalysisService;
import com.regu.orchestration.dto.AnalysisSession;
import com.regu.orchestration.dto.ClassificationResult;
import com.regu.orchestration.dto.ComplianceReport;
import com.regu.orchestration.dto.NextStepResponse;
import com.regu.orchestration.exception.InterviewStateException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/interviews")
@Tag(name = "Interview", description = "Stage 1 compliance interview")
public class InterviewController {

    private static final Logger log = LoggerFactory.getLogger(InterviewController.class);

    private final ComplianceAnalysisService analysisService;
    private final InterviewSessionRepository sessionRepo;
    private final InterviewAnswerRepository answerRepo;

    public InterviewController(
            ComplianceAnalysisService analysisService,
            InterviewSessionRepository sessionRepo,
            InterviewAnswerRepository answerRepo) {
        this.analysisService = analysisService;
        this.sessionRepo     = sessionRepo;
        this.answerRepo      = answerRepo;
    }

    // ── POST /api/v1/interviews ────────────────────────────────────────────

    @Operation(summary = "Start a new compliance interview",
               description = "Creates a new analysis session and returns the first question.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Session created"),
            @ApiResponse(responseCode = "500", description = "Unexpected error")
    })
    @PostMapping
    public ResponseEntity<StartInterviewResponse> startInterview(
            @RequestBody(required = false) StartInterviewRequest ignored) {

        long t0 = System.currentTimeMillis();
        AnalysisSession session = analysisService.startAnalysis();

        // Build QuestionDto from the AnalysisSession projection
        QuestionDto firstQuestion = buildQuestionFromSession(session);
        StartInterviewResponse response = InterviewMapper.toStartResponse(
                session.sessionId(), firstQuestion);

        log.info("POST /interviews → session {} created in {}ms",
                session.sessionId(), System.currentTimeMillis() - t0);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── POST /api/v1/interviews/{sessionId}/answer ─────────────────────────

    @Operation(summary = "Submit an answer and advance the interview")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Answer recorded"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Session not found"),
            @ApiResponse(responseCode = "409", description = "Session already complete"),
            @ApiResponse(responseCode = "502", description = "LLM service unavailable")
    })
    @PostMapping("/{sessionId}/answer")
    public ResponseEntity<ApiNextStepResponse> submitAnswer(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SubmitAnswerRequest request) {

        long t0 = System.currentTimeMillis();

        // Determine the answer text: prefer answerId for choice questions
        String answerText = request.answerId() != null ? request.answerId() : request.freeText();
        if (answerText == null || answerText.isBlank()) {
            throw new IllegalArgumentException("Either answerId or freeText must be provided");
        }

        NextStepResponse internal = analysisService.processAnswer(
                sessionId, request.questionKey(), answerText);

        ApiNextStepResponse response = mapNextStep(internal);

        log.info("POST /interviews/{}/answer [{}] → status={} in {}ms",
                sessionId, request.questionKey(), response.status(),
                System.currentTimeMillis() - t0);
        return ResponseEntity.ok(response);
    }

    // ── POST /api/v1/interviews/{sessionId}/skip-to-stage2 ────────────────

    @Operation(summary = "Skip to Stage 2 with user-declared high-risk classification")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session advanced to Stage 2"),
            @ApiResponse(responseCode = "404", description = "Session not found"),
            @ApiResponse(responseCode = "409", description = "Session already in Stage 2 or complete")
    })
    @PostMapping("/{sessionId}/skip-to-stage2")
    public ResponseEntity<ClassificationSummaryDto> skipToStage2(
            @PathVariable UUID sessionId,
            @RequestBody(required = false) SkipToStage2Request ignored) {

        long t0 = System.currentTimeMillis();
        analysisService.skipToHighRisk(sessionId);

        // Return a synthetic classification summary reflecting user-declared high-risk
        ClassificationSummaryDto summary = new ClassificationSummaryDto(
                "high", "User-declared (Article 6 / Annex III)", "user_declared",
                "The user self-declared this system as high-risk.", java.util.List.of());

        log.info("POST /interviews/{}/skip-to-stage2 → stage2 ready in {}ms",
                sessionId, System.currentTimeMillis() - t0);
        return ResponseEntity.ok(summary);
    }

    // ── GET /api/v1/interviews/{sessionId}/status ──────────────────────────

    @Operation(summary = "Get interview session status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status returned"),
            @ApiResponse(responseCode = "404", description = "Session not found")
    })
    @GetMapping("/{sessionId}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable UUID sessionId) {
        InterviewSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new InterviewStateException("Session not found: " + sessionId));

        int answeredCount = answerRepo.findAllBySession_IdOrderByAnsweredAtAsc(sessionId).size();

        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "status", session.getStatus().name(),
                "stage", (int) session.getStage(),
                "answeredQuestions", answeredCount,
                "riskClassification",
                        session.getRiskClassification() != null
                                ? session.getRiskClassification().name() : "pending"
        ));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Build a minimal QuestionDto from the AnalysisSession projection (raw JSON fields). */
    private QuestionDto buildQuestionFromSession(AnalysisSession session) {
        // The AnalysisSession contains raw answersJson — parse it via InterviewMapper
        com.regu.orchestration.stage1.InterviewQuestionDto raw =
                new com.regu.orchestration.stage1.InterviewQuestionDto(
                        session.currentQuestionKey(),
                        session.currentQuestionText(),
                        session.currentQuestionHint(),
                        session.currentQuestionAnswersJson(),
                        null, (short) 1, null, 0, null, false);
        return InterviewMapper.toQuestionDto(raw);
    }

    private ApiNextStepResponse mapNextStep(NextStepResponse internal) {
        return switch (internal.phase()) {
            case "interview" -> {
                com.regu.orchestration.stage1.InterviewQuestionDto raw =
                        new com.regu.orchestration.stage1.InterviewQuestionDto(
                                internal.nextQuestionKey(),
                                internal.nextQuestionText(),
                                internal.nextQuestionHint(),
                                internal.nextQuestionAnswersJson(),
                                null, (short) 1, null, 0, null, false);
                yield InterviewMapper.toNextQuestion(raw);
            }
            case "stage2_required" ->
                    InterviewMapper.toStage2Required(internal.classification());
            case "complete" -> {
                ComplianceReport report = internal.report();
                // Report is persisted; we need its ID from the DB — not in memory.
                // For now return report_ready with a null reportId (client calls GET /reports)
                yield InterviewMapper.toReportReady(internal.classification(), null);
            }
            default -> InterviewMapper.toStage2Required(internal.classification());
        };
    }
}
