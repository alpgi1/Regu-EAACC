package com.regu.orchestration.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Typed output from the Stage 1 risk classification LLM call.
 *
 * <p>Uses flexible deserialization for {@code applicableArticles} — the LLM
 * may return integers ({@code [6, 9]}) or strings ({@code ["Article 6"]}).
 * Both formats are normalised to {@code List<Integer>}.
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
) {
    private static final Pattern ARTICLE_NUM = Pattern.compile("\\d+");

    @JsonCreator
    public ClassificationResult(
            @JsonProperty("riskCategory")       String riskCategory,
            @JsonProperty("risk_category")       String riskCategorySnake,
            @JsonProperty("primaryLegalBasis")  String primaryLegalBasis,
            @JsonProperty("primary_legal_basis") String primaryLegalBasisSnake,
            @JsonProperty("applicableArticles") List<Object> applicableArticlesRaw,
            @JsonProperty("applicable_articles") List<Object> applicableArticlesSnake,
            @JsonProperty("confidence")         String confidence,
            @JsonProperty("reasoning")          String reasoning,
            @JsonProperty("citedChunkIds")      List<String> citedChunkIds,
            @JsonProperty("cited_chunk_ids")     List<String> citedChunkIdsSnake
    ) {
        this(
            riskCategory != null ? riskCategory : riskCategorySnake,
            primaryLegalBasis != null ? primaryLegalBasis : primaryLegalBasisSnake,
            normaliseArticles(applicableArticlesRaw != null ? applicableArticlesRaw : applicableArticlesSnake),
            confidence,
            reasoning,
            citedChunkIds != null ? citedChunkIds : citedChunkIdsSnake
        );
    }

    /**
     * Converts a mixed list of integers and strings like ["Article 6", 9, "Art. 52"]
     * into a clean list of integers: [6, 9, 52].
     */
    private static List<Integer> normaliseArticles(List<Object> raw) {
        if (raw == null) return List.of();
        List<Integer> result = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof Number n) {
                result.add(n.intValue());
            } else if (item instanceof String s) {
                Matcher m = ARTICLE_NUM.matcher(s);
                if (m.find()) {
                    try {
                        result.add(Integer.parseInt(m.group()));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return result;
    }
}
