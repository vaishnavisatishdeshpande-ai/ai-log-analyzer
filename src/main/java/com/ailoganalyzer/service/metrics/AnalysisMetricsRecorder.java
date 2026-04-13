package com.ailoganalyzer.service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

/**
 * Service for recording operational metrics.
 *
 * Wraps Micrometer counters and timers for easy use
 * throughout the application.
 */
@Service
public class AnalysisMetricsRecorder {

    private final MeterRegistry meterRegistry;

    public AnalysisMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Record that an analysis job was submitted
     */
    public void recordJobSubmitted() {
        Counter.builder("analysis.job.count")
                .description("Total number of analysis jobs submitted")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record successful analysis
     */
    public void recordSuccess() {
        Counter.builder("analysis.success.count")
                .description("Number of successfully completed analyses")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record failed analysis
     */
    public void recordFailure() {
        Counter.builder("analysis.failure.count")
                .description("Number of failed analyses")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record AI-based analysis
     */
    public void recordAiAnalysis() {
        Counter.builder("analysis.ai.count")
                .description("Number of analyses performed by AI")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record rule-based analysis
     */
    public void recordRuleAnalysis() {
        Counter.builder("analysis.rule.count")
                .description("Number of analyses performed by rule engine")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record severity distribution
     */
    public void recordSeverity(String severity) {
        String metricName = "severity." + severity.toLowerCase() + ".count";
        Counter.builder(metricName)
                .description("Number of " + severity + " severity analyses")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record AI analysis latency
     */
    public Timer.Sample recordAiLatency() {
        return Timer.start(meterRegistry);
    }

    public void stopAiLatency(Timer.Sample sample) {
        sample.stop(Timer.builder("analysis.ai.latency")
                .description("Latency of AI analysis in milliseconds")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry));
    }

    /**
     * Record rule engine latency
     */
    public Timer.Sample recordRuleLatency() {
        return Timer.start(meterRegistry);
    }

    public void stopRuleLatency(Timer.Sample sample) {
        sample.stop(Timer.builder("analysis.rule.latency")
                .description("Latency of rule engine analysis in milliseconds")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry));
    }
}

