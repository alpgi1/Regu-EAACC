package com.regu.api.dto;

import java.util.List;

/** Interview question presented to the user. */
public record QuestionDto(
        String questionKey,
        String questionText,
        String questionType,             // "single_choice" | "multiple_choice" | "yes_no" | "free_text"
        String category,
        List<AnswerOptionDto> options,   // null for free_text
        String explanation               // why this question matters
) {}
