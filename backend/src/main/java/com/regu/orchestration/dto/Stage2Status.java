package com.regu.orchestration.dto;

import java.util.List;
import java.util.UUID;

/**
 * Returned from {@code Stage2OrchestratorService.startStage2()} and used
 * by the API layer to present progress across all 9 Annex IV sections.
 */
public record Stage2Status(
        UUID sessionId,
        List<SectionStatus> sections,
        int totalSections,
        int completedSections
) {

    /**
     * Completion status for one Annex IV section.
     *
     * @param sectionNumber  1–9
     * @param sectionTitle   display title from annex_iv_sections
     * @param completed      true when a stage2_submission row exists for this section
     */
    public record SectionStatus(
            int sectionNumber,
            String sectionTitle,
            boolean completed
    ) {}
}
