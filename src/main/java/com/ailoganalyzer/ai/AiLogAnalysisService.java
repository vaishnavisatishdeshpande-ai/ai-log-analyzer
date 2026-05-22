package com.ailoganalyzer.ai;

import com.ailoganalyzer.config.AiPromptConfig;
import com.ailoganalyzer.entity.Log;
import com.ailoganalyzer.entity.LogAnalysis;
import com.ailoganalyzer.enums.AnalysisSource;
import com.ailoganalyzer.enums.AnalysisStatus;
import com.ailoganalyzer.enums.Severity;
import com.ailoganalyzer.exception.AnalysisException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Service
public class AiLogAnalysisService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AiPromptConfig promptConfig;
    private static final Logger logger =
            LoggerFactory.getLogger(AiLogAnalysisService.class);

    public AiLogAnalysisService(ChatClient.Builder builder,
                                ObjectMapper objectMapper,
                                AiPromptConfig promptConfig) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
        this.promptConfig = promptConfig;
    }

    /**
     *  ASYNC METHOD
     * - Fetch existing PENDING analysis
     * - Call AI
     * - Update SAME record (no duplicate rows)
     */
    @Async
    @Transactional
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

            analysis.setAnalysis(getSafeText(json, "analysis"));
            analysis.setPossibleFix(getSafeText(json, "possibleFix"));
            analysis.setConfidence(getSafeDouble(json, "confidence"));
            analysis.setSource(AnalysisSource.AI);

            String severityStr = getSafeText(json, "severity");

            try {
                analysis.setSeverity(
                        Severity.valueOf(severityStr.trim().toUpperCase())
                );
            } catch (Exception e) {
                logger.warn(
                        "Invalid severity from AI response: '{}', defaulting to MEDIUM",
                        severityStr
                );
                analysis.setSeverity(Severity.MEDIUM);
            }

            return analysis;

        } catch (Exception e) {
            throw new AnalysisException("Invalid AI response", e);
        }
    }
}