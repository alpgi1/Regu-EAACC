package com.regu.api.dto;

/** The next unanswered Annex IV requirement in Q&A mode. */
public record NextRequirementDto(
        String requirementId,
        String entityName,
        String fallbackPrompt,
        boolean isOptional,
        int sectionNumber
) {}
