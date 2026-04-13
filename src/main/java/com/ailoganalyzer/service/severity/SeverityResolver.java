package com.ailoganalyzer.service.severity;

import com.ailoganalyzer.constant.SeverityConstants;
import com.ailoganalyzer.enums.Severity;

public class SeverityResolver {

    public SeverityDecision resolve(AiResult ai, RuleResult rule) {
        var finalSeverity = determineSeverity(ai, rule);
        var finalConfidence = calculateConfidence(ai, rule, finalSeverity);
        var decision = determineSourceAndReason(ai, rule, finalSeverity);

        return new SeverityDecision(finalSeverity, finalConfidence, decision.source, decision.reason);
    }

    private Severity determineSeverity(AiResult ai, RuleResult rule) {
        if (rule.severity == Severity.CRITICAL) {
            return Severity.CRITICAL;
        }

        if (rule.severity.ordinal() > ai.severity.ordinal()) {
            return rule.severity;
        }

        if (ai.confidence > SeverityConstants.AI_HIGH_CONFIDENCE_THRESHOLD) {
            return ai.severity;
        }

        if (ai.severity == rule.severity) {
            return ai.severity;
        }

        return rule.severity.ordinal() > ai.severity.ordinal() ? rule.severity : ai.severity;
    }

    private double calculateConfidence(AiResult ai, RuleResult rule, Severity finalSeverity) {
        double weightedConfidence = (ai.confidence * SeverityConstants.AI_WEIGHT) 
                                   + (rule.confidence * SeverityConstants.RULE_WEIGHT);
        
        return Math.min(SeverityConstants.MAX_CONFIDENCE, weightedConfidence);
    }

    private SourceAndReason determineSourceAndReason(AiResult ai, RuleResult rule, Severity finalSeverity) {
        if (rule.severity == Severity.CRITICAL && finalSeverity == Severity.CRITICAL) {
            return new SourceAndReason(SeverityConstants.SOURCE_RULE_OVERRIDE, 
                                       SeverityConstants.REASON_RULE_OVERRIDE_CRITICAL);
        }

        if (rule.severity.ordinal() > ai.severity.ordinal() && finalSeverity == rule.severity) {
            return new SourceAndReason(SeverityConstants.SOURCE_RULE_OVERRIDE,
                                       SeverityConstants.REASON_RULE_HIGHER_SEVERITY);
        }

        if (ai.severity == finalSeverity && rule.severity == finalSeverity) {
            return new SourceAndReason(SeverityConstants.SOURCE_HYBRID, 
                                       SeverityConstants.REASON_BOTH_AGREE);
        }

        if (ai.severity == finalSeverity) {
            String reason = String.format(SeverityConstants.REASON_AI_HIGH_CONFIDENCE, 
                                         SeverityConstants.AI_HIGH_CONFIDENCE_THRESHOLD);
            return new SourceAndReason(SeverityConstants.SOURCE_AI, reason);
        }

        return new SourceAndReason(SeverityConstants.SOURCE_RULE, rule.reason);
    }

    public static class AiResult {
        public final Severity severity;
        public final double confidence;

        public AiResult(Severity severity, double confidence) {
            this.severity = severity;
            this.confidence = confidence;
        }
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

    public static class SeverityDecision {
        public final Severity severity;
        public final double confidence;
        public final String source;
        public final String reason;

        public SeverityDecision(Severity severity, double confidence, String source, String reason) {
            this.severity = severity;
            this.confidence = confidence;
            this.source = source;
            this.reason = reason;
        }
    }

    private static class SourceAndReason {
        final String source;
        final String reason;

        SourceAndReason(String source, String reason) {
            this.source = source;
            this.reason = reason;
        }
    }
}
