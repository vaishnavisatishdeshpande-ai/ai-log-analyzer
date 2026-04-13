package com.ailoganalyzer.repository;

import com.ailoganalyzer.entity.LogAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface LogAnalysisRepository extends JpaRepository<LogAnalysis, Long> {
        Optional<LogAnalysis> findByLogIdOrderByCreatedAtDesc(Long logId);
        Optional<LogAnalysis> findTopByLogIdOrderByCreatedAtDesc(Long logId);
}
