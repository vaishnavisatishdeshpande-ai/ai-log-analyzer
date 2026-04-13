package com.ailoganalyzer.service.severity;

import com.ailoganalyzer.ai.AiLogAnalysisService;
import com.ailoganalyzer.constant.SeverityConstants;
import com.ailoganalyzer.entity.Log;
import com.ailoganalyzer.entity.LogAnalysis;
import com.ailoganalyzer.service.rule.RuleEngineService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SeverityOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(SeverityOrchestrator.class);

    private final AiLogAnalysisService aiService;
    private final RuleEngineService ruleEngine;
    private final SeverityResolver resolver;
    private final MeterRegistry meterRegistry;

    public SeverityOrchestrator(AiLogAnalysisService aiService, RuleEngineService ruleEngine, MeterRegistry meterRegistry) {
        this.aiService = aiService;
        this.ruleEngine = ruleEngine;
        this.resolver = new SeverityResolver();
        this.meterRegistry = meterRegistry;
    }

    public void enrichWithHybridSeverity(LogAnalysis analysis, Log log) {
        try {
            var aiResult = extractAiResult(analysis);
            var ruleEngineResult = ruleEngine.analyze(log.getMessage());
            var ruleResult = new SeverityResolver.RuleResult(ruleEngineResult.severity, ruleEngineResult.confidence, ruleEngineResult.reason);
            var decision = resolver.resolve(aiResult, ruleResult);

            analysis.setSeverity(decision.severity);
            analysis.setConfidence(decision.confidence);
            analysis.setSource(com.ailoganalyzer.enums.AnalysisSource.valueOf(decision.source));

            recordMetrics(decision, aiResult, ruleResult);

            logger.info("Severity resolved: logId={}, source={}, severity={}, confidence={:.2f}, reason={}",
                    log.getId(), decision.source, decision.severity, decision.confidence, decision.reason);
        } catch (Exception e) {
            logger.error("Failed to enrich severity, keeping AI result. logId={}", log.getId(), e);
        }
    }

    private SeverityResolver.AiResult extractAiResult(LogAnalysis analysis) {
        return new SeverityResolver.AiResult(analysis.getSeverity(), analysis.getConfidence());
    }

    private void recordMetrics(SeverityResolver.SeverityDecision decision, SeverityResolver.AiResult ai, SeverityResolver.RuleResult rule) {
        Counter.builder(SeverityConstants.METRIC_SEVERITY_HYBRID_COUNT)
                .description("Total severity decisions made")
                .register(meterRegistry)
                .increment();

        if (SeverityConstants.SOURCE_RULE_OVERRIDE.equals(decision.source)) {
            Counter.builder(SeverityConstants.METRIC_SEVERITY_RULE_OVERRIDE)
                    .description("Rule overrides of AI severity")
                    .register(meterRegistry)
                    .increment();
        }

        if (SeverityConstants.SOURCE_AI.equals(decision.source)) {
            Counter.builder(SeverityConstants.METRIC_SEVERITY_AI_PREFERRED)
                    .description("Cases where AI was preferred")
                    .register(meterRegistry)
                    .increment();
        }

        if (ai.severity != rule.severity) {
            Counter.builder(SeverityConstants.METRIC_SEVERITY_DISAGREEMENT)
                    .description("Cases where AI and Rule disagreed")
                    .register(meterRegistry)
                    .increment();
        }
    }
}
