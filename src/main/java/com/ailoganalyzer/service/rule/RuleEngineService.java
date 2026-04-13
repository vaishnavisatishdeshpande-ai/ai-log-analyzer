package com.ailoganalyzer.service.rule;

import com.ailoganalyzer.config.SeverityRulesConfig;
import com.ailoganalyzer.constant.SeverityConstants;
import com.ailoganalyzer.enums.Severity;
import org.springframework.stereotype.Component;

@Component
public class RuleEngineService {

    private final SeverityRulesConfig config;

    public RuleEngineService(SeverityRulesConfig config) {
        this.config = config;
    }

    public RuleResult analyze(String message) {
        if (message == null || message.isBlank()) {
            return new RuleResult(Severity.LOW, SeverityConstants.LOW_CONFIDENCE, "Empty message");
        }

        var rule = config.findRuleByPattern(message);

        if (rule != null) {
            String matchedPattern = extractMatchedPattern(message, rule);
            String reason = String.format(SeverityConstants.REASON_PATTERN_MATCH, matchedPattern);
            return new RuleResult(rule.getLevel(), rule.getConfidence(), reason);
        }

        return new RuleResult(Severity.LOW, SeverityConstants.LOW_CONFIDENCE, "No pattern matched");
    }

    private String extractMatchedPattern(String message, SeverityRulesConfig.RuleDefinition rule) {
        String lowerMessage = message.toLowerCase();
        for (String pattern : rule.getPatterns()) {
            if (lowerMessage.contains(pattern)) {
                return pattern;
            }
        }
        return "unknown";
    }

    public static class RuleResult {
        public final Severity severity;
        public final double confidence;
        public final String reason;

        public RuleResult(Severity severity, double confidence, String reason) {
            this.severity = severity;
            this.confidence = confidence;
            this.reason = reason;
        }
    }
}
