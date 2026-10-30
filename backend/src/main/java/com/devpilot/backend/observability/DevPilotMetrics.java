package com.devpilot.backend.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Centralized Micrometer metrics collector for DevPilot.
 * Records latencies, throughput, and error rates across RAG retrieval, indexing,
 * and autonomous AI agents.
 */
@Component
public class DevPilotMetrics {

    private final MeterRegistry registry;

    public DevPilotMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Records latency and results of a RAG context retrieval similarity search.
     */
    public void recordRagRetrieval(long durationMs, int chunksRetrieved, boolean hasMatches) {
        Timer.builder("devpilot.rag.retrieval.duration")
                .description("Time taken to retrieve and filter semantic code context from pgvector")
                .tag("matched", String.valueOf(hasMatches))
                .register(registry)
                .record(Duration.ofMillis(durationMs));

        Counter.builder("devpilot.rag.retrieval.total")
                .description("Total number of RAG context retrieval requests")
                .tag("matched", String.valueOf(hasMatches))
                .register(registry)
                .increment();

        DistributionSummary.builder("devpilot.rag.chunks.retrieved")
                .description("Number of chunks retrieved per search")
                .register(registry)
                .record(chunksRetrieved);
    }

    /**
     * Records repository indexing execution metrics.
     */
    public void recordIndexing(long durationMs, int filesProcessed, int chunksCreated, boolean success) {
        Timer.builder("devpilot.indexing.duration")
                .description("Time taken to process, chunk, embed, and index a repository")
                .tag("status", success ? "success" : "failure")
                .register(registry)
                .record(Duration.ofMillis(durationMs));

        Counter.builder("devpilot.indexing.total")
                .description("Total indexing jobs executed")
                .tag("status", success ? "success" : "failure")
                .register(registry)
                .increment();

        Counter.builder("devpilot.indexing.files.processed")
                .description("Total code files chunked and processed during indexing")
                .register(registry)
                .increment(filesProcessed);

        Counter.builder("devpilot.indexing.chunks.created")
                .description("Total vector embeddings created during indexing")
                .register(registry)
                .increment(chunksCreated);
    }

    /**
     * Records execution time and success/failure count for an autonomous AI agent.
     *
     * @param agentType "chat", "bug_investigation", "pr_review", "patch_generation"
     */
    public void recordAgentExecution(String agentType, long durationMs, boolean success) {
        Timer.builder("devpilot.agent.execution.duration")
                .description("Duration of autonomous AI agent execution")
                .tag("agent", agentType)
                .tag("status", success ? "success" : "failure")
                .register(registry)
                .record(Duration.ofMillis(durationMs));

        Counter.builder("devpilot.agent.executions.total")
                .description("Total number of autonomous AI agent invocations")
                .tag("agent", agentType)
                .tag("status", success ? "success" : "failure")
                .register(registry)
                .increment();
    }
}
