package com.regu.retrieval.dto;

import java.util.Map;

/**
 * Specifies a retrieval request — what to search for, how many results to
 * return, and optional metadata filters to narrow the result set.
 *
 * <p>Filters are table-specific:
 * <ul>
 *   <li>{@code legal_chunks} — {@code article_number}, {@code risk_level},
 *       {@code document_type}, {@code citation_eligible}</li>
 *   <li>{@code use_case_chunks} — {@code risk_category}, {@code sector},
 *       {@code actor_role}</li>
 *   <li>{@code guide_chunks} — {@code related_articles} (integer list)</li>
 *   <li>{@code decision_rule_chunks} — {@code chunk_type}, {@code section},
 *       {@code related_articles}</li>
 * </ul>
 *
 * @param queryText  natural-language search query; must not be null or blank
 * @param maxResults maximum number of chunks to return per table (default 5)
 * @param filters    optional metadata filters (may be null or empty)
 */
public record RetrievalQuery(
        String queryText,
        int maxResults,
        Map<String, Object> filters
) {
    /** Convenience constructor with default maxResults and no filters. */
    public static RetrievalQuery of(String queryText) {
        return new RetrievalQuery(queryText, 5, Map.of());
    }

    /** Convenience constructor with custom maxResults and no filters. */
    public static RetrievalQuery of(String queryText, int maxResults) {
        return new RetrievalQuery(queryText, maxResults, Map.of());
    }

    /** Normalises null filters to an empty map. */
    public RetrievalQuery {
        if (filters == null) filters = Map.of();
        if (maxResults <= 0) maxResults = 5;
    }
}
