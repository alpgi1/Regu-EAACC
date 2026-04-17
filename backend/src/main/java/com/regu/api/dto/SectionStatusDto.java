package com.regu.api.dto;

/** Status of one Annex IV section within a Stage 2 assessment. */
public record SectionStatusDto(
        int sectionNumber,
        String sectionTitle,
        String status,                  // "pending" | "submitted" | "analyzed"
        Double compliancePercentage     // null if pending
) {}
