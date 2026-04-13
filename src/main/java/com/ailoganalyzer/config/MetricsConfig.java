package com.ailoganalyzer.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metrics configuration using Micrometer.
 *
 * Tracks key operational metrics:
 * - Job counts and success/failure rates
 * - Latency for AI and rule engine
 * - Severity distribution
 *
 * All metrics are registered with Spring Boot Actuator
 * and available at /actuator/prometheus (with Prometheus exporter)
 * or /actuator/metrics
 */
@Configuration
public class MetricsConfig {

    /**
     * Counter for total analysis jobs submitted
     */
    @Bean
    public Counter analysisJobCounter(MeterRegistry registry) {
        return Counter.builder("analysis.job.count")
                .description("Total number of analysis jobs submitted")
                .register(registry);
    }

    /**
     * Counter for successful analyses
     */
    @Bean
    public Counter analysisSuccessCounter(MeterRegistry registry) {
        return Counter.builder("analysis.success.count")
                .description("Number of successfully completed analyses")
                .register(registry);
    }

    /**
     * Counter for failed analyses
     */
    @Bean
    public Counter analysisFailureCounter(MeterRegistry registry) {
        return Counter.builder("analysis.failure.count")
                .description("Number of failed analyses")
                .register(registry);
    }

    /**
     * Counter for AI-based analyses
     */
    @Bean
    public Counter aiAnalysisCounter(MeterRegistry registry) {
        return Counter.builder("analysis.ai.count")
                .description("Number of analyses performed by AI")
                .register(registry);
    }

    /**
     * Counter for rule-based analyses
     */
    @Bean
    public Counter ruleAnalysisCounter(MeterRegistry registry) {
        return Counter.builder("analysis.rule.count")
                .description("Number of analyses performed by rule engine")
                .register(registry);
    }

    /**
     * Timer for AI analysis latency
     */
    @Bean
    public Timer aiAnalysisLatencyTimer(MeterRegistry registry) {
        return Timer.builder("analysis.ai.latency")
                .description("Latency of AI analysis in milliseconds")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    /**
     * Timer for rule engine latency
     */
    @Bean
    public Timer ruleAnalysisLatencyTimer(MeterRegistry registry) {
        return Timer.builder("analysis.rule.latency")
                .description("Latency of rule engine analysis in milliseconds")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    /**
     * Counter for CRITICAL severity logs
     */
    @Bean
    public Counter criticalSeverityCounter(MeterRegistry registry) {
        return Counter.builder("severity.critical.count")
                .description("Number of CRITICAL severity analyses")
                .register(registry);
    }

    /**
     * Counter for HIGH severity logs
     */
    @Bean
    public Counter highSeverityCounter(MeterRegistry registry) {
        return Counter.builder("severity.high.count")
                .description("Number of HIGH severity analyses")
                .register(registry);
    }

    /**
     * Counter for MEDIUM severity logs
     */
    @Bean
    public Counter mediumSeverityCounter(MeterRegistry registry) {
        return Counter.builder("severity.medium.count")
                .description("Number of MEDIUM severity analyses")
                .register(registry);
    }

    /**
     * Counter for LOW severity logs
     */
    @Bean
    public Counter lowSeverityCounter(MeterRegistry registry) {
        return Counter.builder("severity.low.count")
                .description("Number of LOW severity analyses")
                .register(registry);
    }
}

