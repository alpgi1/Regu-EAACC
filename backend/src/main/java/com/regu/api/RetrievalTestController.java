package com.regu.api;

import com.regu.retrieval.RetrievalOrchestrator;
import com.regu.retrieval.dto.RetrievalQuery;
import com.regu.retrieval.dto.RetrievalResult;
import com.regu.retrieval.dto.RetrievedChunk;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Internal retrieval test endpoints — available in the {@code dev} profile only.
 *
 * <p>These endpoints exist solely for manual development testing and are
 * NOT exposed in the production profile. They allow developers to probe
 * the retrieval layer interactively via curl before the full LLM pipeline
 * (Phase 5) is built.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /api/v1/internal/retrieval/search} — semantic search</li>
 *   <li>{@code POST /api/v1/internal/retrieval/articles} — article metadata lookup</li>
 *   <li>{@code POST /api/v1/internal/retrieval/rules} — deterministic rule lookup</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/internal/retrieval")
@Profile("dev")
public class RetrievalTestController {

    private final RetrievalOrchestrator orchestrator;

    public RetrievalTestController(RetrievalOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Semantic search across one or all tables.
     *
     * <p>Request body:
     * <pre>{@code {
     *   "query": "AI system used for hiring and recruitment",
     *   "maxResults": 5,
     *   "table": "all | legal | use_case | guide | decision_rule",
     *   "filters": { "article_number": 10 }
     * }}</pre>
     *
     * @param request search parameters
     * @return ranked retrieval result
     */
    @PostMapping("/search")
    public RetrievalResult search(@RequestBody SearchRequest request) {
        RetrievalQuery query = new RetrievalQuery(
                request.query(),
                request.maxResults() > 0 ? request.maxResults() : 5,
                request.filters() != null ? request.filters() : Map.of()
        );

        String table = request.table() != null ? request.table().trim().toLowerCase() : "all";
        if (table.equals("all")) {
            return orchestrator.retrieveFromAll(query);
        }
        return orchestrator.retrieveFromTable(table, query);
    }

    /**
     * Article metadata lookup — retrieves all paragraphs of the specified
     * EU AI Act articles without embedding. Useful for verifying corpus
     * coverage and inspecting obligation text.
     *
     * <p>Request body: {@code { "articleNumbers": [9, 10, 14] }}
     *
     * @param request article numbers
     * @return all matching legal_chunks ordered by article, then paragraph
     */
    @PostMapping("/articles")
    public RetrievalResult articleLookup(@RequestBody ArticleRequest request) {
        int[] nums = request.articleNumbers() != null
                ? request.articleNumbers().stream().mapToInt(Integer::intValue).toArray()
                : new int[0];
        return orchestrator.retrieveLegalWithArticles(nums);
    }

    /**
     * Deterministic rule lookup — retrieves decision rule chunks by stable
     * rule_id without embedding. Preserves the input order.
     *
     * <p>Request body: {@code { "ruleIds": ["fli_q_hr4", "fli_outcome_high_risk"] }}
     *
     * @param request rule identifiers
     * @return matched chunks in input order
     */
    @PostMapping("/rules")
    public List<RetrievedChunk> ruleLookup(@RequestBody RuleRequest request) {
        return orchestrator.retrieveByRuleIds(
                request.ruleIds() != null ? request.ruleIds() : List.of()
        );
    }

    // ── Request body records ─────────────────────────────────────────────

    /**
     * @param query      natural-language query text
     * @param maxResults maximum chunks to return (default 5)
     * @param table      target: {@code all | legal | use_case | guide | decision_rule}
     * @param filters    optional metadata filters — keys are table-specific
     */
    record SearchRequest(
            String query,
            int maxResults,
            String table,
            Map<String, Object> filters
    ) {}

    /**
     * @param articleNumbers list of EU AI Act article numbers to look up
     */
    record ArticleRequest(List<Integer> articleNumbers) {}

    /**
     * @param ruleIds list of stable decision rule identifiers (e.g. {@code "fli_q_hr4"})
     */
    record RuleRequest(List<String> ruleIds) {}
}
