# Resilient TfL Tube Status Service

This project implements a production-minded Java service for London Underground status with:

- current line status lookup
- date-range status lookup
- all current unplanned disruptions
- resilience patterns: circuit breaker, retry with jitter, per-client rate limit
- SRE observability: Prometheus metrics, Grafana dashboards, SLO/SLI definitions

## Setup

Prerequisites:

- Java 21+
- Maven 3.9+
- Docker + Docker Compose (for full stack run)

Optional environment variables:

- `TFL_APP_ID`
- `TFL_APP_KEY`
- `TFL_MAX_IN_FLIGHT` (default `200`, bulkhead limit for concurrent outbound TfL calls)
- `TRUST_FORWARD_HEADERS` (default `false`)
- `TRUSTED_PROXY_IPS` (comma-separated allow-list, used only when `TRUST_FORWARD_HEADERS=true`)

## Run

### App only

```bash
mvn -pl tube-status-app -am spring-boot:run
```

### Full stack (app + Prometheus + Grafana + synthetic traffic)

```bash
docker compose up -d --build
```

Smoke test (compose up + health + dashboard + metrics activity assert):

```bash
./scripts/compose-smoke-test.sh
```

Stop stack:

```bash
docker compose down -v --remove-orphans
```

## Sample API Requests

Current status:

```bash
curl -H "API-Version: 1.0" "http://localhost:8080/api/v1/tube/central/status"
```

Date-range status:

```bash
curl -H "API-Version: 1.0" "http://localhost:8080/api/v1/tube/northern/status?startDate=2025-11-20&endDate=2025-11-22"
```

All unplanned disruptions:

```bash
curl -H "API-Version: 1.0" "http://localhost:8080/api/v1/tube/disruptions/unplanned"
```

Health and metrics:

```bash
curl "http://localhost:8080/actuator/health"
curl "http://localhost:8080/actuator/prometheus"
curl "http://localhost:9090/-/healthy"
curl -u admin:admin "http://localhost:3000/api/health"
```

## Architecture Decisions and Trade-offs

### 1) Circuit breaker thresholds

- threshold `5` consecutive failures balances noisy transient errors vs real incidents
- half-open after `30s` gives upstream recovery window without long blind downtime
- open-state requests fail fast and rely on cache fallback where possible

### 2) Retry policy

- exponential backoff with jitter avoids retry storms under shared failure
- max `3` retries limits tail-latency amplification
- retries are only for timeout/network/5xx; 4xx are non-retryable by design

### 3) Reliability trade-offs

- stale-cache fallback improves availability but can degrade freshness
- local compose intentionally injects synthetic faults and lower rate-limit (`20/min`) to make resilience telemetry visible
- production defaults remain conservative (`100/min` rate-limit in app config)

### 4) SLO target rationale

- availability target `99.9%` reflects business-critical use with external dependency constraints
- latency/correctness/freshness SLOs are tied to user impact and enforce bounded degraded mode
- full rationale and formulas are documented in `docs/slo-definition.md`

## SLO / SLI Document

- Submission and reference document: [`docs/slo-definition.md`](docs/slo-definition.md)

## Quality Gates

Run full quality gates:

```bash
mvn -Pquality clean verify
```

Includes:

- Java/Maven baseline enforcement (`maven-enforcer-plugin`)
- unit + integration tests
- coverage gate (`jacoco:check`, min 70% instruction coverage/module)
- Checkstyle
- PMD
- SpotBugs

## Proxy and Client Identity

- Client rate-limit identity is always `remoteAddr` by default.
- `X-Forwarded-For` and `Forwarded` headers are used only when:
  - `TRUST_FORWARD_HEADERS=true`
  - request `remoteAddr` is in `TRUSTED_PROXY_IPS`.
- This prevents header spoofing when the service is directly reachable.

## Confidentiality

If this repository is used for a private interview task, keep it **private** and avoid sharing task text publicly.

## API Docs

Swagger/OpenAPI endpoints:

- `http://localhost:8080/v3/api-docs`
- `http://localhost:8080/swagger-ui/index.html`

## Additional Docs

- Index: [`docs/README.md`](docs/README.md)
- Architecture guide: [`docs/architecture.md`](docs/architecture.md)
- Interview notes: [`docs/interview-notes.md`](docs/interview-notes.md)
