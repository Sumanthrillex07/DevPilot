package com.devpilot.backend.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AiServiceHealthIndicatorTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatModel chatModel;

    @Test
    @DisplayName("Health is UP when VectorStore and ChatModel are configured")
    void health_up() {
        AiServiceHealthIndicator indicator = new AiServiceHealthIndicator(vectorStore, chatModel);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("vectorStore", "active");
    }

    @Test
    @DisplayName("Health is DOWN when components are null")
    void health_down_whenComponentsMissing() {
        AiServiceHealthIndicator indicator = new AiServiceHealthIndicator(null, chatModel);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("vectorStoreReady", false);
    }
}
