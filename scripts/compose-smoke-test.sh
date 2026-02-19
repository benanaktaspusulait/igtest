#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

SMOKE_TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-240}"
POLL_INTERVAL_SECONDS=3
GRAFANA_USER="${GRAFANA_ADMIN_USER:-admin}"
GRAFANA_PASS="${GRAFANA_ADMIN_PASSWORD:-admin}"

log() {
  printf '[smoke] %s\n' "$1"
}

wait_until() {
  local description="$1"
  shift
  local deadline=$((SECONDS + SMOKE_TIMEOUT_SECONDS))

  while (( SECONDS < deadline )); do
    if "$@"; then
      log "${description}: OK"
      return 0
    fi
    sleep "${POLL_INTERVAL_SECONDS}"
  done

  log "${description}: TIMEOUT (${SMOKE_TIMEOUT_SECONDS}s)"
  return 1
}

check_tube_status_health() {
  curl --fail --silent http://localhost:8080/actuator/health/readiness > /dev/null \
    || curl --fail --silent http://localhost:8080/actuator/health > /dev/null
}

check_prometheus_health() {
  curl --fail --silent http://localhost:9090/-/healthy > /dev/null
}

check_grafana_health() {
  curl --fail --silent --user "${GRAFANA_USER}:${GRAFANA_PASS}" http://localhost:3000/api/health \
    | grep -Eq '"database"[[:space:]]*:[[:space:]]*"ok"'
}

check_grafana_dashboards() {
  local payload
  payload="$(curl --fail --silent --user "${GRAFANA_USER}:${GRAFANA_PASS}" "http://localhost:3000/api/search?type=dash-db")"

  echo "${payload}" | grep -q 'Tube Status Overview' \
    && echo "${payload}" | grep -Eq 'Tube Status SLO (\\u0026|&) Burn Rate'
}

prom_query_scalar() {
  local query="$1"
  local response
  response="$(curl --fail --silent --get --data-urlencode "query=${query}" "http://localhost:9090/api/v1/query")"

  python3 - <<'PY' "${response}"
import json
import sys

try:
    payload = json.loads(sys.argv[1])
except json.JSONDecodeError:
    print("")
    raise SystemExit(0)

if payload.get("status") != "success":
    print("")
    raise SystemExit(0)

results = payload.get("data", {}).get("result", [])
if not results:
    print("")
    raise SystemExit(0)

value = results[0].get("value", [])
if len(value) < 2:
    print("")
    raise SystemExit(0)

print(value[1])
PY
}

check_metric_activity() {
  local value
  value="$(prom_query_scalar 'sum(rate(requests_total{dependency="tfl"}[5m]))')"
  if [[ -z "${value}" ]]; then
    return 1
  fi

  python3 - <<'PY' "${value}"
import sys

try:
    val = float(sys.argv[1])
except ValueError:
    raise SystemExit(1)

raise SystemExit(0 if val > 0.0 else 1)
PY
}

cleanup() {
  local exit_code=$?

  if (( exit_code != 0 )); then
    log "Failure detected. Last container status and logs:"
    docker compose ps || true
    docker compose logs --tail=200 tube-status-app prometheus grafana || true
  fi

  docker compose down -v --remove-orphans || true
  exit "${exit_code}"
}

trap cleanup EXIT

log "Resetting stack"
docker compose down -v --remove-orphans || true

log "Starting stack (build included)"
docker compose up -d --build

wait_until "tube-status-app health" check_tube_status_health
wait_until "prometheus health" check_prometheus_health
wait_until "grafana health" check_grafana_health
wait_until "grafana dashboard provisioning" check_grafana_dashboards
wait_until "prometheus metric activity" check_metric_activity

log "Smoke test completed successfully"
