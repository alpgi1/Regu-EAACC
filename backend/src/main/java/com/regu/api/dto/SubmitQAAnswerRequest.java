package com.regu.api.dto;

import jakarta.validation.constraints.NotBlank;

/** One Q&A answer for a specific Annex IV requirement in Stage 2. */
public record SubmitQAAnswerRequest(
        int sectionNumber,

        @NotBlank(message = "requirementId must not be blank")
        String requirementId,

        @NotBlank(message = "answer must not be blank")
        String answer
) {}
