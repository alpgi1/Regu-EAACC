package com.regu.orchestration.dto;

import java.util.UUID;

/**
 * Returned from {@code ComplianceAnalysisService.startAnalysis()} to the API layer.
 * Contains the session and analysis identifiers plus the first interview question.
 */
public record AnalysisSession(
        UUID sessionId,
        UUID analysisId,
        String sessionStatus,
        String currentQuestionKey,
        String currentQuestionText,
        String currentQuestionHint,
        String currentQuestionAnswersJson
) {}
