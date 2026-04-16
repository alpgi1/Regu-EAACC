package com.regu.orchestration.dto;

/**
 * One granular requirement check result from the Stage 2 Annex IV gap analysis.
 *
 * @param requirementId    stable Annex IV identifier, e.g. "1_a_1"
 * @param entityName       human-readable name, e.g. "Intended purpose"
 * @param found            true only if the document contains substantive information
 * @param extractedValue   quoted/paraphrased passage from the document (null if not found)
 * @param gapDescription   what is missing (null if found)
 * @param severity         "critical" | "major" | "minor"
 * @param recommendation   specific, actionable advice for the user
 */
public record GapAnalysisItem(
        String requirementId,
        String entityName,
        boolean found,
        String extractedValue,
        String gapDescription,
        String severity,
        String recommendation
) {}
