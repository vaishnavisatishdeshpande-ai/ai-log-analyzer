package com.ailoganalyzer.integration;

import com.ailoganalyzer.entity.Log;
import com.ailoganalyzer.entity.LogAnalysis;
import com.ailoganalyzer.enums.AnalysisStatus;
import com.ailoganalyzer.repository.LogAnalysisRepository;
import com.ailoganalyzer.repository.LogRepository;
import com.ailoganalyzer.service.job.AnalysisJobPublisher;
import com.ailoganalyzer.service.job.AsyncAnalysisJobPublisher;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AnalysisWorkflowIntegrationTest {

    @Autowired
    private LogRepository logRepository;

    @Autowired
    private LogAnalysisRepository analysisRepository;

    @Autowired
    private AnalysisJobPublisher publisher;

    @Test
    void shouldCompleteAsyncWorkflowLifecycle() {

        // Create log
        Log log = new Log();

        log.setServiceName("PaymentService");
        log.setLevel("ERROR");
        log.setMessage(
                "Connection timeout after 30000ms"
        );

        log = logRepository.save(log);

        // Create pending analysis
        LogAnalysis pending = new LogAnalysis();

        pending.setLog(log);
        pending.setStatus(AnalysisStatus.PENDING);

        pending.setCorrelationId(
                java.util.UUID.randomUUID().toString()
        );

        LogAnalysis savedPending =
                analysisRepository.save(pending);

        // Trigger async workflow
        publisher.publish(log.getId());

        // Await workflow completion
        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {

                    LogAnalysis result =
                            analysisRepository
                                    .findById(savedPending.getId())
                                    .orElseThrow();

                    assertEquals(
                            AnalysisStatus.COMPLETED,
                            result.getStatus()
                    );

                    assertNotNull(result.getAnalysis());

                    assertNotNull(result.getSeverity());

                    assertNotNull(result.getSource());

                    assertNotNull(result.getCompletedAt());
                });
    }
}