# Testing Guide for Phase 3 (Async & Scalability)

## 1. Manual Testing with cURL

### 1.1 Create a Log

```bash
curl -X POST http://localhost:8080/logs \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "payment-service",
    "level": "ERROR",
    "message": "OutOfMemory exception in thread pool"
  }'
```

Response:
```json
{
  "id": 1,
  "serviceName": "payment-service",
  "level": "ERROR",
  "message": "OutOfMemory exception in thread pool",
  "timestamp": "2025-04-08T10:30:00"
}
```

### 1.2 Submit for Async Analysis (Non-Blocking)

```bash
curl -X POST http://localhost:8080/logs/1/analysis \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n"
```

Response (HTTP 202 Accepted - immediately):
```json
{
  "id": 101,
  "logId": 1,
  "analysis": null,
  "severity": null,
  "confidence": null,
  "source": null,
  "status": "PENDING",
  "createdAt": "2025-04-08T10:30:00",
  "completedAt": null
}
```

**Key Point:** Response returns within 50-100ms, not 5+ seconds like sync analysis

### 1.3 Poll for Analysis Status (Every 1 second)

```bash
# Loop until completion
for i in {1..20}; do
  echo "Attempt $i:"
  curl http://localhost:8080/logs/1/analysis | jq '.status'
  sleep 1
done
```

Watch status progression:
```
"PENDING"       # Still in queue
"PROCESSING"    # Worker picked up job
"PROCESSING"    # Still analyzing...
"COMPLETED"     # Analysis finished!
```

### 1.4 Final Result

```bash
curl http://localhost:8080/logs/1/analysis | jq '.'
```

Response after completion:
```json
{
  "id": 101,
  "logId": 1,
  "analysis": "OutOfMemory error detected in thread pool",
  "severity": "CRITICAL",
  "confidence": 0.95,
  "source": "RULE",
  "status": "COMPLETED",
  "createdAt": "2025-04-08T10:30:00",
  "completedAt": "2025-04-08T10:30:05"
}
```

---

## 2. Load Testing

### 2.1 Install Apache Bench (ab)

```bash
# macOS
brew install httpd

# Linux (Ubuntu)
sudo apt-get install apache2-utils
```

### 2.2 Load Test: Submit 100 Analyses in Parallel

```bash
# Create 100 logs first
for i in {1..100}; do
  curl -s -X POST http://localhost:8080/logs \
    -H "Content-Type: application/json" \
    -d "{\"serviceName\": \"service-$i\", \"level\": \"ERROR\", \"message\": \"Test error $i\"}" \
    > /dev/null
done

# Submit 100 analysis jobs (non-blocking)
ab -n 100 -c 10 \
  -p /dev/stdin \
  -T "application/json" \
  http://localhost:8080/logs/1/analysis << EOF
EOF
```

Expected Results:
```
Requests per second:    1000+ (very fast, returns immediately)
Time per request:       5-10ms (vs 5000ms for sync)
Concurrent requests:    10 (test 10 at a time)
```

### 2.3 Load Test: Sync vs Async Comparison

```bash
# Synchronous (blocking)
time ab -n 10 -c 1 http://localhost:8080/logs/1/analysis/sync

# Asynchronous (non-blocking)
time ab -n 10 -c 1 http://localhost:8080/logs/1/analysis
```

Expected Results:
```
Sync:  Real: 1m 5s (10 requests × 6.5s each)
Async: Real: 0.5s (10 requests × 50ms each)
Speedup: 130x faster response time
```

---

## 3. Metrics Testing

### 3.1 View All Available Metrics

```bash
curl http://localhost:8080/actuator/metrics | jq '.'
```

Output:
```json
{
  "names": [
    "analysis.ai.count",
    "analysis.ai.latency",
    "analysis.failure.count",
    "analysis.job.count",
    "analysis.rule.count",
    "analysis.rule.latency",
    "analysis.success.count",
    "severity.critical.count",
    "severity.high.count",
    "severity.low.count",
    "severity.medium.count"
  ]
}
```

### 3.2 Check Specific Metric (Job Count)

```bash
curl http://localhost:8080/actuator/metrics/analysis.job.count | jq '.'
```

Response:
```json
{
  "name": "analysis.job.count",
  "description": "Total number of analysis jobs submitted",
  "baseUnit": null,
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 42
    }
  ],
  "availableTags": []
}
```

### 3.3 Check AI Latency Percentiles

```bash
curl http://localhost:8080/actuator/metrics/analysis.ai.latency | jq '.'
```

Response:
```json
{
  "name": "analysis.ai.latency",
  "description": "Latency of AI analysis in milliseconds",
  "baseUnit": "milliseconds",
  "measurements": [
    {"statistic": "COUNT", "value": 10},
    {"statistic": "TOTAL_TIME", "value": 25000},
    {"statistic": "MAX", "value": 3500},
    {"statistic": "VALUE", "value": 2500}
  ],
  "availableTags": []
}
```

### 3.4 Monitor Severity Distribution

```bash
for level in critical high medium low; do
  echo -n "$level: "
  curl -s http://localhost:8080/actuator/metrics/severity.${level}.count | jq '.measurements[0].value'
done
```

Output:
```
critical: 5
high: 8
medium: 12
low: 25
```

---

## 4. Resilience Pattern Testing

### 4.1 Test Rate Limiter

The rate limiter allows 30 requests per minute (1 every 2 seconds).

```bash
# Quick script to test rate limiting
for i in {1..35}; do
  echo "Request $i:"
  curl -s -o /dev/null -w "HTTP %{http_code}\n" \
    -X POST http://localhost:8080/logs/1/analysis
  sleep 0.1  # 100ms between requests
done
```

Expected Behavior:
- Requests 1-30: **202 Accepted** (success)
- Requests 31-35: **429 Too Many Requests** (rate limited, waits up to 5s)

### 4.2 Test Circuit Breaker

Simulate AI service failure:

1. **Shut down or block OpenAI API** (mock failure)
2. **Submit 15+ analysis jobs**
3. **Watch circuit breaker behavior:**

```bash
# Monitor circuit breaker state
curl -s http://localhost:8080/actuator/health/aiAnalysisCircuitBreaker | jq '.'
```

Output - Circuit **Closed** (normal):
```json
{
  "status": "UP",
  "details": {
    "circuitBreaker": {
      "status": "CLOSED",
      "details": {
        "failureRate": "0%",
        "recordedCalls": 5
      }
    }
  }
}
```

Output - Circuit **Open** (50% failures detected):
```json
{
  "status": "DOWN",
  "details": {
    "circuitBreaker": {
      "status": "OPEN",
      "details": {
        "failureRate": "50%",
        "recordedCalls": 10,
        "remainingWaitDurationInOpenState": "25s"
      }
    }
  }
}
```

---

## 5. Status Tracking Testing

### 5.1 Test Status Progression

Create a simple status tracker:

```bash
#!/bin/bash

logId=1
echo "Submitting analysis for log $logId..."

# Submit async analysis
analysisId=$(curl -s -X POST http://localhost:8080/logs/$logId/analysis | jq '.id')
echo "Analysis ID: $analysisId"

# Track status every 500ms
start=$(date +%s%N | cut -b1-13)
while true; do
  status=$(curl -s http://localhost:8080/logs/$logId/analysis | jq -r '.status')
  elapsed=$(($(date +%s%N | cut -b1-13) - start))
  
  echo "[$elapsed ms] Status: $status"
  
  if [ "$status" = "COMPLETED" ] || [ "$status" = "FAILED" ]; then
    break
  fi
  
  sleep 0.5
done
```

Expected Output:
```
Submitting analysis for log 1...
Analysis ID: 101
[50 ms] Status: PENDING
[150 ms] Status: PROCESSING
[800 ms] Status: PROCESSING
[1500 ms] Status: PROCESSING
[3200 ms] Status: COMPLETED
Total time: 3.2 seconds (actual AI analysis)
```

---

## 6. Database Query Testing

### 6.1 Check Index Effectiveness

```sql
-- Verify indexes exist
SHOW INDEX FROM log_analysis;

-- Check query execution plan
EXPLAIN SELECT * FROM log_analysis 
WHERE log_id = 1 
ORDER BY created_at DESC 
LIMIT 1;

-- Expected: Uses index "idx_log_status_created"
```

### 6.2 Verify N+1 Prevention

```bash
# Enable SQL logging in application.yml
spring:
  jpa:
    properties:
      hibernate:
        format_sql: true
        show_sql: true
        use_sql_comments: true
```

Expected behavior:
```sql
-- Single query with JOIN (good)
SELECT ... FROM log_analysis l 
INNER JOIN log lg ON l.log_id = lg.id 
WHERE l.log_id = 1 
ORDER BY l.created_at DESC LIMIT 1;

-- NOT individual queries (bad)
SELECT * FROM log_analysis WHERE log_id = 1;
SELECT * FROM log WHERE id = 1;  -- Don't see this!
```

---

## 7. Cache Testing

### 7.1 Enable Cache Monitoring

```bash
# Check cache configuration
curl http://localhost:8080/actuator/health/cacheManagers | jq '.'
```

### 7.2 Test Cache Hit Rate

```bash
#!/bin/bash

# Create a log
logId=$(curl -s -X POST http://localhost:8080/logs \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "test",
    "level": "ERROR",
    "message": "OutOfMemory exception"
  }' | jq '.id')

echo "Log ID: $logId"

# First analysis (cache miss)
time curl -s http://localhost:8080/logs/$logId/analysis/sync > /dev/null
echo "First call (cache miss): ~2-5 seconds"

# Second analysis (cache hit)
time curl -s http://localhost:8080/logs/$logId/analysis/sync > /dev/null
echo "Second call (cache hit): <100ms"
```

Expected Results:
```
First call (cache miss): ~2-5 seconds
Second call (cache hit): <100ms
Speedup: 25-50x faster
```

---

## 8. Integration Test Example

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AsyncAnalysisIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private LogRepository logRepository;

    @Autowired
    private LogAnalysisRepository analysisRepository;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
    }

    @Test
    void testAsyncAnalysisFlow() throws InterruptedException {
        // 1. Create log
        Log log = new Log();
        log.setServiceName("test-service");
        log.setLevel("ERROR");
        log.setMessage("OutOfMemory exception in thread pool");
        log = logRepository.save(log);

        // 2. Submit async analysis (non-blocking)
        long startTime = System.currentTimeMillis();
        ResponseEntity<LogAnalysisDTO> response = restTemplate.postForEntity(
            baseUrl + "/logs/" + log.getId() + "/analysis",
            null,
            LogAnalysisDTO.class
        );

        // Should return immediately
        assertThat(System.currentTimeMillis() - startTime).isLessThan(100);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().getStatus()).isEqualTo(AnalysisStatus.PENDING);

        // 3. Poll for completion
        LogAnalysisDTO analysis = response.getBody();
        for (int i = 0; i < 30; i++) {
            ResponseEntity<LogAnalysisDTO> statusResponse = restTemplate.getForEntity(
                baseUrl + "/logs/" + log.getId() + "/analysis",
                LogAnalysisDTO.class
            );
            
            analysis = statusResponse.getBody();
            if (analysis.getStatus() == AnalysisStatus.COMPLETED) {
                break;
            }
            Thread.sleep(100);
        }

        // 4. Verify completion
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(analysis.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(analysis.getConfidence()).isGreaterThan(0.9);
    }

    @Test
    void testHighThroughputScenario() throws InterruptedException {
        // Submit 50 jobs in parallel
        List<Long> logIds = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Log log = new Log();
            log.setServiceName("service-" + i);
            log.setLevel("ERROR");
            log.setMessage("Test error " + i);
            logIds.add(logRepository.save(log).getId());
        }

        // All 50 requests should return quickly
        long startTime = System.currentTimeMillis();
        logIds.forEach(logId ->
            restTemplate.postForEntity(
                baseUrl + "/logs/" + logId + "/analysis",
                null,
                LogAnalysisDTO.class
            )
        );
        long duration = System.currentTimeMillis() - startTime;

        // Should handle all 50 in <5 seconds (non-blocking)
        assertThat(duration).isLessThan(5000);
        
        // Eventually all should complete
        Thread.sleep(10000);  // Wait for processing
        
        logIds.forEach(logId -> {
            ResponseEntity<LogAnalysisDTO> response = restTemplate.getForEntity(
                baseUrl + "/logs/" + logId + "/analysis",
                LogAnalysisDTO.class
            );
            assertThat(response.getBody().getStatus())
                .isIn(AnalysisStatus.COMPLETED, AnalysisStatus.FAILED);
        });
    }
}
```

---

## 9. Performance Benchmarking

### 9.1 Benchmark Script

```bash
#!/bin/bash

echo "=== Phase 3 Performance Benchmarks ==="
echo ""

# Create 100 test logs
echo "Creating 100 test logs..."
for i in {1..100}; do
  curl -s -X POST http://localhost:8080/logs \
    -H "Content-Type: application/json" \
    -d "{\"serviceName\": \"service-$i\", \"level\": \"ERROR\", \"message\": \"Test error $i\"}" \
    > /dev/null
done

echo "100 logs created."
echo ""

# Benchmark: Async submit (should be <100ms per request)
echo "=== Benchmark 1: Async Submit Throughput ==="
echo "Submitting 100 analysis jobs..."
time for i in {1..100}; do
  curl -s -X POST http://localhost:8080/logs/$i/analysis > /dev/null
done

# Benchmark: Status polling
echo ""
echo "=== Benchmark 2: Status Polling ==="
echo "Polling 100 analyses 5 times each..."
time for i in {1..100}; do
  for j in {1..5}; do
    curl -s http://localhost:8080/logs/$i/analysis > /dev/null
  done
done

# Benchmark: Metrics queries
echo ""
echo "=== Benchmark 3: Metrics Query ==="
time for i in {1..100}; do
  curl -s http://localhost:8080/actuator/metrics/analysis.job.count > /dev/null
done
```

---

## 10. Troubleshooting Tests

### 10.1 Debug: Check Thread Pool Status

```bash
# If experiencing delays, check thread pool:
curl http://localhost:8080/actuator/health | jq '.components.threadPoolTaskExecutor'
```

### 10.2 Debug: Verify Resilience Status

```bash
# Check circuit breaker
curl http://localhost:8080/actuator/health/aiAnalysisCircuitBreaker | jq '.'

# Check rate limiter
curl http://localhost:8080/actuator/health/aiAnalysisLimiter | jq '.'
```

### 10.3 Debug: Database Queries

```bash
# Enable query logging
curl -X POST http://localhost:8080/actuator/loggers/org.hibernate.SQL \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

---

## Summary

**Phase 3 Testing Covers:**
1. ✓ Async API (non-blocking submit + poll)
2. ✓ Status tracking (PENDING → PROCESSING → COMPLETED)
3. ✓ High throughput (100+ concurrent requests)
4. ✓ Resilience (rate limiting + circuit breaker)
5. ✓ Metrics (counters + latency percentiles)
6. ✓ Database performance (indexes + N+1 prevention)
7. ✓ Caching (hit rate optimization)

**Key Metrics to Monitor:**
- Submit latency: <100ms (non-blocking)
- Processing latency: 1-5 seconds (AI analysis)
- Throughput: 100+ requests/second
- Success rate: >95% (with fallback)
- Circuit breaker: Stays CLOSED under normal load

