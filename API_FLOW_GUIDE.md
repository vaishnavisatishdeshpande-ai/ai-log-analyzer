# API Flow & Integration Guide

## 📡 Complete Request/Response Flow

### Scenario 1: Async Analysis (Recommended)

```
┌─────────────────────────────────────────────────────────────────┐
│ CLIENT APPLICATION                                              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 1. Create Log (Blocking)
                            ▼
        ┌───────────────────────────────────────┐
        │ POST /logs                            │
        │ Content-Type: application/json        │
        │ {                                     │
        │   "serviceName": "payment-service",   │
        │   "level": "ERROR",                   │
        │   "message": "OutOfMemory exception"  │
        │ }                                     │
        └───────────────────────────────────────┘
                            │
                            │ Response (HTTP 200)
                            │ {
                            │   "id": 1,
                            │   "serviceName": "payment-service",
                            │   ...
                            │ }
                            ▼
        ┌───────────────────────────────────────┐
        │ 2. Submit for Async Analysis          │
        │ (Non-Blocking - Returns Immediately) │
        │ POST /logs/1/analysis                 │
        └───────────────────────────────────────┘
                            │
                            │ Response (HTTP 202 Accepted)
                            │ Time: <100ms
                            │ {
                            │   "id": 101,
                            │   "logId": 1,
                            │   "status": "PENDING",
                            │   "createdAt": "2025-04-08T10:30:00"
                            │ }
                            ▼
        ┌───────────────────────────────────────┐
        │ 3. Client Can Proceed Immediately    │
        │ While analysis runs in background     │
        └───────────────────────────────────────┘
                            │
                            │ Loop: Poll every 500ms-1s
                            ▼
        ┌───────────────────────────────────────┐
        │ GET /logs/1/analysis                  │
        └───────────────────────────────────────┘
                            │
        ┌─────────────────────────────────────────────────────────┐
        │ Response Option 1: Still Processing (HTTP 200)          │
        │ {                                                       │
        │   "status": "PROCESSING",                               │
        │   "severity": null                                      │
        │ }                                                       │
        │                                                         │
        │ Response Option 2: Complete (HTTP 200)                 │
        │ {                                                       │
        │   "status": "COMPLETED",                                │
        │   "severity": "CRITICAL",                               │
        │   "confidence": 0.95,                                   │
        │   "analysis": "OutOfMemory error detected...",          │
        │   "source": "RULE",                                     │
        │   "completedAt": "2025-04-08T10:30:05"                │
        │ }                                                       │
        └─────────────────────────────────────────────────────────┘
```

---

## 🔄 Backend Processing Flow

```
┌──────────────────────────────────────────────────────────────────┐
│ REQUEST: POST /logs/{id}/analysis                                │
│ (HTTP 202 Accepted - Returns Immediately)                        │
└────────────────────┬─────────────────────────────────────────────┘
                     │
                     ▼
        ┌────────────────────────────────┐
        │ LogController                  │
        │ analyzeAsync(logId)            │
        └────────────┬───────────────────┘
                     │
                     ├─→ 1. logService.getLogById(logId)
                     │      Fetch log from database
                     │
                     ├─→ 2. analysisService.createPendingAnalysis(log)
                     │      Create LogAnalysis record with PENDING status
                     │      Save to database (timestamp: now)
                     │
                     ├─→ 3. jobPublisher.publish(logId)
                     │      Submit job for async processing
                     │      → AsyncAnalysisJobPublisher.publish()
                     │
                     ├─→ 4. metricsRecorder.recordJobSubmitted()
                     │      Increment analysis.job.count counter
                     │
                     ▼
        Return HTTP 202 + LogAnalysisDTO (PENDING status)
        (Takes <100ms total)
```

### Background Processing (Async Thread)

```
        AsyncAnalysisJobPublisher.publish(logId)
                     │
                     ▼
        ThreadPoolTaskExecutor picks up job
        (1 of 16 threads)
                     │
                     ▼
        ┌────────────────────────────────┐
        │ AnalysisWorker.analyzeLog()    │
        └────────────────┬───────────────┘
                         │
           ┌─────────────┴──────────────┐
           │                            │
           ▼                            ▼
    ┌────────────────┐        ┌────────────────┐
    │ Try AI:        │        │ If AI Fails:   │
    │                │        │                │
    │ 1. Status →    │        │ 1. Status →    │
    │    PROCESSING  │        │    PROCESSING  │
    │                │        │                │
    │ 2. Apply       │        │ 2. RuleEngine  │
    │    Rate        │        │    Fallback    │
    │    Limiter     │        │                │
    │    (30 req/min)│        │ 3. Analyze     │
    │                │        │    message     │
    │ 3. Apply       │        │    <5ms        │
    │    Circuit     │        │                │
    │    Breaker     │        │ 4. Update      │
    │                │        │    status      │
    │ 4. Call        │        │    COMPLETED   │
    │    aiService   │        │                │
    │    2-5 seconds │        └────────────────┘
    │                │                │
    │ 5. Enrich with │                │
    │    Hybrid      │                │
    │    Severity    │                │
    │                │                │
    │ 6. Update      │                │
    │    Status →    │                │
    │    COMPLETED   │                │
    │                │                │
    └────────────────┘                │
           │                          │
           └──────────────┬───────────┘
                          │
                          ▼
        ┌────────────────────────────────┐
        │ Common: Update Database        │
        │ 1. Save LogAnalysis result     │
        │ 2. Set completedAt timestamp   │
        │ 3. Save severity, confidence   │
        │ 4. Save source (AI/RULE)       │
        └────────────────┬───────────────┘
                         │
                         ▼
        ┌────────────────────────────────┐
        │ Record Metrics                 │
        │ 1. recordSuccess()             │
        │ 2. recordAiAnalysis()          │
        │ 3. recordSeverity(severity)    │
        │ 4. stopAiLatency(timer)        │
        └────────────────┬───────────────┘
                         │
                         ▼
        Status in database: COMPLETED
        Ready for polling client
```

---

## 🎯 Data Models

### Request: Create Log

```json
{
  "serviceName": "payment-service",
  "level": "ERROR",
  "message": "Timeout connecting to database. Connection timeout after 30s"
}
```

### Response: Created Log

```json
{
  "id": 1,
  "serviceName": "payment-service",
  "level": "ERROR",
  "message": "Timeout connecting to database. Connection timeout after 30s",
  "timestamp": "2025-04-08T10:30:00"
}
```

### Request: Submit Analysis

```
POST /logs/1/analysis
(No body required)
```

### Response: Analysis Submitted (HTTP 202)

```json
{
  "id": 101,
  "logId": 1,
  "analysis": null,
  "possibleFix": null,
  "severity": null,
  "confidence": null,
  "source": null,
  "status": "PENDING",
  "reason": null,
  "createdAt": "2025-04-08T10:30:00",
  "completedAt": null
}
```

### Response: Get Status - Processing

```json
{
  "id": 101,
  "logId": 1,
  "analysis": null,
  "possibleFix": null,
  "severity": null,
  "confidence": null,
  "source": null,
  "status": "PROCESSING",
  "reason": null,
  "createdAt": "2025-04-08T10:30:00",
  "completedAt": null
}
```

### Response: Get Status - Completed

```json
{
  "id": 101,
  "logId": 1,
  "analysis": "Database connection timeout detected. The application attempted to connect to the database but exceeded the 30-second timeout threshold.",
  "possibleFix": "1. Check database server status\n2. Verify network connectivity\n3. Review database logs\n4. Consider increasing timeout threshold",
  "severity": "HIGH",
  "confidence": 0.85,
  "source": "RULE",
  "status": "COMPLETED",
  "reason": "Pattern matched: timeout|timed out|exceeded",
  "createdAt": "2025-04-08T10:30:00",
  "completedAt": "2025-04-08T10:30:05"
}
```

### Response: Get Status - Failed

```json
{
  "id": 101,
  "logId": 1,
  "analysis": "Analysis could not be completed due to system error",
  "possibleFix": "Please check system logs and try again",
  "severity": "LOW",
  "confidence": 0.5,
  "source": "RULE",
  "status": "FAILED",
  "reason": null,
  "createdAt": "2025-04-08T10:30:00",
  "completedAt": "2025-04-08T10:30:10"
}
```

---

## 🔗 Status Transitions

```
Database Record Lifecycle:

CREATE ─→ INSERT WITH STATUS=PENDING
           │
           │ (LogController creates)
           │
           ├─→ SELECT status (HTTP 202 response)
           │   Return immediately
           │
           ▼ (AsyncAnalysisJobPublisher picks up)
           
UPDATE ─→ STATUS=PROCESSING
           │
           │ (Worker starts analyzing)
           │
           ├─→ SELECT status (client polls)
           │   Still see PROCESSING
           │
           ▼ (Worker completes)
           
UPDATE ─→ STATUS=COMPLETED (with result)
           OR
           STATUS=FAILED (on error)
           │
           ├─→ SELECT status (client polls)
           │   See final result
           │
           ▼ (Client shows result)
           
FINAL ──→ Record complete, timestamps set
```

---

## 📊 Metrics Collection Points

```
┌─────────────────────┐
│  API Submission     │  recordJobSubmitted()
│  (HTTP 202)         │  analysis.job.count++
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Rate Limiter Check  │  (Built-in)
│ (30 req/min)        │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Circuit Breaker     │  (Built-in)
│ (50% threshold)     │  Automatic fallback
└──────────┬──────────┘
           │
     ┌─────┴──────────────┐
     │                    │
     ▼                    ▼
┌──────────────┐    ┌──────────────┐
│ AI Analysis  │    │ Rule Fallback│
│ (2-5s)       │    │ (<5ms)       │
└──┬───────────┘    └──┬───────────┘
   │                   │
   │recordAiLatency()   │recordRuleLatency()
   │                   │
   │recordAiAnalysis()  │recordRuleAnalysis()
   │                   │
   └─────────┬─────────┘
             │
             ▼
    ┌─────────────────┐
    │ Success/Failure │
    │ recordSuccess() │
    │ recordFailure() │
    └────────┬────────┘
             │
             ▼
    ┌─────────────────┐
    │ Severity Count  │
    │recordSeverity() │
    │(CRITICAL/HIGH/  │
    │ MEDIUM/LOW)     │
    └─────────────────┘
```

---

## 🚀 Client Integration Pattern

### Java Client Example

```java
public class LogAnalysisClient {
    
    private final RestTemplate restTemplate;
    
    public void analyzeLogAsync(Long logId) {
        // 1. Submit analysis (non-blocking)
        ResponseEntity<LogAnalysisDTO> response = restTemplate.postForEntity(
            "http://localhost:8080/logs/{logId}/analysis",
            null,
            LogAnalysisDTO.class,
            logId
        );
        
        assert response.getStatusCode() == HttpStatus.ACCEPTED; // 202
        LogAnalysisDTO pending = response.getBody();
        assert pending.getStatus() == AnalysisStatus.PENDING;
        
        // 2. Poll until completion
        schedulePolling(logId);
    }
    
    private void schedulePolling(Long logId) {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        
        executor.scheduleAtFixedRate(() -> {
            ResponseEntity<LogAnalysisDTO> response = restTemplate.getForEntity(
                "http://localhost:8080/logs/{logId}/analysis",
                LogAnalysisDTO.class,
                logId
            );
            
            LogAnalysisDTO analysis = response.getBody();
            
            if (analysis.getStatus() == AnalysisStatus.COMPLETED) {
                System.out.println("Analysis complete: " + analysis.getSeverity());
                onAnalysisComplete(analysis);
            } else if (analysis.getStatus() == AnalysisStatus.FAILED) {
                System.out.println("Analysis failed");
                onAnalysisFailed(analysis);
            }
            // Otherwise still PENDING or PROCESSING
        }, 0, 500, TimeUnit.MILLISECONDS); // Poll every 500ms
    }
    
    private void onAnalysisComplete(LogAnalysisDTO analysis) {
        // Handle result
        System.out.println("Severity: " + analysis.getSeverity());
        System.out.println("Analysis: " + analysis.getAnalysis());
        System.out.println("Fix: " + analysis.getPossibleFix());
    }
}
```

### Python Client Example

```python
import requests
import time
import json

class LogAnalysisClient:
    def __init__(self, base_url="http://localhost:8080"):
        self.base_url = base_url
    
    def analyze_log_async(self, log_id):
        """Submit analysis and poll for result"""
        
        # 1. Submit (returns immediately with 202)
        response = requests.post(
            f"{self.base_url}/logs/{log_id}/analysis",
            timeout=5
        )
        assert response.status_code == 202
        print("Analysis submitted (202 Accepted)")
        
        # 2. Poll until complete
        max_attempts = 60
        for attempt in range(max_attempts):
            response = requests.get(
                f"{self.base_url}/logs/{log_id}/analysis"
            )
            analysis = response.json()
            
            status = analysis.get('status')
            print(f"Attempt {attempt+1}: {status}")
            
            if status == "COMPLETED":
                print(f"✓ Severity: {analysis['severity']}")
                print(f"✓ Analysis: {analysis['analysis']}")
                return analysis
            elif status == "FAILED":
                print(f"✗ Analysis failed")
                return analysis
            
            time.sleep(0.5)  # Poll every 500ms
        
        raise TimeoutError("Analysis did not complete in 30 seconds")

# Usage
client = LogAnalysisClient()
result = client.analyze_log_async(log_id=1)
```

### cURL Bash Script

```bash
#!/bin/bash

LOG_ID=$1

# 1. Submit async analysis
echo "Submitting analysis for log $LOG_ID..."
RESPONSE=$(curl -s -X POST http://localhost:8080/logs/$LOG_ID/analysis)
ANALYSIS_ID=$(echo $RESPONSE | jq '.id')
echo "Analysis ID: $ANALYSIS_ID"

# 2. Poll until complete
TIMEOUT=60
START=$(date +%s)
while true; do
    STATUS=$(curl -s http://localhost:8080/logs/$LOG_ID/analysis | jq -r '.status')
    ELAPSED=$(($(date +%s) - START))
    
    echo "[$ELAPSED s] Status: $STATUS"
    
    if [ "$STATUS" = "COMPLETED" ] || [ "$STATUS" = "FAILED" ]; then
        echo "Done!"
        curl -s http://localhost:8080/logs/$LOG_ID/analysis | jq '.'
        break
    fi
    
    if [ $ELAPSED -gt $TIMEOUT ]; then
        echo "Timeout after $TIMEOUT seconds"
        exit 1
    fi
    
    sleep 0.5
done
```

---

## 🔄 Concurrent Request Handling

### Before (Blocking)
```
Request 1: Thread 1 ─┐
                     ├→ 1 thread busy for 5 seconds
Request 2: Waiting  ─┘
                     5000ms total

Max throughput: 1 req/5s = 0.2 req/s
```

### After (Non-Blocking)
```
Request 1: Thread 1 ─→ 50ms (submit + create record)
Request 2: Thread 2 ─→ 50ms (submit + create record)
Request 3: Thread 3 ─→ 50ms (submit + create record)
...
Request 100: Thread 100 → 50ms

Max throughput: 100 requests in 50ms = 2000 req/s
Improvement: 10,000x throughput!
```

---

## 📋 Integration Checklist

### For Your Application
- [ ] Have log creation endpoint working
- [ ] Capture service name, level, message
- [ ] Submit to `POST /logs/{id}/analysis`
- [ ] Store analysis ID from response
- [ ] Poll `GET /logs/{id}/analysis` until complete
- [ ] Handle all 4 statuses (PENDING, PROCESSING, COMPLETED, FAILED)
- [ ] Display results to user when COMPLETED
- [ ] Implement timeout handling (>1 minute)

### For Monitoring
- [ ] Monitor `analysis.job.count` (submission rate)
- [ ] Monitor `analysis.success.count` (completion rate)
- [ ] Monitor `analysis.failure.count` (error rate)
- [ ] Monitor `analysis.ai.latency` (p95, p99)
- [ ] Alert if failure rate > 10%
- [ ] Alert if p99 latency > 5 seconds
- [ ] Alert if circuit breaker opens

---

## 📞 Error Handling

### Rate Limit Exceeded
```
Status: 429 Too Many Requests
Message: "Rate limiter permits not available"

Action: 
- Exponential backoff
- Wait 1-5 seconds
- Retry submission
```

### Circuit Breaker Open
```
Status: 503 Service Unavailable
Message: "Circuit breaker is open"

Action:
- Automatic fallback to rule engine
- Retry after 30 seconds
- Check AI service status
```

### Database Unavailable
```
Status: 500 Internal Server Error
Message: "Could not connect to database"

Action:
- Check database connectivity
- Verify credentials
- Check database logs
```

---

## ✅ Integration Validation

```bash
#!/bin/bash

echo "=== Phase 3 Integration Validation ==="

# 1. Check API health
echo "1. Health check..."
curl -s http://localhost:8080/actuator/health | jq '.status'

# 2. Create a test log
echo "2. Create test log..."
LOG=$(curl -s -X POST http://localhost:8080/logs \
  -H "Content-Type: application/json" \
  -d '{"serviceName":"test","level":"ERROR","message":"Test error"}')
LOG_ID=$(echo $LOG | jq '.id')
echo "Created log: $LOG_ID"

# 3. Submit async analysis
echo "3. Submit async analysis..."
ANALYSIS=$(curl -s -X POST http://localhost:8080/logs/$LOG_ID/analysis)
echo "Status: $(echo $ANALYSIS | jq '.status')"

# 4. Check metrics
echo "4. Check metrics..."
curl -s http://localhost:8080/actuator/metrics/analysis.job.count | jq '.measurements[0].value'

# 5. Poll for completion
echo "5. Polling for completion..."
for i in {1..30}; do
  STATUS=$(curl -s http://localhost:8080/logs/$LOG_ID/analysis | jq -r '.status')
  echo "  [$i/30] Status: $STATUS"
  if [ "$STATUS" = "COMPLETED" ] || [ "$STATUS" = "FAILED" ]; then
    break
  fi
  sleep 0.5
done

echo "=== Validation Complete ==="
```

---

This guide covers all Phase 3 API interactions and integration patterns!

