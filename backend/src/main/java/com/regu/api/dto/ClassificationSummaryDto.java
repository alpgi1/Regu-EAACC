package com.regu.api.dto;

import java.util.List;

/** Public-facing classification result included in API responses. */
public record ClassificationSummaryDto(
        String riskCategory,
        String primaryLegalBasis,
        String confidence,
        String reasoning,
        List<Integer> applicableArticles
) {}
