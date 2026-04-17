package com.regu.api.dto;

import java.util.UUID;

/**
 * Response after submitting an interview answer.
 *
 * @param status            "next_question" | "classified" | "stage2_required" | "report_ready"
 * @param nextQuestion      present when status = "next_question"
 * @param classification    present when status != "next_question"
 * @param reportId          present when status = "report_ready"
 */
public record ApiNextStepResponse(
        String status,
        QuestionDto nextQuestion,
        ClassificationSummaryDto classification,
        UUID reportId
) {}
