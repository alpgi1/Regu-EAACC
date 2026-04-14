package com.regu.ingestion;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * HTTP client for the Voyage AI embeddings API.
 *
 * <p>Generates {@code vector(1024)} embeddings using the {@code voyage-3-large}
 * model with Matryoshka truncation to 1024 dimensions (from the model's native
 * 2048). Supports batch embedding to minimise API round-trips.
 *
 * <p>Only active under the {@code ingest} Spring profile. Requires
 * {@code VOYAGE_API_KEY} to be set in the environment.
 */
@Component
@Profile("ingest")
public class VoyageEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(VoyageEmbeddingClient.class);

    private static final String BASE_URL        = "https://api.voyageai.com";
    private static final String MODEL           = "voyage-3-large";
    private static final int    OUTPUT_DIM      = 1024;
    /** Voyage AI maximum texts per request for voyage-3-large. */
    private static final int    BATCH_SIZE      = 128;

    private final String     apiKey;
    private final RestClient restClient;

    public VoyageEmbeddingClient(
            @Value("${regu.ingestion.voyage-api-key}") String apiKey,
            RestClient.Builder builder) {
        this.apiKey      = apiKey;
        this.restClient  = builder.baseUrl(BASE_URL).build();
    }

    /**
     * Embeds a list of texts using Voyage AI {@code voyage-3-large} at
     * {@value OUTPUT_DIM} dimensions. Returns embeddings in input order.
     *
     * <p>Texts are sent in batches of up to {@value BATCH_SIZE} to respect
     * the Voyage API per-request limit.
     *
     * @param texts non-null, may be empty
     * @return embeddings in the same order as {@code texts}
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) return List.of();

        // Split into batches and accumulate results
        java.util.List<float[]> results = new java.util.ArrayList<>(texts.size());
        for (int offset = 0; offset < texts.size(); offset += BATCH_SIZE) {
            List<String> batch = texts.subList(offset, Math.min(offset + BATCH_SIZE, texts.size()));
            log.info("Requesting embeddings for {} texts (offset {}) from Voyage AI", batch.size(), offset);
            results.addAll(callApi(batch));
        }
        return results;
    }

    // ── Private ────────────────────────────────────────────────────────

    private List<float[]> callApi(List<String> texts) {
        EmbedResponse response = restClient.post()
                .uri("/v1/embeddings")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmbedRequest(texts, MODEL, OUTPUT_DIM))
                .retrieve()
                .body(EmbedResponse.class);

        if (response == null || response.data() == null) {
            throw new IllegalStateException("Voyage AI returned null response for batch of " + texts.size());
        }

        // Sort by index to guarantee order matches input, then extract
        return response.data().stream()
                .sorted((a, b) -> Integer.compare(a.index(), b.index()))
                .map(d -> toFloatArray(d.embedding()))
                .toList();
    }

    private static float[] toFloatArray(List<Double> doubles) {
        float[] arr = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); i++) {
            arr[i] = doubles.get(i).floatValue();
        }
        return arr;
    }

    // ── JSON request / response records ────────────────────────────────

    record EmbedRequest(
            List<String> input,
            String model,
            @JsonProperty("output_dimension") int outputDimension
    ) {}

    record EmbedData(
            List<Double> embedding,
            int index
    ) {}

    record EmbedResponse(
            List<EmbedData> data
    ) {}
}
