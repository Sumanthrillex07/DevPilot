package com.devpilot.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;

/**
 * Configuration for vector embeddings and storage.
 *
 * <p><b>Architecture & Strategy:</b>
 * <ul>
 *   <li><b>PostgreSQL + pgvector (Default):</b> In docker/production environments, we store
 *       1536-dimensional embeddings in the {@code vector_store} table using cosine similarity
 *       and HNSW indexing.</li>
 *   <li><b>Flyway Schema Management:</b> We set {@code initializeSchema(false)} so Spring AI
 *       does not attempt runtime DDL. Schema is managed reliably by Flyway.</li>
 *   <li><b>In-Memory Fallback:</b> When running tests or local development without OpenAI/pgvector,
 *       we provide an in-memory {@link SimpleVectorStore} and a stub {@link EmbeddingModel} so the
 *       application starts up smoothly without external network dependencies.</li>
 * </ul>
 */
@Configuration
public class VectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfig.class);

    @Value("${spring.ai.openai.api-key:${OPENAI_API_KEY:}}")
    private String openAiApiKey;

    @Value("${devpilot.vectorstore.type:pgvector}")
    private String vectorStoreType;

    @Bean
    public EmbeddingModel embeddingModel() {
        if (openAiApiKey != null && !openAiApiKey.isBlank() && !openAiApiKey.startsWith("sk-placeholder")) {
            log.info("Configuring OpenAiEmbeddingModel with text-embedding-3-small");
            OpenAiApi openAiApi = new OpenAiApi(openAiApiKey);
            return new OpenAiEmbeddingModel(
                    openAiApi,
                    MetadataMode.EMBED,
                    OpenAiEmbeddingOptions.builder()
                            .model("text-embedding-3-small")
                            .build()
            );
        }

        log.warn("No valid OPENAI_API_KEY found; falling back to stub EmbeddingModel for local/offline run.");
        return new StubEmbeddingModel();
    }

    @Bean
    @Primary
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        if ("pgvector".equalsIgnoreCase(vectorStoreType)) {
            try {
                log.info("Configuring PgVectorStore with HNSW cosine distance indexing");
                return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                        .dimensions(1536)
                        .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                        .indexType(PgVectorStore.PgIndexType.HNSW)
                        .initializeSchema(false)
                        .build();
            } catch (Exception ex) {
                log.warn("Failed to initialize PgVectorStore (falling back to SimpleVectorStore): {}", ex.getMessage());
            }
        }

        log.info("Configuring in-memory SimpleVectorStore");
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /**
     * Stub EmbeddingModel returning zero-filled 1536-dimension vectors for test/offline use.
     */
    static class StubEmbeddingModel implements EmbeddingModel {
        private static final float[] ZERO_VECTOR = new float[1536];

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = request.getInstructions().stream()
                    .map(text -> new Embedding(ZERO_VECTOR, 0))
                    .toList();
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(String text) {
            return ZERO_VECTOR;
        }

        @Override
        public float[] embed(org.springframework.ai.document.Document document) {
            return ZERO_VECTOR;
        }
    }
}
