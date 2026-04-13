package com.ailoganalyzer.constant;

public class AnalysisConstants {
    public static final String CRITICAL_MEMORY_ANALYSIS = "Critical memory or crash issue detected.";
    public static final String CRITICAL_MEMORY_FIX = "Increase JVM heap size, check for memory leaks, or optimize resource usage.";

    public static final String TIMEOUT_ANALYSIS = "Timeout issue detected, likely due to slow database or network latency.";
    public static final String TIMEOUT_FIX = "Check database connectivity, increase timeout settings, review connection pool configuration.";

    public static final String LOW_SEVERITY_ANALYSIS = "No major issue detected in the log.";
    public static final String LOW_SEVERITY_FIX = "Monitor the system and review logs manually if issues persist.";
}
