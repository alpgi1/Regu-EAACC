package com.regu.retrieval;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Voyage AI embedding client scoped to the retrieval layer.
 *
 * <p>Unlike {@code VoyageEmbeddingClient} in the ingestion package, this bean
 * is NOT profile-gated so it is available during normal application startup.
 * It is used to embed user queries at retrieval time (single text, not batched).
 *
 * <p>Requires {@code VOYAGE_API_KEY} to be set. When the key is absent
 * (empty string) the client will fail fast at query time with a descriptive
 * exception — it does not silently return zero-vectors.
 *
 * <p>Uses the same model ({@code voyage-3-large}) and dimension (1024) as the
 * ingestion pipeline so query vectors are in the same space as stored embeddings.
 */
@Component
public class QueryEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(QueryEmbeddingClient.class);

    private static final String BASE_URL   = "https://api.voyageai.com";
    private static final String MODEL      = "voyage-3-large";
    private static final int    OUTPUT_DIM = 1024;

    private final String     apiKey;
    private final RestClient restClient;

    public QueryEmbeddingClient(
            @Value("${regu.ingestion.voyage-api-key}") String apiKey) {
        this.apiKey     = apiKey;
        this.restClient = RestClient.builder().baseUrl(BASE_URL).build();
    }

    /**
     * Embeds a single query text and returns the embedding as a pgvector
     * text literal ({@code [f0,f1,...,f1023]}) ready for use in SQL
     * {@code :param::vector} casts.
     *
     * @param queryText the user's natural-language query
     * @return pgvector-formatted embedding string
     * @throws IllegalStateException if the API key is missing or if Voyage returns null
     */
    public String embedQueryForSql(String queryText) {
        float[] embedding = embedQuery(queryText);
        return formatVector(embedding);
    }

    /**
     * Embeds a single query text and returns the raw float array.
     *
     * @param queryText the user's natural-language query
     * @return 1024-dimensional embedding
     */
    public float[] embedQuery(String queryText) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "VOYAGE_API_KEY is not set — query embedding is unavailable. " +
                    "Set the VOYAGE_API_KEY environment variable before starting the server.");
        }
        log.debug("Embedding query via Voyage AI: [{}]", queryText);
        long t0 = System.currentTimeMillis();

        EmbedResponse response = restClient.post()
                .uri("/v1/embeddings")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmbedRequest(List.of(queryText), MODEL, OUTPUT_DIM))
                .retrieve()
                .body(EmbedResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("Voyage AI returned null/empty embedding for query");
        }

        float[] embedding = toFloatArray(response.data().getFirst().embedding());
        log.debug("Query embedding generated in {}ms", System.currentTimeMillis() - t0);
        return embedding;
    }

    // ── pgvector formatting ──────────────────────────────────────────────

    /**
     * Formats a float array as a pgvector text literal: {@code [f0,f1,...,fn-1]}.
     */
    public static String formatVector(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    // ── JSON records ─────────────────────────────────────────────────────

    private static float[] toFloatArray(List<Double> doubles) {
        float[] arr = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); i++) arr[i] = doubles.get(i).floatValue();
        return arr;
    }

    record EmbedRequest(
            List<String> input,
            String model,
            @JsonProperty("output_dimension") int outputDimension
    ) {}

    record EmbedData(List<Double> embedding, int index) {}

    record EmbedResponse(List<EmbedData> data) {}
}
