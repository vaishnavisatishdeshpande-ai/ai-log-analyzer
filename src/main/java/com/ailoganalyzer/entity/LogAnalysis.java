    package com.ailoganalyzer.entity;

    import com.fasterxml.jackson.annotation.JsonIgnore;
    import jakarta.persistence.*;

    import com.ailoganalyzer.enums.Severity;
    import com.ailoganalyzer.enums.AnalysisSource;
    import com.ailoganalyzer.enums.AnalysisStatus;
    import java.time.LocalDateTime;

    import lombok.Data;

    @Entity
    @Data

    public class LogAnalysis {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;


        @ManyToOne
        @JoinColumn(name = "log_id")
        @JsonIgnore
        private Log log;

        @Column(length = 2000)
        private String analysis;
        @Column(length = 2000)
        private String possibleFix;

        @Enumerated(EnumType.STRING)
        private Severity severity;

        private Double confidence;

        @Enumerated(EnumType.STRING)
        private AnalysisSource source;

        @Enumerated(EnumType.STRING)
        private AnalysisStatus status = AnalysisStatus.PENDING;

        private LocalDateTime createdAt;
        private LocalDateTime completedAt;

        @PrePersist
        protected void onCreate() {
            createdAt = LocalDateTime.now();
            if (status == null) {
                status = AnalysisStatus.PENDING;
            }
        }
    }