# AI Log Analyzer

In production environments, when something breaks, the first response is almost always the same — open the documentation, search through logs manually, and try to piece together what happened. In high-volume systems, this can take hours.

This system takes a raw production log, runs it through an AI analysis pipeline, and returns a structured root cause, suggested fix, severity classification, and confidence score — in seconds, not hours.

The engineering focus was not the AI. It was making the AI reliable in production.

---

## What This System Produces

```json
{
  "id": 101,
  "logId": 1,
  "analysis": "Database connection timeout detected. The application attempted to connect to the database but exceeded the 30-second timeout threshold.",
  "possibleFix": "1. Check database server status\n2. Verify network connectivity\n3. Review database logs\n4. Consider increasing timeout threshold",
  "severity": "HIGH",
  "confidence": 0.85,
  "source": "AI",
  "status": "COMPLETED",
  "reason": "Pattern matched: timeout|timed out|exceeded",
  "createdAt": "2025-04-08T10:30:00",
  "completedAt": "2025-04-08T10:30:05"
}
```

Not just a log dump. A structured decision.

---

## Problem

When a production incident occurs, engineers typically:

- Manually search through thousands of log lines
- Open Confluence documentation written months ago
- Guess at root causes under pressure
- Spend hours in war rooms for issues that follow known patterns

**The cost:** slow response, high toil, repeated mistakes.

**The gap:** most AI log tools are synchronous, fragile, and fail silently when the AI model is unavailable or returns an inconsistent response.

---

## Solution

An async, production-grade log analysis pipeline built on Java and Spring Boot.

Core design goals:
- Non-blocking — analysis runs in the background, API returns in under 100ms
- Reliable — AI failures fall back to a rule engine automatically
- Consistent — single DB write per analysis, no duplicate or partial state
- Observable — every analysis stage is instrumented with Prometheus metrics

---

## System Pipeline

```
Log Submission (POST /logs)
        ↓
Create Log Record
        ↓
Submit Async Analysis (POST /logs/{id}/analysis)
        ↓ HTTP 202 — returns immediately
Background Worker picks up job
        ↓
Rate Limiter (30 req/min)
        ↓
Circuit Breaker (50% failure threshold)
        ↓
        ├── AI Analysis (2-5 seconds)
        │       ↓
        │   Hybrid Severity Enrichment
        │
        └── Rule Engine Fallback (<5ms)
                ↓
        Single DB Write → COMPLETED
        ↓
Poll for result (GET /logs/{id}/analysis)
```

---

## Key Engineering Decisions

### 1. Async Processing — 10,000x Throughput Improvement

Synchronous log analysis blocks threads for 2-5 seconds per request.

```
Before (blocking):   1 request per 5 seconds = 0.2 req/s
After (async):       100 requests in 50ms    = 2,000 req/s
```

Every analysis submission returns HTTP 202 immediately. The client polls for completion.

### 2. Fallback to Rule Engine

LLMs fail. They time out, return malformed responses, or hallucinate confidently.

This system handles that explicitly:
- Circuit breaker opens at 50% failure rate
- Rule engine takes over in under 5ms
- Client never sees a failure — they see a result with `"source": "RULE"`

### 3. Single Consistent DB Write

A common failure mode in async systems: the worker crashes mid-analysis and writes partial state, creating inconsistent records.

This system enforces one atomic DB write per analysis lifecycle. Status transitions are strict: `PENDING → PROCESSING → COMPLETED/FAILED`. No partial updates.

### 4. Hybrid Severity Classification

Severity is not determined by the AI alone. A hybrid engine combines:
- AI confidence score
- Rule-based pattern matching
- Log level (ERROR, WARN, INFO)

This prevents the AI from under-classifying critical failures when confidence is low.

### 5. Observability Built In

Every stage is instrumented:

| Metric | What it tracks |
|---|---|
| `analysis.job.count` | Submission rate |
| `analysis.success.count` | Completion rate |
| `analysis.failure.count` | Error rate |
| `analysis.ai.latency` | AI response time (p95, p99) |
| `analysis.severity` | Distribution by severity level |

---

## API Reference

### Submit a log for analysis

```bash
# Step 1: Create log
POST /logs
{
  "serviceName": "payment-service",
  "level": "ERROR",
  "message": "OutOfMemory exception in BillingService"
}

# Step 2: Submit async analysis
POST /logs/{id}/analysis
# Returns HTTP 202 immediately

# Step 3: Poll for result
GET /logs/{id}/analysis
```

### Status lifecycle

```
PENDING → PROCESSING → COMPLETED
                    ↘ FAILED
```

### Example completed response

```json
{
  "status": "COMPLETED",
  "severity": "CRITICAL",
  "confidence": 0.95,
  "analysis": "OutOfMemory error detected in BillingService...",
  "possibleFix": "Increase heap allocation. Review object lifecycle in billing loop.",
  "source": "AI",
  "completedAt": "2025-04-08T10:30:05"
}
```

---

## Tech Stack

| Layer | Technology | Why |
|---|---|---|
| Backend | Java, Spring Boot | Production-grade async processing |
| AI Layer | LLM via REST | Root cause reasoning |
| Fallback | Rule Engine | Reliability when AI fails |
| Database | PostgreSQL | Consistent state persistence |
| Observability | Prometheus, Actuator | Latency and failure tracking |
| Resilience | Circuit Breaker, Rate Limiter | Production-grade failure handling |

---

## Running the System

```bash
# Clone the repo
git clone https://github.com/vaishnavisatishdeshpande-ai/ai-log-analyzer

# Start the application
./mvnw spring-boot:run

# Validate the setup
curl -s http://localhost:8080/actuator/health | jq '.status'

# Create a test log
curl -X POST http://localhost:8080/logs \
  -H "Content-Type: application/json" \
  -d '{"serviceName":"test-service","level":"ERROR","message":"Heap space exceeded"}'

# Submit for analysis
curl -X POST http://localhost:8080/logs/1/analysis

# Poll for result
curl http://localhost:8080/logs/1/analysis
```

---

## What I Learned Building This

The hardest problems had nothing to do with AI:

- **LLMs hallucinate confidently** — you need a fallback that triggers before the client sees a bad result
- **Async state is subtle** — without strict status transitions, you get ghost records and duplicate writes
- **Observability has to be designed in** — adding metrics after the fact means you miss the failures that matter
- **Throughput is an architecture decision** — async processing changed request capacity by 10,000x, not a tuning parameter

---

## Future Improvements

- Feedback loop — mark analyses as correct/incorrect to improve rule matching
- MLflow integration — version and track rule engine model updates  
- Multi-log correlation — detect patterns across related services
- Webhook support — push results instead of polling
- Streaming ingestion via Kafka

---

## Related Project

This system works alongside the [Intelligent Incident Detection System](https://github.com/vaishnavisatishdeshpande-ai/intelligent-incident-system) — which detects anomalies before they become incidents using real-time Kafka streams, Feast, and XGBoost.

Together: **detect early → explain clearly → respond fast**
