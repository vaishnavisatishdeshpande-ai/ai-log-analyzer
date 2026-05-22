# AI Log Analyzer

Async log analysis pipeline with AI-powered classification and deterministic fallback recovery.

When AI fails, the system doesn't.

---

## Architecture

```
POST /logs → Queue → Worker → AI Analysis → Result
                                ↓ (timeout)
                           Rule Engine → Same Result
```

Every request completes. The `source` field tells you which engine resolved it.

---

## The Problem

Production incidents generate thousands of log lines. Engineers manually search, guess at root causes, and spend hours on repeatable diagnostics.

Most AI log tools are synchronous, fragile, and fail completely when the AI layer goes down.

---

## The Design

**Async pipeline.** `POST /logs/{id}/analysis` returns `202 Accepted` immediately. Analysis happens in the background. Callers poll for results.

**AI + Fallback.** OpenAI classifies logs with root cause analysis, remediation steps, and confidence scoring. When AI times out or fails, a config-driven rule engine activates automatically. Same output format. Same SLA. Lower confidence, but the workflow never breaks.

**Resilience stack.** Retry with exponential backoff → Rate limiter (30 req/min) → Circuit breaker (opens at 50% failure rate, probes every 30s). Individual requests retry before the circuit breaker counts them as failures.

**Single entity lifecycle.** Controller creates a PENDING record. Worker finds and updates the same record through PROCESSING → COMPLETED. No duplicate rows. No orphaned state.

**Hybrid severity.** AI and rule engine results are weighted through a severity resolver. Rule engine can override AI when it detects CRITICAL patterns, preventing dangerous under-classification.

---

## What It Produces

```json
{
  "id": 101,
  "logId": 1,
  "analysis": "Database connection timeout detected. Application exceeded 30s threshold.",
  "possibleFix": "Check database server status. Verify network connectivity. Review connection pool sizing.",
  "severity": "HIGH",
  "confidence": 0.85,
  "source": "AI",
  "status": "COMPLETED",
  "createdAt": "2025-04-08T10:30:00",
  "completedAt": "2025-04-08T10:30:05"
}
```

When AI is unavailable:

```json
{
  "severity": "HIGH",
  "confidence": 0.85,
  "source": "RULE",
  "status": "COMPLETED"
}
```

Same structure. Same fields. Different source. Client doesn't care which engine ran.

---

## Run

```bash
# Local (H2)
./mvnw spring-boot:run

# Docker (PostgreSQL)
docker compose up --build
```

---

## API

```bash
# Submit a log
curl -X POST localhost:8080/logs \
  -H "Content-Type: application/json" \
  -d '{"serviceName":"PaymentService","level":"ERROR","message":"Connection timeout after 30000ms"}'

# Trigger analysis (returns 202)
curl -X POST localhost:8080/logs/1/analysis

# Poll for result
curl localhost:8080/logs/1/analysis

# Bulk upload production logs
curl -X POST localhost:8080/logs/upload \
  -F "file=@sample-logs/production-operational-dataset.log"
```

---

## Observability

```bash
# Health (includes circuit breaker state)
curl localhost:8080/actuator/health

# Metrics
curl localhost:8080/actuator/metrics/analysis.ai.count
curl localhost:8080/actuator/metrics/analysis.rule.count
```

Health response when AI is degraded:

```json
{
  "status": "DOWN",
  "components": {
    "systemHealth": {
      "details": {
        "circuitBreaker": "OPEN",
        "aiService": "unavailable",
        "fallback": "rule engine active"
      }
    }
  }
}
```

---

## Sample Logs

The repo includes production-style operational logs covering:

- PostgreSQL connection pool exhaustion
- Kubernetes CrashLoopBackOff (exit 137)
- OpenAI API timeout failures
- Kafka consumer lag with rebalance
- Nginx upstream failures (502 cascade)
- Raft quorum loss and leader election

```bash
curl -X POST localhost:8080/logs/upload \
  -F "file=@sample-logs/production-operational-dataset.log"
```

---

## Tech

Java 21 · Spring Boot · Spring AI · Resilience4j · Micrometer · H2 / PostgreSQL · Docker

---

## Key Decisions

| Decision | Why |
|---|---|
| Async over sync | AI latency (1-5s) blocks request threads. Async isolates it. |
| Fallback rule engine | AI fails ~15% under load. Fallback guarantees completion. |
| Single entity update | Worker updates the existing PENDING row. No duplicates. |
| Retry before circuit breaker | Transient failures resolve on retry. Only persistent failures open the breaker. |
| Config-driven rules | Severity patterns in YAML, not hardcoded. One source of truth. |
| Prompt sanitization | Log messages can't inject instructions into the AI prompt. |

---

## What I Learned

The hardest problems had nothing to do with AI.

- AI systems need deterministic recovery paths
- Async workflows require strict state lifecycle management
- Resilience matters more than model accuracy
- The fallback engine is 40x faster than the AI path
- Making the AI optional made the system better