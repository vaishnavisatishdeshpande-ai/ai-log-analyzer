
/**
 * Resilience patterns for handling AI service degradation.
 *
 * Prevents:
 * - Overwhelming the AI service with too many concurrent requests
 * - Cascading failures when AI service is slow or down
 * - Resource exhaustion from retrying failed requests
 *
 * Fallback behavior:
 * - When rate limit exceeded: queue and retry
 * - When circuit open: immediately fallback to rule engine
 */
package com.ailoganalyzer.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
public class ResilienceConfig {

    /**
     * Rate limiter for AI analysis requests.
     *
     * Allows maximum throughput to OpenAI while maintaining
     * a fair queue for analysis jobs.
     *
     * Current limits:
     * - 30 requests per minute (1 per 2 seconds)
     * - Prevents overwhelming API
     * - Can be adjusted based on tier/quota
     */
    @Bean
    public RateLimiter aiAnalysisLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(30)  // 30 requests per minute = sustainable rate
                .timeoutDuration(Duration.ofSeconds(5))  // Wait up to 5s for permit
                .build();

        return registry.rateLimiter("aiAnalysisLimiter", config);
    }

    /**
     * Circuit breaker for AI analysis.
     *
     * Protects against cascading failures:
     * - CLOSED (normal): requests pass through
     * - OPEN (circuit broken): requests fail fast with fallback
     * - HALF_OPEN (recovery): test small number of requests
     *
     * Triggers circuit break if:
     * - 50% of requests fail OR
     * - Any request times out
     *
     * Recovers after 30 seconds of no requests
     */
    @Bean
    public CircuitBreaker aiAnalysisCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)  // Open circuit at 50% failure rate
                .slowCallRateThreshold(50.0f)  // Also open at 50% slow calls
                .slowCallDurationThreshold(Duration.ofSeconds(10))  // Consider >10s as slow
                .waitDurationInOpenState(Duration.ofSeconds(30))  // Try recovery after 30s
                .permittedNumberOfCallsInHalfOpenState(3)  // Test with 3 calls
                .minimumNumberOfCalls(10)  // Need at least 10 calls to assess
                .build();

        return registry.circuitBreaker("aiAnalysisCircuitBreaker", config);
    }
}

