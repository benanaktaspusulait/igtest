# TfL Tube Status Service (SRE Exercise)

This repository now uses a **framework-agnostic resilience core** plus a thin Spring Boot app adapter.

Related docs:
- [Docs Index](README.md)
- [SLO / SLI Definition](slo-definition.md)
- [F2F Discussion Notes](interview-notes.md)
- [Resilience Core Module](modules/resilience-core.md)
- [Resilience Spring Adapter Module](modules/resilience-adapter-spring.md)
- [Architecture Diagram (.drawio)](diagrams/tube-status-architecture.drawio)

## Modules

- `resilience-core`: plain Java library (no Spring dependency)
  - timeout
  - retry (exponential backoff + jitter)
  - circuit breaker
  - rate limiter
  - telemetry hook interface (metrics/tracing)
- `resilience-adapter-spring`: Spring-specific adapter
  - `@ConfigurationProperties` policy binding (`resilience.policies.*`)
  - Spring bean wiring for `ResilientExecutor`
  - Micrometer + tracing implementation of core `Telemetry`
- `tube-status-app`: Spring Boot 4 application
  - TfL DTO mapping
  - API endpoints
  - cache fallback
  - domain behavior and fallback strategy

## Stack

- Java 21
- Spring Boot 4 (app module)
- Micrometer + Prometheus
- Grafana (provisioned dashboards)
- Micrometer Tracing (OTel bridge)
- Caffeine cache

## Core Resilience API

`resilience-core` public API:

- `ResilientExecutor.execute(Supplier<T> action, RequestContext ctx)`
- `RequestContext(dependencyKey, operationName, clientKey)`
- `PolicyProvider` selects policy by `dependencyKey` (for example `tfl`)
- `Telemetry` interface exposes counters/latency/circuit/retry/rate-limit hooks

Execution order inside the core:

1. rate limit
2. circuit breaker gate
3. timeout
4. retry (only transient categories: 5xx/timeout/network)

## Application Behavior

Endpoints:

- `GET /api/v1/tube/{lineId}/status`
- `GET /api/v1/tube/{lineId}/status?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`
- `GET /api/v1/tube/disruptions/unplanned`
- `GET /v3/api-docs`
- `GET /swagger-ui/index.html`

Versioning:

- Spring MVC API versioning (`API-Version: 1.0`)

Fallback strategy:

- On transient upstream failure / open circuit: return stale cache if present (`stale=true`, `X-Data-Source: STALE_CACHE`)
- If no cache exists: return `503`

Rate limit:

- Per client key (IP), inside `resilience-core`
- On limit breach: `429` + `Retry-After`
- `X-Forwarded-For` / `Forwarded` headers are trusted only for configured proxy IPs.

Bulkhead:

- Outbound TfL calls are protected by an in-flight semaphore (`tfl.max-in-flight`).
- On saturation, service returns stale cache when available, otherwise `503`.

## Resilience Configuration (Environment Driven)

Configured by dependency key under `resilience.policies` in `tube-status-app/src/main/resources/application.yml`.

Example (`tfl`):

- `timeout-ms`
- `retry.max-retries`, `retry.initial-backoff-ms`, `retry.max-backoff-ms`, `retry.jitter-factor`
- `circuit-breaker.failure-threshold`, `circuit-breaker.half-open-after-seconds`, `circuit-breaker.half-open-max-calls`
- `rate-limit.permits-per-minute`

Environment override examples:

- `RESILIENCE_TIMEOUT_MS_TFL=2000`
- `RESILIENCE_RETRY_MAX=3`
- `RESILIENCE_CB_FAIL=5`
- `RESILIENCE_CB_HALFOPEN_SEC=30`
- `RATE_LIMIT_PER_MINUTE=100`
- `TFL_MAX_IN_FLIGHT=200`
- `TRUST_FORWARD_HEADERS=false`
- `TRUSTED_PROXY_IPS=10.0.0.5,10.0.0.6`

## Metrics and Tracing

Core telemetry emits these standard metric names:

- `requests_total{dependency,operation,outcome}`
- `latency_ms{dependency,operation}`
- `retries_total{dependency,operation}`
- `circuit_state{dependency,state}`
- `rate_limited_total{dependency,operation,clientType}`

`requests_total` outcome values are separated to avoid SLO noise:

- `success`
- `upstream_error` (5xx, timeout, network, circuit-open)
- `client_error` (upstream 4xx)
- `rate_limited`
- `unknown_error`

Span naming:

- `tfl.request` (for dependency `tfl`)

Common span attributes:

- `dependency`, `operation`, `http.status_code`, `retry.count`, `cb.state`

## Build and Run

Prerequisites:

- Java 21
- Maven 3.9+

Run all tests:

```bash
mvn test
```

Run standard Maven verification lifecycle:

```bash
mvn verify
```

Install modules:

```bash
mvn -DskipTests install
```

Run the app:

```bash
mvn -pl tube-status-app -am spring-boot:run
```

Run with Docker Compose:

```bash
docker compose up --build
```

Run in detached mode:

```bash
docker compose up -d --build
```

Synthetic traffic:

- Compose starts a `synthetic-traffic` service that periodically calls API endpoints
  so Grafana panels stay populated in local/dev runs.
- Configure interval with `SYNTHETIC_TRAFFIC_INTERVAL_SECONDS` (default `1`).
- Configure request burst per interval with `SYNTHETIC_TRAFFIC_BURST_PER_INTERVAL` (default `3`).
- Local compose lowers rate-limit threshold with `RATE_LIMIT_PER_MINUTE=20`
  so `rate_limited_total` is observable on Grafana.
- Compose also enables synthetic transient fault injection by default to generate
  retry and rate-limit telemetry in local dashboards:
  - `TFL_SYNTHETIC_FAULT_ENABLED=true`
  - `TFL_SYNTHETIC_FAULT_SERVER_ERROR_RATE=0.02`
  - `TFL_SYNTHETIC_FAULT_TIMEOUT_RATE=0.02`
- Disable with `TFL_SYNTHETIC_FAULT_ENABLED=false` for pure upstream behavior.

Grafana access:

- URL: `http://localhost:3000`
- Username: `admin` (default)
- Password: `admin` (default)
- override via env: `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`

Stop:

```bash
docker compose down
```

Health and metrics:

```bash
curl "http://localhost:8080/actuator/health"
curl "http://localhost:8080/actuator/prometheus"
curl "http://localhost:9090/-/healthy"
curl "http://localhost:3000/api/health"
```

Provisioned dashboards:

- `Tube Status Overview`
- `Tube Status SLO & Burn Rate`

## Quality Checks

Standard checks:

- `mvn test` runs unit/integration tests.
- `mvn verify` runs default lifecycle plus JaCoCo coverage checks.
- `mvn -Pquality clean verify` runs Checkstyle, PMD, and SpotBugs in addition to default gates.

Prometheus alert sample file:

- `monitoring/prometheus/alerts/prometheus-alerts.yml`

## Why This Split (App-level + Platform-ready)

Resilience decisions still need **dependency-aware behavior** in the app layer (4xx vs 5xx, fallback policy, domain-level trade-offs).

By extracting mechanism to `resilience-core` and keeping app-specific policy/mapping in `tube-status-app`:

- app teams avoid copy-paste resilience code
- policies stay dependency-specific
- adapters stay thin and framework-specific
- migration to another runtime (for example Quarkus) is mostly wiring

## Project Structure

```text
resilience-core/
  src/main/java/com/ig/sre/resilience/core/
    ResilientExecutor.java
    ResiliencePolicy.java
    RequestContext.java
    ...

resilience-adapter-spring/
  src/main/java/com/ig/sre/resilience/spring/
    config/SpringResilienceAutoConfiguration.java
    properties/SpringResilienceProperties.java
    telemetry/MicrometerTelemetry.java
  src/main/resources/META-INF/spring/
    org.springframework.boot.autoconfigure.AutoConfiguration.imports

tube-status-app/
  src/main/java/com/ig/sre/tubestatus/
    api/model/
    client/tfl/
    config/
    service/
    web/
  src/main/resources/application.yml
  src/test/java/...

docker-compose.yml
monitoring/prometheus/prometheus.yml
monitoring/prometheus/alerts/prometheus-alerts.yml
monitoring/grafana/provisioning/datasources/prometheus.yml
monitoring/grafana/provisioning/dashboards/dashboards.yml
monitoring/grafana/dashboards/tube-status-overview.json
monitoring/grafana/dashboards/tube-status-slo.json
docs/README.md
docs/architecture.md
docs/slo-definition.md
docs/interview-notes.md
docs/modules/resilience-core.md
docs/modules/resilience-adapter-spring.md
```
