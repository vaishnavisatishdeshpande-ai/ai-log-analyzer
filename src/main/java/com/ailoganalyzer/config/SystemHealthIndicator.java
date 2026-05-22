package com.ailoganalyzer.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class SystemHealthIndicator implements HealthIndicator {

    private final CircuitBreaker circuitBreaker;

    public SystemHealthIndicator(
            CircuitBreaker aiAnalysisCircuitBreaker
    ) {

        this.circuitBreaker = aiAnalysisCircuitBreaker;
    }

    @Override
    public Health health() {

        CircuitBreaker.State state =
                circuitBreaker.getState();

        return switch (state) {

            case CLOSED -> Health.up()
                    .withDetail(
                            "circuitBreaker",
                            "CLOSED"
                    )
                    .withDetail(
                            "aiService",
                            "available"
                    )
                    .build();

            case HALF_OPEN -> Health.up()
                    .withDetail(
                            "circuitBreaker",
                            "HALF_OPEN"
                    )
                    .withDetail(
                            "aiService",
                            "recovering"
                    )
                    .build();

            case OPEN -> Health.down()
                    .withDetail(
                            "circuitBreaker",
                            "OPEN"
                    )
                    .withDetail(
                            "aiService",
                            "unavailable"
                    )
                    .withDetail(
                            "fallback",
                            "rule engine active"
                    )
                    .build();

            default -> Health.unknown().build();
        };
    }
}