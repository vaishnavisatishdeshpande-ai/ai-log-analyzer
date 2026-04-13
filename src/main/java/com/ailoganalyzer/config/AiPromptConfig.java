package com.ailoganalyzer.config;

import org.springframework.stereotype.Component;

@Component
public class AiPromptConfig {

    public String buildPrompt(String message) {
        return """
            Analyze the log and return JSON:
            {
              "analysis": "...",
              "possibleFix": "...",
              "severity": "LOW|MEDIUM|HIGH|CRITICAL",
              "confidence": 0.0-1.0
            }

            Log:
            """ + message;
    }
}
