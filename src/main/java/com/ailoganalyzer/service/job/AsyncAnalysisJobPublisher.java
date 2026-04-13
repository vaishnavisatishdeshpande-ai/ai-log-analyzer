package com.ailoganalyzer.service.job;

import com.ailoganalyzer.entity.Log;
import com.ailoganalyzer.service.LogService;
import com.ailoganalyzer.service.worker.AnalysisWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Async implementation of AnalysisJobPublisher using Spring's @Async.
 *
 * This is a simple in-memory implementation suitable for:
 * - Development environments
 * - Single-instance deployments
 * - Initial scalability testing
 *
 * Can be replaced with Kafka/RabbitMQ without changing business logic.
 * The worker layer remains the same, only the publisher changes.
 */
@Service
public class AsyncAnalysisJobPublisher implements AnalysisJobPublisher {

    private static final Logger logger = LoggerFactory.getLogger(AsyncAnalysisJobPublisher.class);

    private final AnalysisWorker analysisWorker;
    private final LogService logService;

    public AsyncAnalysisJobPublisher(AnalysisWorker analysisWorker, LogService logService) {
        this.analysisWorker = analysisWorker;
        this.logService = logService;
    }

    /**
     * Submits a log for asynchronous analysis.
     * Returns immediately to caller without blocking.
     *
     * @param logId the ID of the log to analyze
     */
    @Async("analysisExecutor")
    @Override
    public void publish(Long logId) {
        try {
            Log log = logService.getLogById(logId);
            logger.info("Processing analysis job for logId={}", logId);
            analysisWorker.analyzeLog(log);
            logger.info("Analysis job completed for logId={}", logId);
        } catch (Exception e) {
            logger.error("Analysis job failed for logId={}", logId, e);
        }
    }
}

