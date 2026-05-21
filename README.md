# AI Log Analyzer

In production environments, when something breaks, the first response is almost always the same — open documentation, search through logs manually, and try to piece together what happened.

In high-volume systems, this process can take hours.

AI Log Analyzer ingests raw operational logs, runs them through an asynchronous AI analysis pipeline, and returns:

- Root cause analysis
- Suggested remediation
- Severity classification
- Confidence scoring
- Workflow status tracking

The engineering focus was not just the AI.

The focus was making the AI reliable in production.

---

# Features

- Async non-blocking log analysis
- AI-powered incident analysis
- Deterministic fallback rule engine
- Severity classification
- Bulk operational log ingestion
- Dockerized deployment
- PostgreSQL persistence
- REST APIs for polling + retrieval
- Production-style operational dataset support
- Graceful degradation during AI failures

---

# Architecture

```text
Operational Logs
        ↓
Bulk Upload API
        ↓
Async Analysis Pipeline
        ↓
AI Log Analysis Service
        ↓
Fallback Rule Engine
        ↓
Severity Classification
        ↓
PostgreSQL Persistence
        ↓
REST Retrieval APIs
```

---

# What This System Produces

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
  "createdAt": "2025-04-08T10:30:00",
  "completedAt": "2025-04-08T10:30:05"
}
```

Not just a log dump. A structured operational decision.

---

# Problem

When a production incident occurs, engineers typically:

- Manually search through thousands of log lines
- Open outdated documentation
- Guess at root causes under pressure
- Spend hours in incident war rooms for repeatable issues

The cost:
- slow response
- operational toil
- repeated mistakes
- delayed recovery

Most AI log tools are:
- synchronous
- fragile
- unable to recover when the AI layer fails

---

# Solution

A resilient async log analysis pipeline built with Java + Spring Boot.

Core design goals:

- Non-blocking
- Reliable
- Observable
- Recoverable
- Production-oriented

---

# System Pipeline

```text
Log Submission (POST /logs)
        ↓
Create Log Record
        ↓
Submit Async Analysis (POST /logs/{id}/analysis)
        ↓ HTTP 202 — returns immediately
Background Worker picks up job
        ↓
AI Analysis
        ↓
        ├── AI Success
        │       ↓
        │   Structured Incident Analysis
        │
        └── AI Failure
                ↓
        Rule Engine Fallback
                ↓
        Severity Classification
                ↓
        Single DB Write → COMPLETED
        ↓
Poll for result (GET /logs/{id}/analysis)
```

---

# Key Engineering Decisions

## 1. Async Processing

Synchronous AI analysis blocks request threads for multiple seconds.

This platform processes analysis asynchronously.

Benefits:
- higher throughput
- lower request latency
- reduced timeout risk
- better concurrency handling
- isolation of AI latency

The API immediately returns `PENDING` while analysis continues in the background.

---

## 2. Fallback Rule Engine

LLMs fail:
- timeouts
- malformed responses
- API outages
- inconsistent outputs

This system explicitly handles AI failure.

When AI becomes unavailable:
- fallback classification activates automatically
- deterministic severity rules execute
- workflow still completes successfully
- client still receives a usable result

Example fallback response:

```json
{
  "source": "RULE",
  "status": "COMPLETED",
  "severity": "HIGH",
  "analysis": "AI unavailable. Fallback rule engine classified this log deterministically."
}
```

---

## 3. Single Consistent DB Write

Async systems commonly produce:
- duplicate records
- partial state
- inconsistent workflows

This system enforces:
- strict status transitions
- atomic analysis updates
- one analysis lifecycle per log

Status lifecycle:

```text
PENDING → COMPLETED
```

---

## 4. Hybrid Severity Classification

Severity is not determined solely by the AI.

Classification combines:
- AI output
- deterministic rule matching
- operational keyword detection
- log severity context

This prevents critical failures from being under-classified.

---

# Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot |
| AI Layer | Spring AI |
| Database | PostgreSQL |
| Local Dev DB | H2 |
| Build Tool | Maven |
| Containerization | Docker |
| Observability | Spring Actuator |

---

# Quick Start

## Run With Docker

```bash
docker compose up --build
```

Application:
```text
http://localhost:8080
```

---

# Upload Operational Dataset

```bash
curl -X POST http://localhost:8080/logs/upload \
-F "file=@sample-logs/production-operational-dataset.log"
```

---

# API Reference

## Create Log

```http
POST /logs
```

## Bulk Upload Logs

```http
POST /logs/upload
```

## Trigger Async Analysis

```http
POST /logs/{id}/analysis
```

## Poll Analysis Status

```http
GET /logs/{id}/analysis
```

## Retrieve All Logs

```http
GET /logs
```

---

# Sample Operational Logs

This repository includes curated production-style operational logs simulating:

- PostgreSQL connection pool exhaustion
- Kubernetes CrashLoopBackOff events
- OpenAI timeout failures
- distributed quorum instability
- Kafka consumer lag
- Nginx upstream failures
- malformed payload handling
- rule-engine fallback activation

Dataset location:

```text
sample-logs/production-operational-dataset.log
```

---

# Example Workflow

1. Upload operational logs
2. API returns immediately
3. Analysis status becomes `PENDING`
4. Background worker processes logs
5. AI analysis completes
6. If AI fails, fallback engine activates
7. Final result persisted in PostgreSQL

---

# Running Locally

```bash
./mvnw spring-boot:run
```

---

# Future Improvements

- Kafka ingestion
- OpenTelemetry tracing
- Prometheus metrics
- Grafana dashboards
- Kubernetes deployment
- distributed worker queues
- webhook callbacks instead of polling

---

# Demo

Demo walkthrough available in:

```text
demo/
```

---

# What I Learned Building This

The hardest problems had nothing to do with AI.

Key lessons:
- AI systems need deterministic recovery paths
- async workflows require strict state handling
- observability must be designed in early
- resilience matters more than model quality
- throughput is an architecture decision
