package com.regu.api.mapper;

import com.regu.api.dto.*;
import com.regu.domain.AnnexIvRequirement;
import com.regu.orchestration.dto.GapAnalysisItem;
import com.regu.orchestration.dto.GapAnalysisResult;
import com.regu.orchestration.dto.NextRequirementResponse;
import com.regu.orchestration.dto.Stage2Status;

import java.util.List;
import java.util.UUID;

/** Static mappers for Stage 2 Annex IV DTOs. */
public final class Stage2Mapper {

    private Stage2Mapper() {}

    public static Stage2StatusResponse toStatusResponse(UUID sessionId,
                                                         List<Stage2Status.SectionStatus> sections,
                                                         boolean allComplete) {
        List<SectionStatusDto> dtos = sections.stream()
                .map(s -> new SectionStatusDto(
                        s.sectionNumber(),
                        s.sectionTitle(),
                        s.completed() ? "analyzed" : "pending",
                        null   // compliancePercentage not available at overview level
                ))
                .toList();

        String overallStatus = allComplete ? "completed" : "in_progress";
        return new Stage2StatusResponse(sessionId, overallStatus, dtos);
    }

    public static GapAnalysisResponse toGapResponse(GapAnalysisResult r) {
        if (r == null) return null;
        List<GapItemDto> items = r.items() != null
                ? r.items().stream().map(Stage2Mapper::toGapItem).toList()
                : List.of();
        return new GapAnalysisResponse(
                r.sectionNumber(), r.sectionTitle(),
                r.totalRequirements(), r.metRequirements(), r.gapCount(),
                r.compliancePercentage(), items);
    }

    public static GapItemDto toGapItem(GapAnalysisItem i) {
        if (i == null) return null;
        return new GapItemDto(
                i.requirementId(), i.entityName(), i.found(),
                i.extractedValue(), i.gapDescription(), i.severity(), i.recommendation());
    }

    public static NextRequirementDto toNextRequirement(NextRequirementResponse r) {
        if (r == null) return null;
        return new NextRequirementDto(
                r.requirementId(), r.entityName(), r.fallbackPrompt(),
                false,   // isOptional not in NextRequirementResponse — default false
                r.sectionNumber());
    }

    public static NextRequirementDto toNextRequirementFromEntity(AnnexIvRequirement req) {
        if (req == null) return null;
        return new NextRequirementDto(
                req.getRequirementId(), req.getEntityName(), req.getFallbackPrompt(),
                req.isOptional(), req.getSectionNumber());
    }
}
