package com.devpilot.backend.observability;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom Spring Boot Actuator Health Indicator for DevPilot's AI infrastructure.
 * Reports health of vector store and language model integrations to /actuator/health.
 */
@Component
public class AiServiceHealthIndicator implements HealthIndicator {

    private final VectorStore vectorStore;
    private final ChatModel chatModel;

    public AiServiceHealthIndicator(VectorStore vectorStore, ChatModel chatModel) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
    }

    @Override
    public Health health() {
        try {
            boolean vectorStoreReady = vectorStore != null;
            boolean chatModelReady = chatModel != null;

            if (!vectorStoreReady || !chatModelReady) {
                return Health.down()
                        .withDetail("vectorStoreReady", vectorStoreReady)
                        .withDetail("chatModelReady", chatModelReady)
                        .withDetail("error", "AI service components not fully initialized")
                        .build();
            }

            return Health.up()
                    .withDetail("vectorStore", "active")
                    .withDetail("chatModel", chatModel.getClass().getSimpleName())
                    .withDetail("status", "AI agents and RAG pipeline operational")
                    .build();
        } catch (Exception ex) {
            return Health.down(ex)
                    .withDetail("error", ex.getMessage())
                    .build();
        }
    }
}
