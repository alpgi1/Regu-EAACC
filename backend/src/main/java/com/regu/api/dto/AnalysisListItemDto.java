package com.regu.api.dto;

import java.util.UUID;

/** Summary item for the analyses list endpoint. */
public record AnalysisListItemDto(
        UUID analysisId,
        String status,
        String riskCategory,
        String confidence,
        String createdAt,
        String completedAt
) {}
