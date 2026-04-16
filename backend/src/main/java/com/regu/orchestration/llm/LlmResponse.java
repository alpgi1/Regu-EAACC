package com.regu.orchestration.llm;

/**
 * Captures the raw result of one LLM call including usage telemetry.
 *
 * @param content      raw text content returned by the model
 * @param model        model identifier as reported by the provider
 * @param inputTokens  number of tokens in the prompt (0 if unavailable)
 * @param outputTokens number of tokens in the completion (0 if unavailable)
 * @param latencyMs    wall-clock time for the API round-trip in milliseconds
 */
public record LlmResponse(
        String content,
        String model,
        int inputTokens,
        int outputTokens,
        long latencyMs
) {}
