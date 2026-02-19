# SLO / SLI Definition - Tube Status Service

Related docs:
- [Docs Index](README.md)
- [Architecture](architecture.md)
- [F2F Discussion Notes](interview-notes.md)

## Service scope

This service provides tube status data to latency-sensitive users and depends on an external upstream (TfL API). SLOs focus on user experience while acknowledging upstream dependency risk.

## SLIs

## 1) Availability SLI

- **Definition:** fraction of valid API requests served with non-5xx responses.
- **Formula:** `1 - (5xx responses / total valid requests)`
- **Dependency metric mapping:** burn-rate alerting uses `requests_total{outcome="upstream_error"}` over `requests_total{outcome=~"success|upstream_error"}` so `rate_limited` events do not consume availability error budget.
- **Why it matters:** users primarily need predictable access; stale fallback still counts as successful if contract is met.

## 2) Latency SLI (p95)

- **Definition:** p95 end-to-end request latency per endpoint.
- **Why it matters:** delays reduce usefulness of commute decisions and operational confidence.

## 3) Correctness SLI (error-free responses)

- **Definition:** percentage of responses that pass schema + required field checks (`lineId`, `lineName`, at least one status entry for line-status endpoint).
- **Why it matters:** availability without valid payloads is not useful.

## 4) Freshness SLI

- **Definition:** percentage of successful responses where data age (`now - fetchedAt`) is below freshness threshold.
- **Threshold:** 5 minutes for current status endpoints.
- **Why it matters:** stale fallback is acceptable during incidents but should remain bounded.

## SLO Targets (30-day rolling window)

## 1) Availability SLO

- **Target:** `>= 99.9%` successful requests.
- **Reasoning:** business-critical internal dependency but still external-upstream constrained.

## 2) Latency SLO

- **Target:** `p95 < 300ms` for `/api/v1/tube/{lineId}/status`
- **Target:** `p95 < 500ms` for `/api/v1/tube/disruptions/unplanned`
- **Reasoning:** second endpoint aggregates all lines; slightly higher budget is acceptable.

## 3) Correctness SLO

- **Target:** `>= 99.95%` responses are schema-valid and non-empty per endpoint contract.
- **Reasoning:** avoids silent reliability failures where requests "succeed" but payloads are unusable.

## 4) Freshness SLO

- **Target:** `>= 99.0%` successful responses have data age `< 5 minutes`.
- **Reasoning:** permits short degraded windows while keeping stale mode exceptional.

## Error Budget (30 days)

- Availability 99.9% => error budget 0.1%.
- Monthly request volume example: 10M requests => 10,000 request failures budget.

## Alerting Strategy (Burn-rate based)

## Page (fast burn)

- Trigger if **burn rate >= 14x** for 5 minutes on availability SLO.
- Trigger if p95 latency breaches 2x target for 10 minutes.
- Trigger if stale responses exceed 20% for 10 minutes.

## Ticket (slow burn)

- Trigger if **burn rate >= 2x** for 1 hour.
- Trigger if freshness SLI < 99.0% over 1 hour.
- Trigger if correctness SLI < 99.95% over 1 hour.

## Alert-to-action mapping

- Fast burn page: protect error budget immediately (rollback, disable retries via config/flag, reduce traffic, investigate TfL outage).
- Slow burn ticket: plan mitigation and follow-up changes without waking on-call unnecessarily.

## Notes

- During confirmed external TfL outage, service may intentionally degrade to stale-cache mode to preserve availability.
- Freshness SLO ensures stale mode does not become silent steady-state behavior.
