package com.devpilot.backend.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DevPilotMetricsTest {

    private MeterRegistry registry;
    private DevPilotMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new DevPilotMetrics(registry);
    }

    @Test
    @DisplayName("Records RAG retrieval metrics with tags")
    void recordRagRetrieval() {
        metrics.recordRagRetrieval(150, 4, true);

        assertThat(registry.find("devpilot.rag.retrieval.duration").timer()).isNotNull();
        assertThat(registry.find("devpilot.rag.retrieval.duration").tag("matched", "true").timer().count()).isEqualTo(1);

        assertThat(registry.find("devpilot.rag.retrieval.total").tag("matched", "true").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("devpilot.rag.chunks.retrieved").summary().totalAmount()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("Records indexing metrics with file and chunk counts")
    void recordIndexing() {
        metrics.recordIndexing(2500, 12, 48, true);

        assertThat(registry.find("devpilot.indexing.duration").tag("status", "success").timer().count()).isEqualTo(1);
        assertThat(registry.find("devpilot.indexing.total").tag("status", "success").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("devpilot.indexing.files.processed").counter().count()).isEqualTo(12.0);
        assertThat(registry.find("devpilot.indexing.chunks.created").counter().count()).isEqualTo(48.0);
    }

    @Test
    @DisplayName("Records autonomous AI agent execution metrics")
    void recordAgentExecution() {
        metrics.recordAgentExecution("patch_generation", 1200, true);
        metrics.recordAgentExecution("patch_generation", 800, false);

        assertThat(registry.find("devpilot.agent.execution.duration").tag("agent", "patch_generation").timers()).hasSize(2);
        assertThat(registry.find("devpilot.agent.executions.total").tag("agent", "patch_generation").tag("status", "success").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("devpilot.agent.executions.total").tag("agent", "patch_generation").tag("status", "failure").counter().count()).isEqualTo(1.0);
    }
}
