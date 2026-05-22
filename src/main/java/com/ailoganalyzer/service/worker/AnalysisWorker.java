package com.ailoganalyzer.service.worker;

import com.ailoganalyzer.ai.AiLogAnalysisService;
import com.ailoganalyzer.entity.Log;
import com.ailoganalyzer.entity.LogAnalysis;
import com.ailoganalyzer.enums.AnalysisSource;
import com.ailoganalyzer.enums.AnalysisStatus;
import com.ailoganalyzer.enums.Severity;
import com.ailoganalyzer.repository.LogAnalysisRepository;
import com.ailoganalyzer.service.metrics.AnalysisMetricsRecorder;
import com.ailoganalyzer.service.severity.SeverityOrchestrator;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Worker service that processes analysis jobs asynchronously.
 *
 * Handles:
 * - AI analysis with resilience
 * - Rule engine fallback
 * - Workflow lifecycle management
 * - Metrics and observability
 */
@Service
public class AnalysisWorker {

    private static final Logger logger =
            LoggerFactory.getLogger(AnalysisWorker.class);

    private final AiLogAnalysisService aiService;
    private final LogAnalysisRepository repository;
    private final SeverityOrchestrator severityOrchestrator;
    private final RuleEngineWorker ruleEngineWorker;
    private final AnalysisMetricsRecorder metricsRecorder;

    public AnalysisWorker(
            AiLogAnalysisService aiService,
            LogAnalysisRepository repository,
            SeverityOrchestrator severityOrchestrator,
            RuleEngineWorker ruleEngineWorker,
            AnalysisMetricsRecorder metricsRecorder
    ) {

        this.aiService = aiService;
        this.repository = repository;
        this.severityOrchestrator = severityOrchestrator;
        this.ruleEngineWorker = ruleEngineWorker;
        this.metricsRecorder = metricsRecorder;
    }

    /**
     * Main workflow lifecycle.
     */
    public LogAnalysis analyzeLog(Log log) {

        if (log == null || log.getId() == null) {

            logger.error("Cannot analyze null log or log without ID");

            metricsRecorder.recordFailure();

            throw new IllegalArgumentException(
                    "Log and log ID cannot be null"
            );
        }

        // Reuse existing pending analysis
        LogAnalysis analysis = repository
                .findByLogIdOrderByCreatedAtDesc(log.getId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No pending analysis found for logId="
                                        + log.getId()
                        ));

        // Ensure correlation ID exists
        if (analysis.getCorrelationId() == null) {
            analysis.setCorrelationId(
                    UUID.randomUUID().toString()
            );
        }

        logger.debug(
                "Loaded analysis. analysisId={}, correlationId={}, logId={}",
                analysis.getId(),
                analysis.getCorrelationId(),
                log.getId()
        );

        try {

            // Transition to PROCESSING
            analysis.setStatus(AnalysisStatus.PROCESSING);

            analysis = repository.save(analysis);

            logger.debug(
                    "Transitioned to PROCESSING. analysisId={}, correlationId={}, logId={}",
                    analysis.getId(),
                    analysis.getCorrelationId(),
                    log.getId()
            );

            // AI analysis path
            try {

                LogAnalysis aiResult = performAiAnalysis(log);

                if (aiResult != null) {

                    logger.info(
                            "AI analysis succeeded. correlationId={}, logId={}",
                            analysis.getCorrelationId(),
                            log.getId()
                    );

                    // Update SAME entity
                    analysis.setAnalysis(aiResult.getAnalysis());
                    analysis.setPossibleFix(aiResult.getPossibleFix());
                    analysis.setSeverity(aiResult.getSeverity());
                    analysis.setConfidence(aiResult.getConfidence());
                    analysis.setSource(aiResult.getSource());

                    analysis.setStatus(AnalysisStatus.COMPLETED);
                    analysis.setCompletedAt(LocalDateTime.now());

                    analysis = repository.save(analysis);

                    metricsRecorder.recordSuccess();
                    metricsRecorder.recordAiAnalysis();

                    if (analysis.getSeverity() != null) {

                        metricsRecorder.recordSeverity(
                                analysis.getSeverity().toString()
                        );
                    }

                    logger.info(
                            "Analysis completed successfully via AI. analysisId={}, correlationId={}, severity={}, logId={}",
                            analysis.getId(),
                            analysis.getCorrelationId(),
                            analysis.getSeverity(),
                            log.getId()
                    );

                    return analysis;
                }

            } catch (Exception e) {

                logger.warn(
                        "AI analysis failed for correlationId={}, logId={}. Falling back to rule engine.",
                        analysis.getCorrelationId(),
                        log.getId(),
                        e
                );
            }

            // Fallback path
            logger.info(
                    "Falling back to rule engine. correlationId={}, logId={}",
                    analysis.getCorrelationId(),
                    log.getId()
            );

            return handleRuleEngineAnalysis(
                    analysis,
                    log
            );

        } catch (Exception e) {

            logger.error(
                    "Unexpected error during analysis. correlationId={}, logId={}",
                    analysis.getCorrelationId(),
                    log.getId(),
                    e
            );

            metricsRecorder.recordFailure();

            return handleAnalysisFailure(
                    analysis,
                    e
            );
        }
    }

    /**
     * Retry → RateLimiter → CircuitBreaker
     */
    @Retry(name = "aiAnalysisRetry")
    @RateLimiter(name = "aiAnalysisLimiter")
    @CircuitBreaker(
            name = "aiAnalysisCircuitBreaker",
            fallbackMethod = "fallbackAiAnalysis"
    )
    private LogAnalysis performAiAnalysis(Log log) {

        return aiService.analyzeLog(log);
    }

    /**
     * Circuit breaker fallback.
     */
    private LogAnalysis fallbackAiAnalysis(
            Log log,
            Exception e
    ) {

        logger.warn(
                "AI service unavailable. Using rule engine fallback. logId={}",
                log.getId(),
                e
        );

        return null;
    }

    /**
     * Rule engine fallback path.
     */
    private LogAnalysis handleRuleEngineAnalysis(
            LogAnalysis analysis,
            Log log
    ) {

        if (analysis == null || analysis.getId() == null) {

            logger.error(
                    "Cannot handle rule engine analysis. Invalid entity."
            );

            throw new IllegalStateException(
                    "LogAnalysis entity must exist before rule engine analysis"
            );
        }

        Timer.Sample sample =
                metricsRecorder.recordRuleLatency();

        try {

            logger.debug(
                    "Starting rule engine analysis. analysisId={}, logId={}",
                    analysis.getId(),
                    log.getId()
            );

            LogAnalysis ruleResult =
                    ruleEngineWorker.analyze(log);

            if (ruleResult == null) {

                throw new IllegalStateException(
                        "Rule engine returned null"
                );
            }

            // Update SAME entity
            analysis.setAnalysis(ruleResult.getAnalysis());
            analysis.setPossibleFix(ruleResult.getPossibleFix());
            analysis.setSeverity(ruleResult.getSeverity());
            analysis.setConfidence(ruleResult.getConfidence());

            analysis.setSource(AnalysisSource.RULE);

            analysis.setStatus(AnalysisStatus.COMPLETED);

            analysis.setCompletedAt(LocalDateTime.now());

            analysis = repository.save(analysis);

            logger.info(
                    "Rule engine analysis completed. analysisId={}, severity={}, logId={}",
                    analysis.getId(),
                    analysis.getSeverity(),
                    log.getId()
            );

            metricsRecorder.recordSuccess();
            metricsRecorder.recordRuleAnalysis();

            if (analysis.getSeverity() != null) {

                metricsRecorder.recordSeverity(
                        analysis.getSeverity().toString()
                );
            }

            return analysis;

        } catch (Exception e) {

            logger.error(
                    "Rule engine analysis failed for logId={}",
                    log.getId(),
                    e
            );

            metricsRecorder.recordFailure();

            return handleAnalysisFailure(
                    analysis,
                    e
            );

        } finally {

            metricsRecorder.stopRuleLatency(sample);
        }
    }

    /**
     * Final failure state.
     */
    private LogAnalysis handleAnalysisFailure(
            LogAnalysis analysis,
            Exception e
    ) {

        if (analysis == null || analysis.getId() == null) {

            throw new IllegalStateException(
                    "Analysis entity missing during failure handling"
            );
        }

        logger.error(
                "Marking analysis as FAILED. analysisId={}, reason={}",
                analysis.getId(),
                e.getMessage(),
                e
        );

        analysis.setStatus(AnalysisStatus.FAILED);

        analysis.setSource(AnalysisSource.RULE);

        analysis.setSeverity(Severity.LOW);

        analysis.setConfidence(0.5);

        analysis.setAnalysis(
                "Analysis could not be completed due to system error"
        );

        analysis.setPossibleFix(
                "Please check system logs and try again later"
        );

        analysis.setCompletedAt(LocalDateTime.now());

        analysis = repository.save(analysis);

        logger.warn(
                "Analysis marked FAILED. analysisId={}",
                analysis.getId()
        );

        metricsRecorder.recordFailure();

        return analysis;
    }
}