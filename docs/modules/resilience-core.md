# resilience-core

Framework-agnostic resilience library for dependency calls.

Related docs:
- [Docs Index](../README.md)
- [Architecture](../architecture.md)
- [resilience-adapter-spring](resilience-adapter-spring.md)

## Scope

`resilience-core` provides reusable resilience mechanisms without any Spring dependency:

- timeout
- retry (exponential backoff and jitter)
- circuit breaker
- token bucket rate limiting
- telemetry hook interface

## Public API

- `com.ig.sre.resilience.core.executor.ResilientExecutor`
  - `execute(Supplier<T> action, RequestContext context)`
- `com.ig.sre.resilience.core.context.RequestContext`
  - `dependencyKey`, `operationName`, `clientKey`
- `com.ig.sre.resilience.core.policy.ResiliencePolicy`
  - timeout / retry / circuit-breaker / rate-limit policies
- `com.ig.sre.resilience.core.policy.PolicyProvider`
  - resolves policy by `dependencyKey`

## Execution Flow

Inside `ResilientExecutor`:

1. Rate limit check (per `dependencyKey|clientKey`)
2. Circuit breaker gate
3. Timeout execution
4. Retry for transient failures

Retry is applied only for transient categories:

- `SERVER_ERROR`
- `TIMEOUT`
- `NETWORK`

## Error Model

`ErrorClassifier` maps exceptions to:

- `CLIENT_ERROR`
- `SERVER_ERROR`
- `TIMEOUT`
- `NETWORK`
- `CIRCUIT_OPEN`
- `RATE_LIMITED`
- `UNKNOWN`

## Telemetry Contract

Implement `com.ig.sre.resilience.core.telemetry.Telemetry` in adapters (Spring, Quarkus, etc.).

Core expects hooks for:

- request outcome
- latency
- retries
- circuit state
- rate-limited events

Standard `request outcome` values emitted by `ResilientExecutor`:

- `success`
- `upstream_error`
- `client_error`
- `rate_limited`
- `unknown_error`

## Package Layout

- `context`: request context
- `policy`: policy model and provider
- `executor`: resilient execution orchestration
- `circuit`: circuit state and exception
- `ratelimit`: rate limiter interfaces and in-memory token bucket
- `error`: error classification and upstream exception model
- `clock`: time abstraction for testability
- `telemetry`: telemetry abstraction and no-op implementation

## Build and Test

From repository root:

```bash
mvn -pl resilience-core -am test
```

Static checks:

```bash
mvn -Pquality clean verify
```

## Notes

- `InMemoryTokenBucketRateLimiter` is suitable for single-instance usage.
- For distributed deployments, use a shared rate-limit backend via another `RateLimiter` implementation.
