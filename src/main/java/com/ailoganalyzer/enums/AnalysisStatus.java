package com.ailoganalyzer.enums;

/**
 * Tracks the state of an analysis job through its lifecycle.
 * Enables non-blocking async processing with status tracking.
 */
public enum AnalysisStatus {
    /**
     * Analysis job submitted but not yet started
     */
    PENDING,

    /**
     * Analysis job currently being processed
     */
    PROCESSING,

    /**
     * Analysis completed successfully
     */
    COMPLETED,

    /**
     * Analysis failed (fallback to rule engine applied)
     */
    FAILED
}

