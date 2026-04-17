package com.regu.api.dto;

/** A single gap analysis finding for one Annex IV requirement. */
public record GapItemDto(
        String requirementId,
        String entityName,
        boolean found,
        String extractedValue,
        String gapDescription,
        String severity,
        String recommendation
) {}
