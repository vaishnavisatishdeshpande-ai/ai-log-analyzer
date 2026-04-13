package com.ailoganalyzer.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
public class Log {
    @Id
    @GeneratedValue
    private Long id;

    private String serviceName;
    private String level;
    private String message;
    private LocalDateTime timestamp;

    @OneToMany(mappedBy = "log", cascade = CascadeType.ALL)
    private List<LogAnalysis> analyses;
}