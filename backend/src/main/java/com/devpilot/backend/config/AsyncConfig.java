package com.devpilot.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for asynchronous task execution.
 *
 * <p>Provides a dedicated {@link ThreadPoolTaskExecutor} named {@code indexingExecutor}
 * for background repository indexing tasks. Isolating indexing into its own bounded thread
 * pool prevents long-running indexing jobs from consuming standard web request threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "indexingExecutor")
    public Executor indexingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("indexing-");
        executor.initialize();
        return executor;
    }
}
