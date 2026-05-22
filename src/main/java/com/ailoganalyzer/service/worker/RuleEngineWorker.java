package com.ailoganalyzer.service.worker;

import com.ailoganalyzer.entity.Log;
import com.ailoganalyzer.entity.LogAnalysis;
import com.ailoganalyzer.enums.AnalysisSource;
import com.ailoganalyzer.enums.Severity;
import com.ailoganalyzer.service.rule.RuleEngineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Deterministic rule-based analysis worker.
 * Delegates pattern matching to RuleEngineService (config-driven).
 * Always succeeds — this is the fallback guarantee.
 */
@Service
public class RuleEngineWorker {

    private static final Logger logger =
            LoggerFactory.getLogger(RuleEngineWorker.class);

    private final RuleEngineService ruleEngine;

    public RuleEngineWorker(RuleEngineService ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    public LogAnalysis analyze(Log log) {

        RuleEngineService.RuleResult result =
                ruleEngine.analyze(log.getMessage());

        LogAnalysis analysis = new LogAnalysis();

        analysis.setLog(log);
        analysis.setSource(AnalysisSource.RULE);
        analysis.setSeverity(result.severity);
        analysis.setConfidence(result.confidence);

        analysis.setAnalysis(buildAnalysisText(result));
        analysis.setPossibleFix(buildFixText(result));

        logger.debug(
                "Rule engine classified logId={} as {} (confidence={})",
                log.getId(),
                result.severity,
                result.confidence
        );

        return analysis;
    }

    private String buildAnalysisText(
            RuleEngineService.RuleResult result
    ) {

        return switch (result.severity) {

            case CRITICAL ->
                    "Critical system failure detected. Immediate attention required.";

            case HIGH ->
                    "Significant operational issue detected requiring investigation.";

            case MEDIUM ->
                    "Warning condition detected. Monitor for escalation.";

            case LOW ->
                    "Informational log entry. No immediate action required.";
        };
    }

    private String buildFixText(
            RuleEngineService.RuleResult result
    ) {

        return switch (result.severity) {

            case CRITICAL ->
                    "Restart affected services, check resource limits, review recent deployments.";

            case HIGH ->
                    "Check service health, review connection pools, verify external dependencies.";

            case MEDIUM ->
                    "Review and update as needed. Consider adding monitoring alerts.";

            case LOW ->
                    "No action required. Continue monitoring.";
        };
    }
}