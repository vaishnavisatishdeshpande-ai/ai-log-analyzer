package com.ailoganalyzer.service;

import com.ailoganalyzer.entity.Log;
import com.ailoganalyzer.exception.LogNotFoundException;
import com.ailoganalyzer.repository.LogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LogService {

    private final LogRepository logRepository;

    public LogService(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    public Log saveLog(Log log) {
        return logRepository.save(log);
    }

    public List<Log> getAllLogs() {
        return logRepository.findAll();
    }

    public List<Log> getLogsByServiceName(
            String serviceName
    ) {

        return logRepository.findByServiceName(
                serviceName
        );
    }

    public void deleteLog(Long id) {
        logRepository.deleteById(id);
    }

    public Log getLogById(Long id) {

        return logRepository.findById(id)
                .orElseThrow(() ->
                        new LogNotFoundException(
                                "Log not found with id: " + id
                        )
                );
    }

    public Log saveLog(
            com.ailoganalyzer.dto.LogEntryDTO dto
    ) {

        Log log = new Log();

        log.setServiceName(dto.getServiceName());
        log.setLevel(dto.getLevel());
        log.setMessage(dto.getMessage());

        return logRepository.save(log);
    }
}