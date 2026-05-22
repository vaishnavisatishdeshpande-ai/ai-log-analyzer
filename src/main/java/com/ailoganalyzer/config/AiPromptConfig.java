package com.ailoganalyzer.config;

import org.springframework.stereotype.Component;

@Component
public class AiPromptConfig {

    public String buildPrompt(String message) {

        String sanitized = sanitize(message);

        return """
            You are a log analysis engine.

            Analyze the log entry between the <LOG> tags
            and return ONLY valid JSON.

            Response format:
            {
              "analysis": "root cause description",
              "possibleFix": "actionable remediation steps",
              "severity": "LOW|MEDIUM|HIGH|CRITICAL",
              "confidence": 0.0-1.0
            }

            <LOG>
            %s
            </LOG>
            """.formatted(sanitized);
    }

    private String sanitize(String message) {

        if (message == null) {
            return "";
        }

        return message
                .replace("</LOG>", "")
                .replace("<LOG>", "")
                .replace("```", "")
                .trim();
    }
}