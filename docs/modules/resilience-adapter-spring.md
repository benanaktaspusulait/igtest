# resilience-adapter-spring

Spring Boot adapter for `resilience-core`.

Related docs:
- [Docs Index](../README.md)
- [Architecture](../architecture.md)
- [resilience-core](resilience-core.md)

## What This Module Provides

- Spring Boot auto-configuration for resilience beans
- immutable, record-based `@ConfigurationProperties` model for `resilience.policies.*`
- Micrometer + tracing backed `Telemetry` implementation
- Fallback to `NoOpTelemetry` when `MeterRegistry` is not available

## Auto-Configuration

Auto-config class:

- `com.ig.sre.resilience.spring.config.SpringResilienceAutoConfiguration`

Registration:

- `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

No manual `@Import` is required in consuming Spring Boot apps.

## Provided Beans

Beans are created with `@ConditionalOnMissingBean`:

- `PolicyProvider`
- `RateLimiter`
- `ResilienceClock`
- `Telemetry`
- `ResilientExecutor`

This allows app-level overrides when needed.

## Configuration

Prefix:

- `resilience`

Expected map:

- `resilience.policies.<dependencyKey>.*`

Example:

```yaml
resilience:
  policies:
    tfl:
      timeout-ms: 2000
      retry:
        max-retries: 3
        initial-backoff-ms: 1000
        max-backoff-ms: 4000
        jitter-factor: 0.25
      circuit-breaker:
        failure-threshold: 5
        half-open-after-seconds: 30
        half-open-max-calls: 2
      rate-limit:
        permits-per-minute: 100
```

Notes:

- Adapter does not assume a domain-specific dependency key.
- Missing dependency policy lookup fails fast in `PolicyProvider`.

## Development Commands

From repository root:

```bash
mvn -pl resilience-adapter-spring -am test
mvn -Pquality clean verify
```
