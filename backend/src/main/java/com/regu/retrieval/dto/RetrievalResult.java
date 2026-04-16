package com.regu.retrieval.dto;

import java.util.List;

/**
 * The result of a retrieval operation — holds the ranked chunks plus
 * diagnostic metadata about how the retrieval was performed.
 *
 * @param chunks       ranked list of retrieved chunks (highest relevance first)
 * @param queryText    the original query text (preserved for downstream logging)
 * @param retrievalMs  wall-clock time the retrieval took in milliseconds
 * @param strategy     description of the retrieval strategy used
 *                     (e.g. {@code "hybrid-rrf"}, {@code "vector"}, {@code "metadata"})
 */
public record RetrievalResult(
        List<RetrievedChunk> chunks,
        String queryText,
        long retrievalMs,
        String strategy
) {
    /** Convenience factory for an empty result (no chunks found). */
    public static RetrievalResult empty(String queryText, long retrievalMs, String strategy) {
        return new RetrievalResult(List.of(), queryText, retrievalMs, strategy);
    }
}
