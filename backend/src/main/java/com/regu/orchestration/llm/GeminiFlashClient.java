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
 * {@link LlmClient} implementation backed by the Google Gemini API.
 * Uses gemini-2.5-flash (or similar) to provide extremely fast report generation
 * and RAG completions.
 */
@Service
public class GeminiFlashClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiFlashClient.class);

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final RestClient restClient;
    private final String     apiKey;
    private final String     model;
    private final double     temperature;
    private final int        maxRetries;

    public GeminiFlashClient(
            @Value("${regu.llm.api-key:}")          String  apiKey,
            @Value("${regu.llm.model:gemini-2.5-flash}") String  model,
            @Value("${regu.llm.temperature:0.1}")   double  temperature,
            @Value("${regu.llm.max-retries:2}")     int     maxRetries) {
        this.apiKey      = apiKey;
        this.model       = model;
        this.temperature = temperature;
        this.maxRetries  = maxRetries;
        
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("content-type", "application/json")
                .build();
    }

    // ── LlmClient interface ───────────────────────────────────────────────

    @Override
    public LlmResponse call(String systemPrompt, String userPrompt) {
        return doCall(systemPrompt, userPrompt, 4096, "text/plain");
    }

    @Override
    public LlmResponse call(String systemPrompt, String userPrompt, int maxTokens) {
        return doCall(systemPrompt, userPrompt, maxTokens, "text/plain");
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

    private LlmResponse doCall(String systemPrompt, String userPrompt, int maxTokens, String mimeType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not set.");
        }

        long t0 = System.currentTimeMillis();

        GeminiContent sysContent = new GeminiContent(List.of(new GeminiPart(systemPrompt)));
        GeminiContent userContent = new GeminiContent(List.of(new GeminiPart(userPrompt)));
        
        GeminiGenerationConfig config = new GeminiGenerationConfig(maxTokens, temperature, mimeType);
        
        GeminiRequest request = new GeminiRequest(
                sysContent,
                List.of(userContent),
                config
        );

        String uri = String.format("/v1beta/models/%s:generateContent?key=%s", model, apiKey);

        GeminiResponse response = restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GeminiResponse.class);

        long latency = System.currentTimeMillis() - t0;

        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new RuntimeException("Gemini API returned null or empty response");
        }

        String content = "";
        try {
            content = response.candidates().get(0).content().parts().get(0).text();
        } catch (Exception e) {
            log.warn("Could not extract text from Gemini response");
        }
        
        int inputTokens  = response.usageMetadata() != null ? response.usageMetadata().promptTokenCount()  : 0;
        int outputTokens = response.usageMetadata() != null ? response.usageMetadata().candidatesTokenCount() : 0;

        log.info("LLM call [Gemini]: model={}, responseChars={}, inputTokens={}, outputTokens={}, latencyMs={}",
                model, content.length(), inputTokens, outputTokens, latency);

        return new LlmResponse(content, model, inputTokens, outputTokens, latency);
    }

    // ── JSON parse with single retry ──────────────────────────────────────

    @FunctionalInterface
    private interface JsonParser<T> {
        T parse(String raw) throws Exception;
    }

    private <T> T parseWithRetry(String systemPrompt, String userPrompt,
                                  int maxTokens, JsonParser<T> parser) {
        // Enforce application/json for Gemini to ensure purely JSON outputs
        LlmResponse first = doCall(systemPrompt, userPrompt, maxTokens, "application/json");
        try {
            return parser.parse(first.content());
        } catch (Exception e) {
            log.warn("JSON parse failed on first attempt: {}. Retrying with correction prompt.",
                    e.getMessage());
        }

        String retryUser = userPrompt +
                "\n\n[IMPORTANT: Your previous response could not be parsed as JSON. " +
                "Return ONLY valid JSON.]";
        LlmResponse retry = doCall(systemPrompt, retryUser, maxTokens, "application/json");
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

    // ── Gemini API DTOs (internal) ────────────────────────────────────────

    private record GeminiPart(String text) {}
    
    private record GeminiContent(List<GeminiPart> parts) {}
    
    private record GeminiGenerationConfig(
            @JsonProperty("maxOutputTokens") int maxOutputTokens,
            double temperature,
            @JsonProperty("responseMimeType") String responseMimeType
    ) {}
    
    private record GeminiRequest(
            @JsonProperty("systemInstruction") GeminiContent systemInstruction,
            List<GeminiContent> contents,
            @JsonProperty("generationConfig") GeminiGenerationConfig generationConfig
    ) {}

    private record GeminiResponse(
            List<GeminiCandidate> candidates,
            @JsonProperty("usageMetadata") GeminiUsage usageMetadata
    ) {}

    private record GeminiCandidate(GeminiContent content) {}

    private record GeminiUsage(
            int promptTokenCount,
            int candidatesTokenCount,
            int totalTokenCount
    ) {}
}
