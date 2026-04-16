package com.regu.orchestration.dto;

import java.util.List;

/**
 * Typed output from the Stage 1 risk classification LLM call.
 *
 * @param riskCategory      "unacceptable" | "high" | "limited" | "minimal"
 * @param primaryLegalBasis e.g. "Annex III, point 4(a)"
 * @param applicableArticles all articles imposing obligations on this system
 * @param confidence        "high" | "medium" | "low" | "review_recommended" | "user_declared"
 * @param reasoning         1–3 sentences referencing the legal text
 * @param citedChunkIds     database IDs of legal_chunks used in the reasoning
 */
public record ClassificationResult(
        String riskCategory,
        String primaryLegalBasis,
        List<Integer> applicableArticles,
        String confidence,
        String reasoning,
        List<String> citedChunkIds
) {}
