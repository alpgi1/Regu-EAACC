package com.regu.api.dto;

import java.util.List;
import java.util.UUID;

/** Full compliance report returned to the caller. */
public record ReportResponse(
        UUID reportId,
        UUID analysisId,
        String summary,
        ClassificationSummaryDto classification,
        List<ReportSectionDto> sections,
        List<GapAnalysisResponse> gapAnalyses,
        List<CitationDto> citations,
        String disclaimer,
        String generatedAt,
        long totalProcessingMs
) {}
