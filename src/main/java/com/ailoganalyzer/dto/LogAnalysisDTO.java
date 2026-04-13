package com.ailoganalyzer.dto;

import com.ailoganalyzer.enums.AnalysisSource;
import com.ailoganalyzer.enums.Severity;
import com.ailoganalyzer.enums.AnalysisStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LogAnalysisDTO {
    private Long id;
    private Long logId; // Instead of full LogEntryDTO to avoid recursion
    private String analysis;
    private String possibleFix;
    private Severity severity;
    private Double confidence;
    private AnalysisSource source;
    private AnalysisStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
