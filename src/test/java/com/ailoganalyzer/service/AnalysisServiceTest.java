package com.ailoganalyzer.service;

import com.ailoganalyzer.ai.AiLogAnalysisService;
import com.ailoganalyzer.entity.Log;
import com.ailoganalyzer.entity.LogAnalysis;
import com.ailoganalyzer.enums.AnalysisSource;
import com.ailoganalyzer.enums.Severity;
import com.ailoganalyzer.repository.LogAnalysisRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    @Mock
    private AiLogAnalysisService aiService;

    @Mock
    private LogAnalysisRepository repository;

    @InjectMocks
    private AnalysisService analysisService;

    @Test
    void shouldReturnAiAnalysisWhenSuccessful() {
        var log = createLogEntry("INFO", "Application started");
        var aiResult = createLogAnalysis(AnalysisSource.AI, Severity.LOW, 0.8);
        when(aiService.analyzeLog(log)).thenReturn(aiResult);
        when(repository.save(any(LogAnalysis.class))).thenReturn(aiResult);

        var result = analysisService.analyzeLog(log);

        assertThat(result.getSource()).isEqualTo(AnalysisSource.AI);
        assertThat(result.getSeverity()).isEqualTo(Severity.LOW);
    }

    @Test
    void shouldFallbackToRuleWhenAiFails() {
        var log = createLogEntry("ERROR", "OutOfMemoryError occurred");
        when(aiService.analyzeLog(log)).thenThrow(RuntimeException.class);
        when(repository.save(any(LogAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = analysisService.analyzeLog(log);

        assertThat(result.getSource()).isEqualTo(AnalysisSource.RULE);
        assertThat(result.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(result.getConfidence()).isEqualTo(0.9);
    }

    @Test
    void shouldFallbackToLowSeverityWhenNoKeywordsMatch() {
        var log = createLogEntry("INFO", "Some random log message");
        when(aiService.analyzeLog(log)).thenThrow(RuntimeException.class);
        when(repository.save(any(LogAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = analysisService.analyzeLog(log);

        assertThat(result.getSource()).isEqualTo(AnalysisSource.RULE);
        assertThat(result.getSeverity()).isEqualTo(Severity.LOW);
        assertThat(result.getConfidence()).isEqualTo(0.6);
    }

    @Test
    void shouldFallbackToHighSeverityWhenTimeoutDetected() {
        var log = createLogEntry("ERROR", "Connection timeout occurred");
        when(aiService.analyzeLog(log)).thenThrow(RuntimeException.class);
        when(repository.save(any(LogAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = analysisService.analyzeLog(log);

        assertThat(result.getSource()).isEqualTo(AnalysisSource.RULE);
        assertThat(result.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(result.getConfidence()).isEqualTo(0.8);
    }

    private Log createLogEntry(String level, String message) {
        var log = new Log();
        log.setId(1L);
        log.setLevel(level);
        log.setMessage(message);
        return log;
    }

    private LogAnalysis createLogAnalysis(AnalysisSource source, Severity severity, double confidence) {
        var analysis = new LogAnalysis();
        analysis.setSource(source);
        analysis.setSeverity(severity);
        analysis.setConfidence(confidence);
        analysis.setAnalysis("Test analysis");
        analysis.setPossibleFix("Test fix");
        return analysis;
    }
}
