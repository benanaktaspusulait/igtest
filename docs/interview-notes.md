# F2F Discussion Notes

Related docs:
- [Docs Index](README.md)
- [Architecture](architecture.md)
- [SLO / SLI Definition](slo-definition.md)

## 60-second architecture script

"I built a small Spring Boot service with clear separation between controller, service, and TfL client. The reliability core is in the client path: outbound timeouts, retry with exponential backoff+jitter only for transient errors, and a circuit breaker that opens after 5 sustained failures and probes recovery after 30 seconds. The service layer adds graceful degradation using stale-cache fallback, so we can continue serving data during upstream incidents. I added per-IP token bucket limiting to protect both our own service and TfL, plus metrics/tracing for SLI/SLO observability and burn-rate alerting."

## Failure scenario answers

## Q1: TfL down for 6 hours. What happens?

- Circuit opens after sustained failures, reducing upstream pressure.
- Requests are served from stale cache where available (`stale=true`), preserving partial functionality.
- For cache misses, service returns `503` with explicit upstream-unavailable error.
- On-call receives fast-burn alert quickly, then slow-burn ticketing for extended outage.
- Mitigation path: communicate degraded mode, monitor freshness SLI, tune retry/circuit via config.

## Q2: How would this handle 10k RPS?

- Current implementation is stateless enough to scale horizontally.
- Immediate production upgrade: move cache + rate limits to Redis for cross-instance consistency.
- Add gateway-level global rate limiting and autoscaling policies.
- Consider non-blocking upstream client and bulkhead isolation to avoid thread exhaustion.
- Add load tests (steady + burst + failure modes) to validate p95 and saturation behavior.

## Q3: What is missing for production readiness?

- Authn/Authz and mTLS for internal consumers.
- Distributed cache/rate-limit state.
- Better disruption classification and stricter contract tests.
- Progressive delivery (canary), config safety rails, runbooks, and dashboards.
- SLO-based release gating and incident automation playbooks.

## Q4: Why these SLO numbers?

- 99.9% availability gives strong reliability without pretending five-nines for an upstream-dependent service.
- p95 300ms/500ms aligns to user-perceived responsiveness and endpoint complexity.
- Freshness SLO explicitly limits stale-mode overuse.
- Burn-rate policy balances urgent paging for fast budget loss vs ticketing for slower drifts.

## Q5: If you had more time, what would you change?

1. Distributed resilience state and stronger chaos/failure testing.
2. Better upstream contract modeling and synthetic canary checks.
3. Dynamic config + feature flags for retry/circuit/rate tuning during incidents.
4. Deeper capacity model and adaptive load-shedding.
