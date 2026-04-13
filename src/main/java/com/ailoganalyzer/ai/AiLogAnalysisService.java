package com.ailoganalyzer.ai;

import com.ailoganalyzer.config.AiPromptConfig;
import com.ailoganalyzer.entity.Log;
import com.ailoganalyzer.entity.LogAnalysis;
import com.ailoganalyzer.enums.AnalysisSource;
import com.ailoganalyzer.enums.AnalysisStatus;
import com.ailoganalyzer.enums.Severity;
import com.ailoganalyzer.exception.AnalysisException;
import com.ailoganalyzer.repository.LogAnalysisRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AiLogAnalysisService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AiPromptConfig promptConfig;
    private final LogAnalysisRepository repository;

    public AiLogAnalysisService(ChatClient.Builder builder,
                                ObjectMapper objectMapper,
                                AiPromptConfig promptConfig,
                                LogAnalysisRepository repository) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
        this.promptConfig = promptConfig;
        this.repository = repository;
    }

    /**
     *  ASYNC METHOD
     * - Fetch existing PENDING analysis
     * - Call AI
     * - Update SAME record (no duplicate rows)
     */
    @Async
    @Transactional
    public void analyzeAndUpdate(Log log) {
        LogAnalysis analysis = repository.findByLogIdOrderByCreatedAtDesc(log.getId())
                .orElseThrow(() ->
                        new AnalysisException("No pending analysis found for logId: " + log.getId()));
        System.out.println("Log ID: " + log.getId());
        try {
            String prompt = promptConfig.buildPrompt(log.getMessage());

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            response = response.replace("```json", "")
                    .replace("```", "")
                    .trim();

            JsonNode json = objectMapper.readTree(response);
            analysis.setAnalysis(json.get("analysis").asText());
            analysis.setAnalysis(getSafeText(json, "analysis"));
            analysis.setPossibleFix(getSafeText(json, "possibleFix"));
            analysis.setConfidence(getSafeDouble(json, "confidence"));
            analysis.setConfidence(json.get("confidence").asDouble());
            analysis.setSource(AnalysisSource.AI);
            analysis.setStatus(AnalysisStatus.COMPLETED);
            analysis.setCompletedAt(LocalDateTime.now());
            String severityStr = getSafeText(json, "severity");

            if (severityStr != null) {
                try {
                    analysis.setSeverity(
                            Severity.valueOf(severityStr.trim().toUpperCase())
                    );
                } catch (Exception e) {
                    System.out.println("Invalid severity from AI: " + severityStr);
                    analysis.setSeverity(Severity.MEDIUM);
                }
            } else {
                analysis.setSeverity(Severity.MEDIUM);
            }

            repository.save(analysis);

        } catch (Exception e) {

            analysis.setStatus(AnalysisStatus.FAILED);
            analysis.setAnalysis("AI processing failed");
            analysis.setCompletedAt(LocalDateTime.now());

            repository.save(analysis);

            throw new AnalysisException("AI analysis failed", e);
        }
    }

    private String getSafeText(JsonNode json, String field) {
        return json.has(field) && !json.get(field).isNull()
                ? json.get(field).asText()
                : null;
    }

    private Double getSafeDouble(JsonNode json, String field) {
        return json.has(field) && !json.get(field).isNull()
                ? json.get(field).asDouble()
                : null;
    }

    /**
     * OPTIONAL (for sync API)
     * Pure AI logic without DB interaction
     */
    public LogAnalysis analyzeLog(Log log) {

        try {
            String prompt = promptConfig.buildPrompt(log.getMessage());

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            response = response.replace("```json", "")
                    .replace("```", "")
                    .trim();

            JsonNode json = objectMapper.readTree(response);

            LogAnalysis analysis = new LogAnalysis();
            analysis.setAnalysis(json.get("analysis").asText());
            analysis.setPossibleFix(json.get("possibleFix").asText());
            analysis.setSeverity(
                    Severity.valueOf(json.get("severity").asText().toUpperCase())
            );
            analysis.setConfidence(json.get("confidence").asDouble());
            analysis.setSource(AnalysisSource.AI);

            return analysis;

        } catch (Exception e) {
            throw new AnalysisException("Invalid AI response", e);
        }
    }
}