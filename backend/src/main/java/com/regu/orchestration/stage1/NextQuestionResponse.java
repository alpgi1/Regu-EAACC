package com.regu.orchestration.stage1;

import com.regu.orchestration.dto.ClassificationResult;

/**
 * Returned by {@link Stage1InterviewService#answerAndGetNext} after each answer.
 *
 * <p>Two states:
 * <ul>
 *   <li>{@code ongoing} — interview continues; {@code nextQuestion} is populated.</li>
 *   <li>{@code classified} — Stage 1 complete; {@code classification} is populated.</li>
 * </ul>
 */
public record NextQuestionResponse(
        boolean              classified,
        InterviewQuestionDto nextQuestion,
        ClassificationResult classification
) {

    /** Interview is still in progress — present the next question to the user. */
    public static NextQuestionResponse ongoing(InterviewQuestionDto nextQuestion) {
        return new NextQuestionResponse(false, nextQuestion, null);
    }

    /** Stage 1 is complete — classification result is ready. */
    public static NextQuestionResponse classified(ClassificationResult result) {
        return new NextQuestionResponse(true, null, result);
    }
}
