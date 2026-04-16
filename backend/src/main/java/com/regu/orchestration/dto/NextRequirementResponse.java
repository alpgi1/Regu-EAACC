package com.regu.orchestration.dto;

/**
 * Returned by {@code Stage2OrchestratorService.getNextRequirement()} in Q&A mode.
 * Provides the next unanswered Annex IV requirement to present to the user.
 */
public record NextRequirementResponse(
        String requirementId,
        String entityName,
        String fallbackPrompt,
        int sectionNumber,
        boolean isLastRequirement
) {}
