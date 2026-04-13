package com.ailoganalyzer.constant;

public class SeverityConstants {

    // Confidence Thresholds
    public static final double AI_HIGH_CONFIDENCE_THRESHOLD = 0.90;
    public static final double AI_DEFAULT_CONFIDENCE = 0.5;

    // Weights for Hybrid Calculation
    public static final double AI_WEIGHT = 0.6;
    public static final double RULE_WEIGHT = 0.4;

    // Default Confidence Values
    public static final double CRITICAL_CONFIDENCE = 0.95;
    public static final double HIGH_CONFIDENCE = 0.85;
    public static final double MEDIUM_CONFIDENCE = 0.70;
    public static final double LOW_CONFIDENCE = 0.50;

    // Source Types
    public static final String SOURCE_AI = "AI";
    public static final String SOURCE_RULE = "RULE";
    public static final String SOURCE_HYBRID = "HYBRID";
    public static final String SOURCE_RULE_OVERRIDE = "RULE_OVERRIDE";

    // Metric Names
    public static final String METRIC_SEVERITY_HYBRID_COUNT = "severity.hybrid.count";
    public static final String METRIC_SEVERITY_RULE_OVERRIDE = "severity.rule.override";
    public static final String METRIC_SEVERITY_AI_PREFERRED = "severity.ai.preferred";
    public static final String METRIC_SEVERITY_DISAGREEMENT = "severity.disagreement";

    // Reason Messages
    public static final String REASON_PATTERN_MATCH = "Matched pattern: {}";
    public static final String REASON_AI_HIGH_CONFIDENCE = "AI high confidence (>{})";
    public static final String REASON_BOTH_AGREE = "AI and Rule agree";
    public static final String REASON_RULE_OVERRIDE_CRITICAL = "Rule override - severity is CRITICAL";
    public static final String REASON_RULE_HIGHER_SEVERITY = "Rule override - higher severity detected";

    // Confidence Capping
    public static final double MAX_CONFIDENCE = 1.0;
}
