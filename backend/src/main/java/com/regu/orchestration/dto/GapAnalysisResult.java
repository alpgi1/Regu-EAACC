package com.regu.orchestration.dto;

import java.util.List;

/**
 * Aggregated gap analysis result for one Annex IV section.
 */
public record GapAnalysisResult(
        int sectionNumber,
        String sectionTitle,
        List<GapAnalysisItem> items,
        int totalRequirements,
        int metRequirements,
        int gapCount,
        double compliancePercentage
) {

    /** Convenience constructor that computes derived counters from the items list. */
    public static GapAnalysisResult of(int sectionNumber, String sectionTitle,
                                       List<GapAnalysisItem> items) {
        int met = (int) items.stream().filter(GapAnalysisItem::found).count();
        int gaps = items.size() - met;
        double pct = items.isEmpty() ? 0.0 : (met * 100.0) / items.size();
        return new GapAnalysisResult(sectionNumber, sectionTitle, items,
                items.size(), met, gaps, Math.round(pct * 10.0) / 10.0);
    }
}
