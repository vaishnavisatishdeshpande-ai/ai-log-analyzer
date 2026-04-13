    package com.ailoganalyzer.service.worker;

    import com.ailoganalyzer.ai.AiLogAnalysisService;
    import com.ailoganalyzer.entity.Log;
    import com.ailoganalyzer.entity.LogAnalysis;
    import com.ailoganalyzer.enums.AnalysisSource;
    import com.ailoganalyzer.enums.AnalysisStatus;
    import com.ailoganalyzer.enums.Severity;
    import com.ailoganalyzer.constant.AnalysisConstants;
    import com.ailoganalyzer.repository.LogAnalysisRepository;
    import com.ailoganalyzer.service.severity.SeverityOrchestrator;
    import com.ailoganalyzer.service.metrics.AnalysisMetricsRecorder;
    import io.micrometer.core.instrument.Timer;
    import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
    import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    import org.springframework.stereotype.Service;
    import java.time.LocalDateTime;

    /**
     * Worker service that processes analysis jobs asynchronously.
     *
     * Handles:
     * - AI analysis with resilience (rate limiting, circuit breaker)
     * - Fallback to rule engine on AI failures
     * - Status tracking throughout the analysis lifecycle
     * - Metrics and observability
     */
    @Service
    public class AnalysisWorker {

        private static final Logger logger = LoggerFactory.getLogger(AnalysisWorker.class);

        private final AiLogAnalysisService aiService;
        private final LogAnalysisRepository repository;
        private final SeverityOrchestrator severityOrchestrator;
        private final RuleEngineWorker ruleEngineWorker;
        private final AnalysisMetricsRecorder metricsRecorder;

        public AnalysisWorker(AiLogAnalysisService aiService,
                             LogAnalysisRepository repository,
                             SeverityOrchestrator severityOrchestrator,
                             RuleEngineWorker ruleEngineWorker,
                             AnalysisMetricsRecorder metricsRecorder) {
            this.aiService = aiService;
            this.repository = repository;
            this.severityOrchestrator = severityOrchestrator;
            this.ruleEngineWorker = ruleEngineWorker;
            this.metricsRecorder = metricsRecorder;
        }

    /**
     * Analyzes a log with AI and fallback to rule engine.
     *
     * Key Design Principle:
     * - ONE LogAnalysis entity is created and persisted
     * - Entity lifecycle: PENDING → PROCESSING → COMPLETED/FAILED
     * - All updates modify the SAME entity (no duplicates)
     *
     * Flow:
     * 1. Create entity with PENDING status, save to DB
     * 2. Update status to PROCESSING, save to DB
     * 3. Try AI analysis (returns data only, no DB save)
     * 4. If AI succeeds: copy data to entity, mark COMPLETED, save
     * 5. If AI fails: try rule engine with SAME entity
     * 6. Rule engine updates SAME entity, mark COMPLETED, save
     * 7. If both fail: mark FAILED, save
     *
     * @param log the log to analyze
     * @return the completed LogAnalysis (single row per request)
     */
    public LogAnalysis analyzeLog(Log log) {
        if (log == null || log.getId() == null) {
            logger.error("Cannot analyze null log or log without ID");
            metricsRecorder.recordFailure();
            throw new IllegalArgumentException("Log and log ID cannot be null");
        }

        // ✅ STEP 1: Create ONE LogAnalysis entity - this becomes the single source of truth
        LogAnalysis analysis = new LogAnalysis();
        analysis.setLog(log);
        analysis.setStatus(AnalysisStatus.PENDING);

        analysis = repository.save(analysis);
        logger.debug("Created analysis record with PENDING status. analysisId={}, logId={}", analysis.getId(), log.getId());

        try {
            // ✅ STEP 2: Transition to PROCESSING
            analysis.setStatus(AnalysisStatus.PROCESSING);
            analysis = repository.save(analysis);
            logger.debug("Transitioned to PROCESSING. analysisId={}, logId={}", analysis.getId(), log.getId());

            // ✅ STEP 3: Try AI analysis
            try {
                LogAnalysis aiResult = performAiAnalysis(log);

                if (aiResult != null) {
                    // ✅ STEP 4a: AI succeeded - UPDATE EXISTING ENTITY (NOT REPLACE)
                    logger.info("AI analysis succeeded. logId={}", log.getId());

                    // Copy AI results into existing entity
                    analysis.setAnalysis(aiResult.getAnalysis());
                    analysis.setPossibleFix(aiResult.getPossibleFix());
                    analysis.setSeverity(aiResult.getSeverity());
                    analysis.setConfidence(aiResult.getConfidence());
                    analysis.setSource(aiResult.getSource());  // Will be AI or hybrid from orchestrator
                    analysis.setStatus(AnalysisStatus.COMPLETED);
                    analysis.setCompletedAt(LocalDateTime.now());

                    // Save SAME entity with completed data
                    analysis = repository.save(analysis);

                    // Record metrics
                    metricsRecorder.recordSuccess();
                    metricsRecorder.recordAiAnalysis();
                    if (analysis.getSeverity() != null) {
                        metricsRecorder.recordSeverity(analysis.getSeverity().toString());
                    }

                    logger.info("Analysis completed successfully via AI. analysisId={}, severity={}, logId={}",
                        analysis.getId(), analysis.getSeverity(), log.getId());
                    return analysis;
                }

            } catch (Exception e) {
                logger.warn("AI analysis failed for logId={}. Will attempt rule engine fallback.", log.getId(), e);
            }

            // ✅ STEP 4b: AI failed - fallback to rule engine with EXISTING entity
            logger.info("Falling back to rule engine. logId={}", log.getId());
            return handleRuleEngineAnalysis(analysis, log);

        } catch (Exception e) {
            logger.error("Unexpected error during analysis. logId={}", log.getId(), e);
            metricsRecorder.recordFailure();

            // ✅ Final fallback: mark existing entity as FAILED
            return handleAnalysisFailure(analysis, e);
        }
    }

        /**
         * Performs AI analysis with resilience patterns:
         * - Rate limiting to prevent overwhelming AI service
         * - Circuit breaker to fail fast if AI service is degraded
         */
        @RateLimiter(name = "aiAnalysisLimiter")
        @CircuitBreaker(name = "aiAnalysisCircuitBreaker", fallbackMethod = "fallbackAiAnalysis")
        private LogAnalysis performAiAnalysis(Log log) {
            Timer.Sample sample = metricsRecorder.recordAiLatency();
            try {
                var result = aiService.analyzeLog(log);
                severityOrchestrator.enrichWithHybridSeverity(result, log);
                return result;
            } finally {
                metricsRecorder.stopAiLatency(sample);
            }
        }

        /**
         * Fallback method for circuit breaker when AI service fails repeatedly
         */
        private LogAnalysis fallbackAiAnalysis(Log log, Exception e) {
            logger.warn("AI service circuit breaker open or request failed, using rule engine. logId={}", log.getId());
            return null; // Signal to use rule engine in analyzeLog
        }

    /**
     * Handles analysis using the optimized rule engine.
     *
     * Key Design:
     * - Accepts EXISTING LogAnalysis entity (created in analyzeLog)
     * - Rule engine worker returns analysis data only (creates NO entities)
     * - Copies rule results into EXISTING entity
     * - Saves and returns SAME entity (no duplication)
     *
     * @param analysis the existing LogAnalysis entity to update
     * @param log the log to analyze
     * @return the updated LogAnalysis entity (same object)
     */
    private LogAnalysis handleRuleEngineAnalysis(LogAnalysis analysis, Log log) {
        if (analysis == null || analysis.getId() == null) {
            logger.error("Cannot handle rule engine analysis: LogAnalysis entity is null or has no ID. logId={}",
                log != null ? log.getId() : "unknown");
            throw new IllegalStateException("LogAnalysis entity must exist before rule engine analysis");
        }

        Timer.Sample sample = metricsRecorder.recordRuleLatency();

        try {
            logger.debug("Starting rule engine analysis. analysisId={}, logId={}", analysis.getId(), log.getId());

            // Rule engine worker returns analysis data only (does NOT save to DB)
            LogAnalysis ruleResult = ruleEngineWorker.analyze(log);

            if (ruleResult == null) {
                logger.warn("Rule engine returned null result. logId={}", log.getId());
                throw new IllegalStateException("Rule engine analysis returned null");
            }

            // ✅ IMPORTANT: Update EXISTING entity instead of creating new one
            // This ensures only ONE row in the database per analysis request
            analysis.setAnalysis(ruleResult.getAnalysis());
            analysis.setPossibleFix(ruleResult.getPossibleFix());
            analysis.setSeverity(ruleResult.getSeverity());
            analysis.setConfidence(ruleResult.getConfidence());
            analysis.setSource(AnalysisSource.RULE);  // Mark as rule-based
            analysis.setStatus(AnalysisStatus.COMPLETED);
            analysis.setCompletedAt(LocalDateTime.now());

            // Save SAME entity with rule results
            analysis = repository.save(analysis);
            logger.info("Rule engine analysis completed. analysisId={}, severity={}, logId={}",
                analysis.getId(), analysis.getSeverity(), log.getId());

            // Record metrics
            metricsRecorder.recordSuccess();
            metricsRecorder.recordRuleAnalysis();
            if (analysis.getSeverity() != null) {
                metricsRecorder.recordSeverity(analysis.getSeverity().toString());
            }

            return analysis;

        } catch (Exception e) {
            logger.error("Rule engine analysis failed for logId={}. Will mark as FAILED.", log.getId(), e);
            metricsRecorder.recordFailure();

            // Call the overloaded method with existing entity
            return handleAnalysisFailure(analysis, e);

        } finally {
            metricsRecorder.stopRuleLatency(sample);
        }
    }
    /**
     * Handles final fallback when both AI and rule engine fail.
     *
     * This method has TWO overloads for different scenarios:
     * 1. handleAnalysisFailure(Log log, Exception e) - when no entity was created
     * 2. handleAnalysisFailure(LogAnalysis analysis, Exception e) - when entity exists
     *
     * Key Design:
     * - If entity exists (from analyzeLog), UPDATE IT to FAILED
     * - If entity doesn't exist (error before creation), CREATE ONE with FAILED status
     * - Either way, ONE row is persisted per analysis attempt
     */

    /**
     * Fallback for when an existing LogAnalysis entity needs to be marked as FAILED
     *
     * @param analysis existing LogAnalysis entity
     * @param e the exception that caused the failure
     * @return the updated LogAnalysis entity with FAILED status
     */
    private LogAnalysis handleAnalysisFailure(LogAnalysis analysis, Exception e) {
        if (analysis == null || analysis.getId() == null) {
            logger.error("Cannot mark analysis as failed: entity is null or has no ID", e);
            throw new IllegalStateException("LogAnalysis entity must have an ID to update status");
        }

        logger.error("Marking analysis as FAILED. analysisId={}, reason={}", analysis.getId(), e.getMessage(), e);

        // ✅ UPDATE EXISTING entity (don't create new)
        analysis.setStatus(AnalysisStatus.FAILED);
        analysis.setSource(AnalysisSource.RULE);  // Indicate fallback source
        analysis.setSeverity(Severity.LOW);  // Conservative default on failure
        analysis.setConfidence(0.5);

        String errorMsg = "Analysis could not be completed due to system error";
        if (e != null && e.getMessage() != null) {
            errorMsg += ": " + e.getMessage();
        }

        analysis.setAnalysis(errorMsg);
        analysis.setPossibleFix("Please check system logs and try again later");
        analysis.setCompletedAt(LocalDateTime.now());

        analysis = repository.save(analysis);
        logger.warn("Analysis marked FAILED and persisted. analysisId={}", analysis.getId());

        metricsRecorder.recordFailure();
        return analysis;
    }

    /**
     * Fallback for when NO LogAnalysis entity was created before failure occurred.
     * Creates a new entity with FAILED status to track the failure.
     *
     * This should be rare - it's a safety net for unexpected errors early in the flow.
     *
     * @param log the log that failed analysis
     * @param e the exception that caused the failure
     * @return a new LogAnalysis entity with FAILED status
     */
    private LogAnalysis handleAnalysisFailure(Log log, Exception e) {
        if (log == null || log.getId() == null) {
            logger.error("Cannot create failure record: log is null or has no ID", e);
            // Don't throw - try to continue gracefully
            return null;
        }

        logger.error("Creating FAILED analysis record. logId={}, reason={}", log.getId(), e.getMessage(), e);

        // Only create NEW entity if one wasn't already created (safety net case)
        LogAnalysis analysis = new LogAnalysis();
        analysis.setLog(log);
        analysis.setStatus(AnalysisStatus.FAILED);
        analysis.setSource(AnalysisSource.RULE);  // Fallback source
        analysis.setSeverity(Severity.LOW);  // Conservative default
        analysis.setConfidence(0.5);

        String errorMsg = "Analysis could not be completed due to system error";
        if (e != null && e.getMessage() != null) {
            errorMsg += ": " + e.getMessage();
        }

        analysis.setAnalysis(errorMsg);
        analysis.setPossibleFix("Please check system logs and try again later");
        analysis.setCompletedAt(LocalDateTime.now());

        analysis = repository.save(analysis);
        logger.warn("FAILED analysis record created. analysisId={}, logId={}", analysis.getId(), log.getId());

        metricsRecorder.recordFailure();
        return analysis;
    }
    }

