package com.regu.orchestration.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * {@link LlmClient} implementation backed by the Anthropic Messages API.
 *
 * <p>Calls {@code POST https://api.anthropic.com/v1/messages} directly via
 * Spring's {@link RestClient} — the same pattern used by {@code VoyageEmbeddingClient}
 * and {@code QueryEmbeddingClient}. No Spring AI framework dependency required.
 *
 * <p>The bean is created at startup even when {@code ANTHROPIC_API_KEY} is absent
 * (defaults to empty string). Calls fail at runtime when the key is missing —
 * startup is always safe.
 */
@Service
public class ClaudeSonnetClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeSonnetClient.class);

    private static final String BASE_URL        = "https://api.anthropic.com";
    private static final String MESSAGES_PATH   = "/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final RestClient restClient;
    private final String     model;
    private final double     temperature;
    private final int        maxRetries;

    public ClaudeSonnetClient(
            @Value("${regu.llm.api-key:}")          String  apiKey,
            @Value("${regu.llm.model:claude-sonnet-4-20250514}") String  model,
            @Value("${regu.llm.temperature:0.1}")   double  temperature,
            @Value("${regu.llm.max-retries:2}")     int     maxRetries) {
        this.model       = model;
        this.temperature = temperature;
        this.maxRetries  = maxRetries;
        // RestClient.builder() is used inline per gotcha #16 — Builder is not auto-configured.
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("x-api-key",          apiKey)
                .defaultHeader("anthropic-version",  ANTHROPIC_VERSION)
                .defaultHeader("content-type",        "application/json")
                .build();
    }

    // ── LlmClient interface ───────────────────────────────────────────────

    @Override
    public LlmResponse call(String systemPrompt, String userPrompt) {
        return doCall(systemPrompt, userPrompt, 4096);
    }

    @Override
    public LlmResponse call(String systemPrompt, String userPrompt, int maxTokens) {
        return doCall(systemPrompt, userPrompt, maxTokens);
    }

    @Override
    public <T> T callWithSchema(String systemPrompt, String userPrompt, Class<T> responseType) {
        return callWithSchema(systemPrompt, userPrompt, responseType, 4096);
    }

    @Override
    public <T> T callWithSchema(String systemPrompt, String userPrompt,
                                Class<T> responseType, int maxTokens) {
        String enhanced = jsonSystemPrompt(systemPrompt);
        return parseWithRetry(enhanced, userPrompt, maxTokens,
                raw -> OBJECT_MAPPER.readValue(extractJson(raw), responseType));
    }

    @Override
    public <T> T callWithTypeRef(String systemPrompt, String userPrompt,
                                 TypeReference<T> typeRef, int maxTokens) {
        String enhanced = jsonSystemPrompt(systemPrompt);
        return parseWithRetry(enhanced, userPrompt, maxTokens,
                raw -> OBJECT_MAPPER.readValue(extractJson(raw), typeRef));
    }

    // ── Core HTTP call ────────────────────────────────────────────────────

    private LlmResponse doCall(String systemPrompt, String userPrompt, int maxTokens) {
        long t0 = System.currentTimeMillis();

        AnthropicRequest request = new AnthropicRequest(
                model,
                maxTokens,
                temperature,
                systemPrompt,
                List.of(new AnthropicMessage("user", userPrompt))
        );

        AnthropicResponse response = restClient.post()
                .uri(MESSAGES_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AnthropicResponse.class);

        long latency = System.currentTimeMillis() - t0;

        if (response == null) {
            throw new RuntimeException("Anthropic API returned null response");
        }

        String content = response.content() != null && !response.content().isEmpty()
                ? response.content().get(0).text()
                : "";
        int inputTokens  = response.usage() != null ? response.usage().inputTokens()  : 0;
        int outputTokens = response.usage() != null ? response.usage().outputTokens() : 0;
        String usedModel = response.model() != null ? response.model() : model;

        log.info("LLM call: model={}, promptChars={}, responseChars={}, inputTokens={}, outputTokens={}, latencyMs={}",
                usedModel,
                systemPrompt.length() + userPrompt.length(),
                content.length(),
                inputTokens, outputTokens, latency);

        return new LlmResponse(content, usedModel, inputTokens, outputTokens, latency);
    }

    // ── JSON parse with single retry ──────────────────────────────────────

    @FunctionalInterface
    private interface JsonParser<T> {
        T parse(String raw) throws Exception;
    }

    private <T> T parseWithRetry(String systemPrompt, String userPrompt,
                                  int maxTokens, JsonParser<T> parser) {
        LlmResponse first = doCall(systemPrompt, userPrompt, maxTokens);
        try {
            return parser.parse(first.content());
        } catch (Exception e) {
            log.warn("JSON parse failed on first attempt: {}. Retrying with correction prompt.",
                    e.getMessage());
        }

        String retryUser = userPrompt +
                "\n\n[IMPORTANT: Your previous response could not be parsed as JSON. " +
                "Return ONLY valid JSON — no markdown, no code blocks, no explanation.]";
        LlmResponse retry = doCall(systemPrompt, retryUser, maxTokens);
        try {
            return parser.parse(retry.content());
        } catch (Exception e) {
            throw new RuntimeException(
                    "LLM returned invalid JSON after retry: " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static String jsonSystemPrompt(String base) {
        return base + "\n\nIMPORTANT: Respond ONLY with valid JSON. " +
                "No markdown, no code blocks, no explanation — pure JSON only.";
    }

    /** Package-visible for unit testing in PromptBuilderTest. */
    public static String extractJson(String text) {
        if (text == null) return "{}";
        String t = text.strip();
        if (t.startsWith("```")) {
            int newline = t.indexOf('\n');
            int closing = t.lastIndexOf("```");
            if (newline > 0 && closing > newline) {
                return t.substring(newline + 1, closing).strip();
            }
        }
        return t;
    }

    // ── Anthropic API DTOs (internal) ─────────────────────────────────────

    private record AnthropicRequest(
            String           model,
            @JsonProperty("max_tokens")  int    maxTokens,
            double           temperature,
            String           system,
            List<AnthropicMessage> messages
    ) {}

    private record AnthropicMessage(
            String role,
            String content
    ) {}

    private record AnthropicResponse(
            String id,
            String model,
            List<ContentBlock> content,
            AnthropicUsage usage
    ) {}

    private record ContentBlock(
            String type,
            String text
    ) {}

    private record AnthropicUsage(
            @JsonProperty("input_tokens")  int inputTokens,
            @JsonProperty("output_tokens") int outputTokens
    ) {}
}
