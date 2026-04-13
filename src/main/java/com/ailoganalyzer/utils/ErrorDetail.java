package com.ailoganalyzer.utils;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ErrorDetail {
    private String analysis;
    private String possibleFix;
    private String severity;
}