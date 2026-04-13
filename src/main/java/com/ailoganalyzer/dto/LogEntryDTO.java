package com.ailoganalyzer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LogEntryDTO {
    private Long id;

    @NotBlank(message = "Service name is required")
    @Size(max = 100, message = "Service name must not exceed 100 characters")
    private String serviceName;

    @NotBlank(message = "Level is required")
    @Size(max = 20, message = "Level must not exceed 20 characters")
    private String level;

    @NotBlank(message = "Message is required")
    private String message;

    private LocalDateTime timestamp;
}
