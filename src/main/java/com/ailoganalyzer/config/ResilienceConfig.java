package com.ailoganalyzer.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience patterns for handling AI service degradation.
 *
 * Flow:
 * Retry
 * → RateLimiter
 * → CircuitBreaker
 *
 * Prevents:
 * - Overwhelming AI provider
 * - Cascading failures
 * - Immediate hard-fail behavior
 * - Resource exhaustion
 */
@Configuration
public class ResilienceConfig {

    /**
     * Retry transient AI failures before
     * circuit breaker counts them.
     */
    @Bean
    public Retry aiAnalysisRetry() {

        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(2))
                .retryExceptions(Exception.class)
                .build();

        return RetryRegistry.of(config)
                .retry("aiAnalysisRetry");
    }

    /**
     * Rate limiter for AI analysis requests.
     */
    @Bean
    public RateLimiter aiAnalysisLimiter(
            RateLimiterRegistry registry
    ) {

        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(30)
                .timeoutDuration(Duration.ofSeconds(5))
                .build();

        return registry.rateLimiter(
                "aiAnalysisLimiter",
                config
        );
    }

    /**
     * Circuit breaker for AI degradation handling.
     */
    @Bean
    public CircuitBreaker aiAnalysisCircuitBreaker(
            CircuitBreakerRegistry registry
    ) {

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .slowCallRateThreshold(50.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(10))
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .minimumNumberOfCalls(10)
                .build();

        return registry.circuitBreaker(
                "aiAnalysisCircuitBreaker",
                config
        );
    }
}