/**
 * REST Controller for log management and analysis.
 *
 * Key design changes:
 * - POST /logs/{id}/analysis now returns immediately with PENDING status
 * - Analysis is processed asynchronously in the background
 * - Callers can poll GET /logs/{id}/analysis to track status
 *
 * This non-blocking design allows the API to:
 * - Handle more concurrent requests
 * - Prevent long request timeouts
 * - Scale to high throughput
 */
package com.ailoganalyzer.controller;

import com.ailoganalyzer.ai.AiLogAnalysisService;
import com.ailoganalyzer.dto.LogAnalysisDTO;
import com.ailoganalyzer.dto.LogEntryDTO;
import com.ailoganalyzer.entity.Log;
import com.ailoganalyzer.entity.LogAnalysis;
import com.ailoganalyzer.service.AnalysisService;
import com.ailoganalyzer.service.LogService;
import com.ailoganalyzer.service.job.AnalysisJobPublisher;
import com.ailoganalyzer.service.metrics.AnalysisMetricsRecorder;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/logs")
public class LogController {

    private final LogService logService;
    private final AnalysisService analysisService;
    private final AnalysisJobPublisher jobPublisher;
    private final AnalysisMetricsRecorder metricsRecorder;
    private final AiLogAnalysisService aiService;


    public LogController(LogService logService,
                         AnalysisService analysisService,
                         AnalysisJobPublisher jobPublisher,
                         AnalysisMetricsRecorder metricsRecorder, AiLogAnalysisService aiService) {
        this.logService = logService;
        this.analysisService = analysisService;
        this.jobPublisher = jobPublisher;
        this.metricsRecorder = metricsRecorder;
        this.aiService = aiService;
    }

    @PostMapping
    public ResponseEntity<LogEntryDTO> create(@Valid @RequestBody LogEntryDTO request) {
        Log saved = logService.saveLog(request);
        analysisService.createPendingAnalysis(saved);
        return ResponseEntity.ok(toDto(saved));
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadLogFile(@RequestParam("file") MultipartFile file) {

        try {
            String content = new String(file.getBytes());

            List<String> lines = content.lines().toList();

            for (String line : lines) {

                if (line.isBlank()) {
                    continue;
                }

                LogEntryDTO dto = new LogEntryDTO();

                dto.setServiceName("BulkUploadService");

                if (line.contains("CRITICAL")) {
                    dto.setLevel("CRITICAL");
                } else if (line.contains("ERROR")) {
                    dto.setLevel("ERROR");
                } else if (line.contains("WARN")) {
                    dto.setLevel("WARN");
                } else {
                    dto.setLevel("INFO");
                }

                dto.setMessage(line);
                dto.setTimestamp(java.time.LocalDateTime.now());

                Log saved = logService.saveLog(dto);
                analysisService.createPendingAnalysis(saved);
            }

            return ResponseEntity.ok("Log file processed successfully");

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to process log file: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<LogEntryDTO>> getAll() {
        List<LogEntryDTO> response = logService.getAllLogs()
                .stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/service/{name}")
    public ResponseEntity<List<LogEntryDTO>> getByService(@PathVariable String name) {
        List<LogEntryDTO> response = logService.getLogsByServiceName(name)
                .stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LogEntryDTO> getById(@PathVariable Long id) {
        Log log = logService.getLogById(id);
        return ResponseEntity.ok(toDto(log));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        logService.deleteLog(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/analysis")
    public ResponseEntity<String> analyzeAsync(@PathVariable Long id) {

        Log log = logService.getLogById(id);

        jobPublisher.publish(log.getId());

        return ResponseEntity.accepted()
                .body("Analysis job accepted for processing");
    }

    @GetMapping("/{id}/analysis")
    public ResponseEntity<LogAnalysisDTO> getAnalysis(@PathVariable Long id) {
        LogAnalysis analysis = analysisService.getAnalysis(id);
        return ResponseEntity.ok(toDto(analysis));
    }

    @PostMapping("/{id}/analysis/sync")
    public ResponseEntity<LogAnalysisDTO> analyzeSync(@PathVariable Long id) {
        Log log = logService.getLogById(id);
        LogAnalysis analysis = analysisService.analyzeLog(log);
        return ResponseEntity.ok(toDto(analysis));
    }

    private LogEntryDTO toDto(Log log) {
        LogEntryDTO dto = new LogEntryDTO();
        dto.setId(log.getId());
        dto.setServiceName(log.getServiceName());
        dto.setLevel(log.getLevel());
        dto.setMessage(log.getMessage());
        dto.setTimestamp(log.getTimestamp());
        return dto;
    }

    private LogAnalysisDTO toDto(LogAnalysis analysis) {
        LogAnalysisDTO dto = new LogAnalysisDTO();
        dto.setId(analysis.getId());
        dto.setLogId(analysis.getLog().getId());
        dto.setAnalysis(analysis.getAnalysis());
        dto.setPossibleFix(analysis.getPossibleFix());
        dto.setSeverity(analysis.getSeverity());
        dto.setConfidence(analysis.getConfidence());
        dto.setSource(analysis.getSource());
        dto.setStatus(analysis.getStatus());
        dto.setCreatedAt(analysis.getCreatedAt());
        dto.setCompletedAt(analysis.getCompletedAt());
        return dto;
    }
}