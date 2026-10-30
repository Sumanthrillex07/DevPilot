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
            log.info("Configuring OpenAiEmbeddingModel with text-embedding-3-small and resilient offline fallback");
            OpenAiApi openAiApi = new OpenAiApi(openAiApiKey);
            OpenAiEmbeddingModel openAiModel = new OpenAiEmbeddingModel(
                    openAiApi,
                    MetadataMode.EMBED,
                    OpenAiEmbeddingOptions.builder()
                            .model("text-embedding-3-small")
                            .build()
            );
            return new ResilientEmbeddingModel(openAiModel);
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
     * Resilient EmbeddingModel that falls back to deterministic vectors if OpenAI API fails (e.g. quota exhausted).
     */
    public static class ResilientEmbeddingModel implements EmbeddingModel {
        private final EmbeddingModel delegate;
        private final StubEmbeddingModel fallback = new StubEmbeddingModel();

        public ResilientEmbeddingModel(EmbeddingModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            try {
                return delegate.call(request);
            } catch (Exception ex) {
                log.warn("OpenAI embedding API failed ({}), falling back to deterministic local vectors: {}",
                        ex.getClass().getSimpleName(), ex.getMessage());
                return fallback.call(request);
            }
        }

        @Override
        public float[] embed(String text) {
            try {
                return delegate.embed(text);
            } catch (Exception ex) {
                log.warn("OpenAI embedding API failed ({}), falling back to deterministic local vectors: {}",
                        ex.getClass().getSimpleName(), ex.getMessage());
                return fallback.embed(text);
            }
        }

        @Override
        public float[] embed(org.springframework.ai.document.Document document) {
            try {
                return delegate.embed(document);
            } catch (Exception ex) {
                log.warn("OpenAI embedding API failed ({}), falling back to deterministic local vectors: {}",
                        ex.getClass().getSimpleName(), ex.getMessage());
                return fallback.embed(document);
            }
        }
    }

    /**
     * Stub EmbeddingModel returning deterministic 1536-dimension unit vectors for test/offline/quota-fallback use.
     */
    static class StubEmbeddingModel implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = request.getInstructions().stream()
                    .map(text -> new Embedding(deterministicVector(text), 0))
                    .toList();
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(String text) {
            return deterministicVector(text);
        }

        @Override
        public float[] embed(org.springframework.ai.document.Document document) {
            return deterministicVector(document != null ? document.getText() : "");
        }

        private static float[] deterministicVector(String text) {
            float[] vec = new float[1536];
            int h = (text != null && !text.isBlank()) ? text.hashCode() : 42;
            for (int i = 0; i < 1536; i++) {
                vec[i] = (float) Math.sin((h + i) * 0.17);
            }
            double norm = 0;
            for (float v : vec) norm += v * v;
            norm = Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < 1536; i++) vec[i] /= (float) norm;
            }
            return vec;
        }
    }
}
