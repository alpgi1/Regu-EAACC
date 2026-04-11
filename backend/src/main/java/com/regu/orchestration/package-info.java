/**
 * LLM router and multi-stage pipeline coordination.
 *
 * <p>Routes compliance analysis tasks between available LLM providers (Gemini, Claude),
 * manages the parallel retrieval + cross-check flow, and assembles intermediate results
 * into a payload ready for the reporting layer.
 *
 * <p>Populated in Phase 5.
 */
package com.regu.orchestration;
