package com.regu.api.dto;

import java.util.UUID;

/** Response to POST /api/v1/interviews — session created, first question included. */
public record StartInterviewResponse(
        UUID sessionId,
        String status,
        QuestionDto firstQuestion
) {}
