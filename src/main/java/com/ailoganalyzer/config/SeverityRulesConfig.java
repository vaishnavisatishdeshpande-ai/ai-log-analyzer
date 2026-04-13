package com.ailoganalyzer.config;

import com.ailoganalyzer.enums.Severity;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "severity.rules")
@Data
public class SeverityRulesConfig {

    private List<RuleDefinition> patterns;

    @Data
    public static class RuleDefinition {
        private Severity level;
        private List<String> patterns;
        private double confidence;
    }

    public RuleDefinition findRuleByPattern(String message) {
        if (message == null || patterns == null) {
            return null;
        }

        String lowerMessage = message.toLowerCase();

        for (RuleDefinition rule : patterns) {
            for (String pattern : rule.getPatterns()) {
                if (lowerMessage.contains(pattern)) {
                    return rule;
                }
            }
        }

        return null;
    }
}
