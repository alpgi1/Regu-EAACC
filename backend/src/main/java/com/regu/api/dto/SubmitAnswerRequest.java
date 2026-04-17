package com.regu.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Answer submitted to advance the Stage 1 interview. */
public record SubmitAnswerRequest(
        @NotBlank(message = "questionKey must not be blank")
        String questionKey,

        String answerId,    // for choice questions
        String freeText     // for free_text questions; null otherwise
) {}
