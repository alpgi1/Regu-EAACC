package com.regu.api.dto;

import java.util.List;

/** Gap analysis results for one Annex IV section. */
public record GapAnalysisResponse(
        int sectionNumber,
        String sectionTitle,
        int totalRequirements,
        int metRequirements,
        int gapCount,
        double compliancePercentage,
        List<GapItemDto> items
) {}
