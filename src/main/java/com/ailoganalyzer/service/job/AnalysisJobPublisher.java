package com.ailoganalyzer.service.job;

/**
 * Interface for publishing analysis jobs to a processing queue.
 *
 * Designed to be easily replaced with Kafka/RabbitMQ in the future
 * while maintaining the same business logic.
 *
 * Current implementation uses async @Async for in-process handling.
 */
public interface AnalysisJobPublisher {
    /**
     * Submit a log ID for asynchronous analysis.
     *
     * Returns immediately without waiting for analysis to complete.
     * The analysis job will be processed by a worker thread.
     *
     * @param logId the ID of the log to analyze
     */
    void publish(Long logId);
}

