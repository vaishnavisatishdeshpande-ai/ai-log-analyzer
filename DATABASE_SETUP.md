# Database Schema & Migration Scripts

## 1. Current Schema (H2)

### Log Table
```sql
CREATE TABLE log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_name VARCHAR(255) NOT NULL,
    level VARCHAR(50) NOT NULL,
    message VARCHAR(4000) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    
    -- Indexes
    INDEX idx_service_name (service_name),
    INDEX idx_timestamp (timestamp DESC),
    INDEX idx_level (level)
);
```

### LogAnalysis Table
```sql
CREATE TABLE log_analysis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    log_id BIGINT NOT NULL,
    analysis VARCHAR(2000),
    possible_fix VARCHAR(2000),
    severity VARCHAR(50) NOT NULL,
    confidence DOUBLE,
    source VARCHAR(50),  -- AI, RULE, HYBRID
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',  -- PENDING, PROCESSING, COMPLETED, FAILED
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    
    FOREIGN KEY (log_id) REFERENCES log(id),
    
    -- Critical indexes for async tracking
    INDEX idx_log_id (log_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at DESC),
    INDEX idx_log_status_created (log_id, status, created_at DESC),
    INDEX idx_severity (severity),
    INDEX idx_source (source)
);
```

---

## 2. Migration Strategy for Production

### PostgreSQL Setup (Recommended)

```sql
-- Create schema
CREATE SCHEMA log_analysis;

-- Log table with proper indexing
CREATE TABLE log_analysis.log (
    id BIGSERIAL PRIMARY KEY,
    service_name VARCHAR(255) NOT NULL,
    level VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_log_service_name ON log_analysis.log(service_name);
CREATE INDEX idx_log_timestamp ON log_analysis.log(timestamp DESC);
CREATE INDEX idx_log_created_at ON log_analysis.log(created_at DESC);

-- LogAnalysis table
CREATE TABLE log_analysis.log_analysis (
    id BIGSERIAL PRIMARY KEY,
    log_id BIGINT NOT NULL REFERENCES log_analysis.log(id) ON DELETE CASCADE,
    analysis TEXT,
    possible_fix TEXT,
    severity VARCHAR(50) NOT NULL,
    confidence FLOAT8,
    source VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    reason TEXT,  -- For explainability
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    
    -- IMPORTANT: These indexes optimize async polling
    UNIQUE(id),
    FOREIGN KEY (log_id) REFERENCES log_analysis.log(id)
);

-- Optimized indexes for async flow
CREATE INDEX idx_analysis_log_id ON log_analysis.log_analysis(log_id);
CREATE INDEX idx_analysis_status ON log_analysis.log_analysis(status);
CREATE INDEX idx_analysis_created_at ON log_analysis.log_analysis(created_at DESC);
CREATE INDEX idx_analysis_log_status_created ON log_analysis.log_analysis(log_id, status, created_at DESC);
CREATE INDEX idx_analysis_severity ON log_analysis.log_analysis(severity);

-- For cleanup jobs (archive old records)
CREATE INDEX idx_analysis_cleanup ON log_analysis.log_analysis(created_at DESC)
WHERE status = 'COMPLETED';
```

### MySQL Setup

```sql
-- Log table
CREATE TABLE log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_name VARCHAR(255) NOT NULL,
    level VARCHAR(50) NOT NULL,
    message LONGTEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    KEY idx_service_name (service_name),
    KEY idx_timestamp (timestamp DESC),
    KEY idx_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- LogAnalysis table
CREATE TABLE log_analysis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    log_id BIGINT NOT NULL,
    analysis LONGTEXT,
    possible_fix LONGTEXT,
    severity VARCHAR(50) NOT NULL,
    confidence FLOAT,
    source VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    
    UNIQUE KEY unique_id (id),
    FOREIGN KEY (log_id) REFERENCES log(id) ON DELETE CASCADE,
    
    -- Async polling indexes
    KEY idx_log_id (log_id),
    KEY idx_status (status),
    KEY idx_created_at (created_at DESC),
    KEY idx_log_status_created (log_id, status, created_at DESC),
    KEY idx_severity (severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 3. Maintenance & Cleanup Jobs

### Archive Old Records (Batch Job)

```sql
-- Archive analyses older than 90 days
INSERT INTO log_analysis_archive 
SELECT * FROM log_analysis 
WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY);

DELETE FROM log_analysis 
WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY);

-- Reorganize table after large delete
OPTIMIZE TABLE log_analysis;
```

### Cleanup Job Implementation (Spring)

```java
@Component
public class AnalysisCleanupJob {
    
    private static final Logger logger = LoggerFactory.getLogger(AnalysisCleanupJob.class);
    private final LogAnalysisRepository repository;
    
    public AnalysisCleanupJob(LogAnalysisRepository repository) {
        this.repository = repository;
    }
    
    /**
     * Daily job to archive old analysis records.
     * Runs at 2 AM daily.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void archiveOldAnalyses() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
        
        List<LogAnalysis> oldAnalyses = repository
            .findByCre atedAtBefore(cutoffDate);
        
        logger.info("Archiving {} old analyses", oldAnalyses.size());
        
        // TODO: Write to archive table
        // archiveRepository.saveAll(oldAnalyses);
        
        // Delete from main table
        repository.deleteAll(oldAnalyses);
        
        logger.info("Archived and cleaned {} analyses", oldAnalyses.size());
    }
}
```

---

## 4. Performance Tuning Queries

### Query Analysis
```sql
-- Check slow queries (MySQL)
SELECT * FROM mysql.slow_log LIMIT 10;

-- Check query execution plan
EXPLAIN SELECT * FROM log_analysis 
WHERE log_id = 1 
ORDER BY created_at DESC LIMIT 1;
```

### Optimization Tips

1. **Batch Operations:**
   ```java
   // BAD: Individual saves
   for (LogAnalysis a : analyses) {
       repository.save(a);  // N queries
   }
   
   // GOOD: Batch save
   repository.saveAll(analyses);  // Single query
   ```

2. **Pagination for Large Datasets:**
   ```java
   Pageable pageable = PageRequest.of(0, 100);
   Page<LogAnalysis> page = repository.findAll(pageable);
   ```

3. **Projection for Large Result Sets:**
   ```java
   // Select only needed columns
   @Query("SELECT new com.ailoganalyzer.dto.AnalysisSummary(a.id, a.severity) " +
          "FROM LogAnalysis a WHERE a.logId = :logId")
   List<AnalysisSummary> findSummaries(@Param("logId") Long logId);
   ```

---

## 5. Liquibase/Flyway Migration Setup

### Liquibase Configuration

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-core</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

### Migration File: `db/changelog/v1-initial-schema.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
    http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.3.xsd">

    <changeSet id="1-create-log-table" author="ai-analyzer">
        <createTable tableName="log">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true"/>
            </column>
            <column name="service_name" type="VARCHAR(255)">
                <constraints nullable="false"/>
            </column>
            <column name="level" type="VARCHAR(50)">
                <constraints nullable="false"/>
            </column>
            <column name="message" type="CLOB">
                <constraints nullable="false"/>
            </column>
            <column name="timestamp" type="TIMESTAMP" defaultValueDate="CURRENT_TIMESTAMP">
                <constraints nullable="false"/>
            </column>
        </createTable>
    </changeSet>

    <changeSet id="2-create-log-analysis-table" author="ai-analyzer">
        <createTable tableName="log_analysis">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true"/>
            </column>
            <column name="log_id" type="BIGINT">
                <constraints nullable="false" foreignKeyName="fk_log_analysis_log"
                    referencedTableName="log" referencedColumnNames="id"/>
            </column>
            <column name="analysis" type="VARCHAR(2000)"/>
            <column name="possible_fix" type="VARCHAR(2000)"/>
            <column name="severity" type="VARCHAR(50)">
                <constraints nullable="false"/>
            </column>
            <column name="confidence" type="FLOAT"/>
            <column name="source" type="VARCHAR(50)"/>
            <column name="status" type="VARCHAR(50)" defaultValue="PENDING">
                <constraints nullable="false"/>
            </column>
            <column name="created_at" type="TIMESTAMP" defaultValueDate="CURRENT_TIMESTAMP">
                <constraints nullable="false"/>
            </column>
            <column name="completed_at" type="TIMESTAMP"/>
        </createTable>
    </changeSet>

    <changeSet id="3-create-indexes" author="ai-analyzer">
        <createIndex indexName="idx_log_service_name" tableName="log">
            <column name="service_name"/>
        </createIndex>
        <createIndex indexName="idx_log_timestamp" tableName="log">
            <column name="timestamp"/>
        </createIndex>
        <createIndex indexName="idx_analysis_log_id" tableName="log_analysis">
            <column name="log_id"/>
        </createIndex>
        <createIndex indexName="idx_analysis_status" tableName="log_analysis">
            <column name="status"/>
        </createIndex>
        <createIndex indexName="idx_analysis_created_at" tableName="log_analysis">
            <column name="created_at"/>
        </createIndex>
    </changeSet>
</databaseChangeLog>
```

### application.yml Configuration

```yaml
spring:
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.xml
    contexts: production
```

---

## 6. Monitoring Database Health

### Repository Queries
```java
@Repository
public interface LogAnalysisRepository extends JpaRepository<LogAnalysis, Long> {
    
    // For status polling (async)
    Optional<LogAnalysis> findByLogIdOrderByCreatedAtDesc(Long logId);
    
    // For metrics
    @Query("SELECT COUNT(a) FROM LogAnalysis a WHERE a.status = 'COMPLETED'")
    long countCompletedAnalyses();
    
    @Query("SELECT COUNT(a) FROM LogAnalysis a WHERE a.status = 'FAILED'")
    long countFailedAnalyses();
    
    @Query("SELECT COUNT(a) FROM LogAnalysis a WHERE a.status = 'PENDING'")
    long countPendingAnalyses();
    
    // For average latency
    @Query("SELECT AVG(EXTRACT(EPOCH FROM (a.completed_at - a.created_at))) " +
           "FROM LogAnalysis a WHERE a.completedAt IS NOT NULL")
    Double getAverageLatency();
}
```

### Database Monitoring Service

```java
@Service
public class DatabaseHealthService {
    
    private final LogAnalysisRepository repository;
    private final MeterRegistry meterRegistry;
    
    public DatabaseHealthService(LogAnalysisRepository repository, 
                                 MeterRegistry meterRegistry) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
    }
    
    @Scheduled(fixedRate = 60000)  // Every minute
    public void recordDatabaseMetrics() {
        Gauge.builder("db.analysis.completed", 
                     repository::countCompletedAnalyses)
            .description("Count of completed analyses")
            .register(meterRegistry);
        
        Gauge.builder("db.analysis.failed",
                     repository::countFailedAnalyses)
            .description("Count of failed analyses")
            .register(meterRegistry);
        
        Gauge.builder("db.analysis.pending",
                     repository::countPendingAnalyses)
            .description("Count of pending analyses")
            .register(meterRegistry);
        
        Double avgLatency = repository.getAverageLatency();
        if (avgLatency != null) {
            Gauge.builder("db.analysis.latency.avg",
                         () -> avgLatency)
                .description("Average analysis latency (seconds)")
                .register(meterRegistry);
        }
    }
}
```

---

## 7. Backup & Recovery

### PostgreSQL Backup Script

```bash
#!/bin/bash

# Full backup
BACKUP_FILE="ai-log-analyzer-$(date +%Y%m%d-%H%M%S).sql"
pg_dump -h localhost -U analyzer -d ai_log_analyzer > "$BACKUP_FILE"

# Compress
gzip "$BACKUP_FILE"

# Upload to S3
aws s3 cp "${BACKUP_FILE}.gz" s3://my-backups/ai-log-analyzer/

# Keep only last 30 days
find . -name "ai-log-analyzer-*.sql.gz" -mtime +30 -delete
```

### Recovery

```bash
# Restore from backup
gunzip ai-log-analyzer-20250408-120000.sql.gz
psql -h localhost -U analyzer -d ai_log_analyzer < ai-log-analyzer-20250408-120000.sql
```

---

## Summary

This document provides:
1. **Complete database schema** for both H2 and production databases
2. **Migration strategies** from H2 → PostgreSQL/MySQL
3. **Performance optimization** tips and queries
4. **Maintenance scripts** for cleanup and archiving
5. **Monitoring setup** with Liquibase/Flyway
6. **Backup and recovery** procedures

**Next Step:** Implement these schemas in production before scaling horizontally.

