package com.regu.api.dto;

/** One selectable option in a choice-type interview question. */
public record AnswerOptionDto(
        String answerId,
        String label
) {}
