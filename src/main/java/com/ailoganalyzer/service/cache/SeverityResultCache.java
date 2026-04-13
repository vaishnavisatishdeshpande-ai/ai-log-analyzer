package com.ailoganalyzer.service.cache;

import com.ailoganalyzer.enums.Severity;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Caching service for severity analysis results.
 *
 * Caches severity determinations for identical log messages.
 * Useful when the same error occurs multiple times (common in logs).
 *
 * Uses deterministic message hash as cache key.
 * Cache is managed by Spring Cache abstraction (can use Redis, etc.).
 */
@Service
public class SeverityResultCache {

    /**
     * Cache a severity result by message hash.
     *
     * For identical messages, returns cached result without re-analysis.
     * Can significantly reduce AI calls for repeated errors.
     *
     * Cache key is SHA-256 hash of message for:
     * - Deterministic caching
     * - Safe for all message contents
     * - Consistent across restarts
     *
     * @param messageHash SHA-256 hash of log message
     * @param severity the determined severity
     * @return the severity
     */
    @Cacheable(value = "severityCache", key = "#messageHash")
    public Severity getCachedSeverity(String messageHash, Severity severity) {
        // This is intentionally simple - the @Cacheable annotation does the heavy lifting
        // On cache hit, this method is not called
        // On cache miss, result is stored and returned
        return severity;
    }

    /**
     * Generates a deterministic hash for a message.
     * Same message always produces same hash.
     *
     * @param message the log message
     * @return SHA-256 hash as hex string
     */
    public String generateMessageHash(String message) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // Should never happen - SHA-256 is always available
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}

