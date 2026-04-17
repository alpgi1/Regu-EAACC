package com.regu.api.mapper;

import com.regu.api.dto.*;
import com.regu.orchestration.dto.*;

import java.util.List;
import java.util.UUID;

/** Static mappers for report-related DTOs. */
public final class ReportMapper {

    private ReportMapper() {}

    public static ReportResponse toReportResponse(ComplianceReport r, UUID reportId, UUID analysisId) {
        if (r == null) return null;
        return new ReportResponse(
                reportId,
                analysisId,
                r.summary(),
                InterviewMapper.toClassificationSummary(r.classification()),
                r.sections() != null
                        ? r.sections().stream().map(ReportMapper::toSectionDto).toList()
                        : List.of(),
                r.gapAnalyses() != null
                        ? r.gapAnalyses().stream().map(Stage2Mapper::toGapResponse).toList()
                        : List.of(),
                r.citations() != null
                        ? r.citations().stream().map(ReportMapper::toCitationDto).toList()
                        : List.of(),
                r.disclaimer(),
                r.generatedAt(),
                r.totalProcessingMs()
        );
    }

    public static ReportSectionDto toSectionDto(ReportSection s) {
        if (s == null) return null;
        return new ReportSectionDto(s.title(), s.content(), s.citationRefs());
    }

    public static CitationDto toCitationDto(CitationEntry c) {
        if (c == null) return null;
        return new CitationDto(
                c.citationId(), c.sourceTable(), c.sourceChunkId(), c.reference(), c.snippet());
    }
}
