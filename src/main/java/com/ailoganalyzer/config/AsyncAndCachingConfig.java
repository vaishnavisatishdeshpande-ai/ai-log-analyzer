/**
 * Configuration for asynchronous processing, caching, and resilience.
 *
 * Enables:
 * - Spring async @Async processing for job publishing
 * - Spring Cache abstraction for result caching
 * - Thread pool for background analysis jobs
 */
package com.ailoganalyzer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.cache.annotation.EnableCaching;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableCaching
public class AsyncAndCachingConfig {

    /**
     * Configures thread pool for analysis jobs.
     *
     * Design:
     * - corePoolSize: minimum threads always available
     * - maxPoolSize: maximum threads for peak loads
     * - queueCapacity: backlog queue before rejecting
     * - threadNamePrefix: for debugging
     *
     * Tuning for production:
     * - Core = CPU cores (can handle sustained load)
     * - Max = CPU cores * 2 (for brief spikes)
     * - Queue = between core and max
     */
    @Bean(name = "analysisExecutor")
    public Executor analysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);              // Baseline threads
        executor.setMaxPoolSize(16);              // Max for peak loads
        executor.setQueueCapacity(50);            // Backlog queue
        executor.setThreadNamePrefix("analysis-"); // For monitoring
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}

