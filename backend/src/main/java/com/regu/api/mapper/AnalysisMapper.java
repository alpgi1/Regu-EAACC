package com.regu.api.mapper;

import com.regu.api.dto.AnalysisListItemDto;
import com.regu.domain.Analysis;

/** Static mapper for Analysis entity → API DTO. */
public final class AnalysisMapper {

    private AnalysisMapper() {}

    public static AnalysisListItemDto toListItem(Analysis a) {
        return new AnalysisListItemDto(
                a.getId(),
                a.getStatus() != null ? a.getStatus().name() : null,
                a.getRiskCategory() != null ? a.getRiskCategory().name() : null,
                a.getConfidence() != null ? a.getConfidence().name() : null,
                a.getCreatedAt() != null ? a.getCreatedAt().toString() : null,
                a.getCompletedAt() != null ? a.getCompletedAt().toString() : null
        );
    }
}
