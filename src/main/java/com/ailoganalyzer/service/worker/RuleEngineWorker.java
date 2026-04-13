package com.ailoganalyzer.service.worker;

import com.ailoganalyzer.entity.Log;
import com.ailoganalyzer.entity.LogAnalysis;
import com.ailoganalyzer.enums.AnalysisSource;
import com.ailoganalyzer.enums.Severity;
import com.ailoganalyzer.constant.AnalysisConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.regex.Pattern;

/**
 * Optimized rule engine for pattern matching.
 *
 * Improvements:
 * - Precompiled regex patterns for efficiency
 * - Lowercase normalization done once
 * - Hierarchical pattern matching (critical -> high -> low)
 * - Extensible design for future optimization (Aho-Corasick, etc.)
 */
@Service
public class RuleEngineWorker {

    private static final Logger logger = LoggerFactory.getLogger(RuleEngineWorker.class);

    // Precompiled patterns for efficiency (avoid recompilation on each call)
    private static final Pattern CRITICAL_MEMORY_PATTERN = Pattern.compile("outofmemory|oom|heap|out of memory", Pattern.CASE_INSENSITIVE);
    private static final Pattern CRITICAL_CRASH_PATTERN = Pattern.compile("crash|fatal error|segmentation fault", Pattern.CASE_INSENSITIVE);
    private static final Pattern HIGH_TIMEOUT_PATTERN = Pattern.compile("timeout|timed out|exceeded|deadlock", Pattern.CASE_INSENSITIVE);
    private static final Pattern HIGH_ERROR_PATTERN = Pattern.compile("exception|error occurred|critical error", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEDIUM_WARN_PATTERN = Pattern.compile("warn|warning|deprecated", Pattern.CASE_INSENSITIVE);

    /**
     * Analyzes a log message using optimized rule patterns.
     * Returns immediately without blocking.
     *
     * @param log the log to analyze
     * @return the analysis result with severity
     */
    public LogAnalysis analyze(Log log) {
        String message = log.getMessage();
        if (message == null || message.isEmpty()) {
            return createDefaultAnalysis(log);
        }

        // Normalize once
        String normalizedMessage = message.toLowerCase();

        LogAnalysis analysis = new LogAnalysis();
        analysis.setLog(log);
        analysis.setSource(AnalysisSource.RULE);

        // Check CRITICAL patterns first
        if (CRITICAL_MEMORY_PATTERN.matcher(normalizedMessage).find()) {
            analysis.setSeverity(Severity.CRITICAL);
            analysis.setConfidence(0.95);
            analysis.setAnalysis(AnalysisConstants.CRITICAL_MEMORY_ANALYSIS);
            analysis.setPossibleFix(AnalysisConstants.CRITICAL_MEMORY_FIX);
            return analysis;
        }

        if (CRITICAL_CRASH_PATTERN.matcher(normalizedMessage).find()) {
            analysis.setSeverity(Severity.CRITICAL);
            analysis.setConfidence(0.93);
            analysis.setAnalysis("Application crash or fatal error detected");
            analysis.setPossibleFix("Restart the service and review recent deployments");
            return analysis;
        }

        // Check HIGH patterns
        if (HIGH_TIMEOUT_PATTERN.matcher(normalizedMessage).find()) {
            analysis.setSeverity(Severity.HIGH);
            analysis.setConfidence(0.85);
            analysis.setAnalysis(AnalysisConstants.TIMEOUT_ANALYSIS);
            analysis.setPossibleFix(AnalysisConstants.TIMEOUT_FIX);
            return analysis;
        }

        if (HIGH_ERROR_PATTERN.matcher(normalizedMessage).find()) {
            analysis.setSeverity(Severity.HIGH);
            analysis.setConfidence(0.80);
            analysis.setAnalysis("Error condition detected that requires attention");
            analysis.setPossibleFix("Review error logs and check system health");
            return analysis;
        }

        // Check MEDIUM patterns
        if (MEDIUM_WARN_PATTERN.matcher(normalizedMessage).find()) {
            analysis.setSeverity(Severity.MEDIUM);
            analysis.setConfidence(0.70);
            analysis.setAnalysis("Warning or deprecation notice");
            analysis.setPossibleFix("Review and update as needed");
            return analysis;
        }

        // Default to LOW
        return createDefaultAnalysis(log);
    }

    /**
     * Creates a default LOW severity analysis
     */
    private LogAnalysis createDefaultAnalysis(Log log) {
        LogAnalysis analysis = new LogAnalysis();
        analysis.setLog(log);
        analysis.setSource(AnalysisSource.RULE);
        analysis.setSeverity(Severity.LOW);
        analysis.setConfidence(0.60);
        analysis.setAnalysis(AnalysisConstants.LOW_SEVERITY_ANALYSIS);
        analysis.setPossibleFix(AnalysisConstants.LOW_SEVERITY_FIX);
        return analysis;
    }
}

