package com.regu.orchestration.llm;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Abstraction over all LLM chat calls made by the orchestration layer.
 *
 * <p>All LLM calls in the system MUST go through this interface.
 * No code outside {@link ClaudeSonnetClient} may call the Anthropic API directly.
 *
 * <p>Two call families are provided:
 * <ul>
 *   <li>{@code call} — returns the raw text response (use when post-processing
 *       is handled by the caller, or for non-JSON outputs).</li>
 *   <li>{@code callWithSchema} / {@code callWithTypeRef} — instructs the model
 *       to return valid JSON and deserialises it into the requested type.
 *       Retries once on JSON parse failure before throwing.</li>
 * </ul>
 */
public interface LlmClient {

    /**
     * Basic call with default token limit from config (4096).
     */
    LlmResponse call(String systemPrompt, String userPrompt);

    /**
     * Call with an explicit token limit, overriding the configured default.
     * Use {@code regu.llm.classification-max-tokens} / {@code report-max-tokens} values.
     */
    LlmResponse call(String systemPrompt, String userPrompt, int maxTokens);

    /**
     * Call that enforces a JSON response and deserialises into {@code responseType}.
     * Uses default token limit. Retries once if the first response is not valid JSON.
     *
     * @throws RuntimeException wrapping the parse error if retry also fails
     */
    <T> T callWithSchema(String systemPrompt, String userPrompt, Class<T> responseType);

    /**
     * Call that enforces JSON response, deserialises into {@code responseType},
     * with an explicit token limit.
     */
    <T> T callWithSchema(String systemPrompt, String userPrompt,
                         Class<T> responseType, int maxTokens);

    /**
     * Call that enforces JSON response and deserialises using a Jackson
     * {@link TypeReference} — needed for generic types such as {@code List<GapAnalysisItem>}.
     */
    <T> T callWithTypeRef(String systemPrompt, String userPrompt,
                          TypeReference<T> typeRef, int maxTokens);
}
