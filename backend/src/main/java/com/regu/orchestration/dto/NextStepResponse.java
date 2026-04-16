package com.regu.orchestration.dto;

/**
 * Returned after each answer in the interview.
 *
 * @param phase              "interview" | "stage2_required" | "complete"
 * @param nextQuestionKey    next question key (null when phase != "interview")
 * @param nextQuestionText   display text of the next question (null when not interviewing)
 * @param nextQuestionHint   optional hint text for the next question
 * @param nextQuestionAnswersJson JSONB answers blob for the next question
 * @param stage2Required     true when classification is high-risk and Stage 2 is needed
 * @param classification     populated once Stage 1 classification completes
 * @param report             populated only when phase = "complete" and risk is not high
 */
public record NextStepResponse(
        String phase,
        String nextQuestionKey,
        String nextQuestionText,
        String nextQuestionHint,
        String nextQuestionAnswersJson,
        boolean stage2Required,
        ClassificationResult classification,
        ComplianceReport report
) {}
