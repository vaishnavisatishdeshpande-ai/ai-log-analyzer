package com.ailoganalyzer.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AnalysisResponse {
    private String analysis;
    private String possibleFix;
    private String severity;
    private double confidence;
}

