package com.ailoganalyzer.service;

import com.ailoganalyzer.ai.AiLogAnalysisService;
import com.ailoganalyzer.constant.AnalysisConstants;
import com.ailoganalyzer.entity.Log;
import com.ailoganalyzer.entity.LogAnalysis;
import com.ailoganalyzer.enums.AnalysisSource;
import com.ailoganalyzer.enums.AnalysisStatus;
import com.ailoganalyzer.enums.Severity;
import com.ailoganalyzer.exception.AnalysisException;
import com.ailoganalyzer.repository.LogAnalysisRepository;
import com.ailoganalyzer.service.severity.SeverityOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for managing log analysis operations.
 *
 * Coordinates:
 * - Creating pending analysis records (for async flow)
 * - Retrieving analysis status
 * - Synchronous analysis (for backwards compatibility)
 */
@Service
public class AnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisService.class);

    private final AiLogAnalysisService aiService;
    private final LogAnalysisRepository repository;
    private final SeverityOrchestrator severityOrchestrator;

    public AnalysisService(AiLogAnalysisService aiService, LogAnalysisRepository repository, SeverityOrchestrator severityOrchestrator) {
        this.aiService = aiService;
        this.repository = repository;
        this.severityOrchestrator = severityOrchestrator;
    }

    /**
     * Creates a pending analysis record for a log.
     * Used in async flow when submitting jobs.
     *
     * @param log the log to analyze
     * @return LogAnalysis with PENDING status
     */
    public LogAnalysis createPendingAnalysis(Log log) {
        LogAnalysis analysis = new LogAnalysis();
        analysis.setLog(log);
        analysis.setStatus(AnalysisStatus.PENDING);
        analysis.setCorrelationId(UUID.randomUUID().toString());
        return repository.save(analysis);
    }

    /**
     * Gets the latest analysis for a log.
     * Returns the most recently created analysis record.
     *
     * @param logId the ID of the log
     * @return LogAnalysis with current status
     * @throws RuntimeException if no analysis found
     */
    public LogAnalysis getLatestAnalysis(Long logId) {
        return repository.findByLogIdOrderByCreatedAtDesc(logId)
                .orElseThrow(() -> new RuntimeException("No analysis found for log " + logId));
    }
    public LogAnalysis getAnalysis(Long logId) {
        return repository
                .findTopByLogIdOrderByCreatedAtDesc(logId)
                .orElseThrow(() -> new AnalysisException("No analysis found for logId: " + logId));
    }

    /**
     * Synchronous analysis for backwards compatibility.
     * Blocks until analysis is complete.
     *
     * DO NOT use in async flow. Use jobPublisher.publish() instead.
     *
     * @param log the log to analyze
     * @return LogAnalysis with COMPLETED status
     */
    public LogAnalysis  analyzeLog(Log log) {
        try {
            var result = aiService.analyzeLog(log);
            severityOrchestrator.enrichWithHybridSeverity(result, log);
            return repository.save(result);
        } catch (Exception e) {
            logger.warn("AI analysis failed, falling back to rule engine. logId={}", log.getId());
            var fallback = createFallbackAnalysis(log);
            return repository.save(fallback);
        }
    }

    private LogAnalysis createFallbackAnalysis(Log log) {
        var message = log.getMessage().toLowerCase();
        var analysis = new LogAnalysis();
        analysis.setLog(log);
        analysis.setSource(AnalysisSource.RULE);

        if (message.contains("outofmemory") || message.contains("crash")) {
            analysis.setSeverity(Severity.CRITICAL);
            analysis.setConfidence(0.9);
            analysis.setAnalysis(AnalysisConstants.CRITICAL_MEMORY_ANALYSIS);
            analysis.setPossibleFix(AnalysisConstants.CRITICAL_MEMORY_FIX);
        } else if (message.contains("timeout")) {
            analysis.setSeverity(Severity.HIGH);
            analysis.setConfidence(0.8);
            analysis.setAnalysis(AnalysisConstants.TIMEOUT_ANALYSIS);
            analysis.setPossibleFix(AnalysisConstants.TIMEOUT_FIX);
        } else {
            analysis.setSeverity(Severity.LOW);
            analysis.setConfidence(0.6);
            analysis.setAnalysis(AnalysisConstants.LOW_SEVERITY_ANALYSIS);
            analysis.setPossibleFix(AnalysisConstants.LOW_SEVERITY_FIX);
        }

        return analysis;
    }
}
