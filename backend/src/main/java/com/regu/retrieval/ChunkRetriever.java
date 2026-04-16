package com.regu.retrieval;

import com.regu.retrieval.dto.RetrievalQuery;
import com.regu.retrieval.dto.RetrievalResult;

/**
 * Contract for all single-table retrieval implementations.
 *
 * <p>Each implementation is responsible for one vector table
 * ({@code legal_chunks}, {@code use_case_chunks}, {@code guide_chunks},
 * {@code decision_rule_chunks}) and encapsulates the table-specific SQL,
 * filter logic, and result mapping.
 *
 * <p>All implementations must:
 * <ul>
 *   <li>Return an empty {@link RetrievalResult} (not null) when no chunks match</li>
 *   <li>Log retrieval timing at INFO level</li>
 *   <li>Be stateless — safe for concurrent use</li>
 * </ul>
 */
public interface ChunkRetriever {

    /**
     * Executes a retrieval query against this retriever's table.
     *
     * @param query the retrieval request (text, k, filters)
     * @return a non-null result containing matched chunks in ranked order
     */
    RetrievalResult retrieve(RetrievalQuery query);

    /**
     * Returns the PostgreSQL table name this retriever targets.
     * Used by {@code RetrievalOrchestrator} for routing.
     *
     * @return table name, e.g. {@code "legal_chunks"}
     */
    String getSourceTable();
}
