#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CLUSTER_NAME="idp-local"
NAMESPACE="idp"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose-kind.yaml"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
DIM='\033[2m'
NC='\033[0m'

ok()   { echo -e "  ${GREEN}✔${NC} $*"; }
fail() { echo -e "  ${RED}✘${NC} $*"; }
warn() { echo -e "  ${YELLOW}!${NC} $*"; }
info() { echo -e "  ${DIM}$*${NC}"; }

section() {
  echo ""
  echo -e "${CYAN}${BOLD}── $* ──${NC}"
}

# Track overall status
ERRORS=0

# =========================================================
# 1. kind cluster
# =========================================================
section "kind cluster"

if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
  ok "Cluster '${CLUSTER_NAME}' exists"
  kubectl cluster-info --context "kind-${CLUSTER_NAME}" &>/dev/null && \
    ok "kubectl context is reachable" || { fail "kubectl cannot reach cluster"; ERRORS=$((ERRORS+1)); }
else
  fail "Cluster '${CLUSTER_NAME}' not found"
  ERRORS=$((ERRORS+1))
  echo ""
  echo -e "${RED}kind cluster is not running. Start with:${NC}"
  echo "  bash k8s/local/up-app-only.sh"
  exit 1
fi

# =========================================================
# 2. Docker Compose services
# =========================================================
section "Docker Compose (docker-compose-kind.yaml)"

if ! docker compose -f "$COMPOSE_FILE" ps &>/dev/null 2>&1; then
  fail "Docker Compose is not running"
  ERRORS=$((ERRORS+1))
else
  # Get all containers (including exited) as JSON
  ALL_COMPOSE_JSON=$(docker compose -f "$COMPOSE_FILE" ps -a --format json 2>/dev/null || echo "[]")

  COMPOSE_ERRORS=$(echo "$ALL_COMPOSE_JSON" | python3 -c "
import sys, json

data = json.load(sys.stdin)
if not isinstance(data, list):
    data = [data]

# Build lookup by Service name
by_service = {}
for c in data:
    by_service[c['Service']] = c

errors = 0

# Long-running services
for svc in ['postgres-primary','postgres-replica','redis','nginx','app-view','app-view-crosssite','app-view-context-path','sample-web','mockoon']:
    c = by_service.get(svc)
    if not c:
        print(f'  \033[0;31m✘\033[0m {svc}  not found')
        errors += 1
    elif c['State'] == 'running':
        health = c.get('Health','')
        extra = f'  \033[2m({health})\033[0m' if health else ''
        print(f'  \033[0;32m✔\033[0m {svc}{extra}')
    else:
        print(f'  \033[0;31m✘\033[0m {svc}  ({c[\"State\"]})')
        errors += 1

# One-shot jobs
for svc in ['postgres-user-init','flyway-migrator','pg-cron-setup']:
    c = by_service.get(svc)
    if not c:
        print(f'  \033[1;33m!\033[0m {svc}  not found (not yet run?)')
    elif c['State'] == 'exited' and c.get('ExitCode', 1) == 0:
        print(f'  \033[0;32m✔\033[0m {svc}  \033[2m(completed)\033[0m')
    elif c['State'] == 'exited':
        print(f'  \033[0;31m✘\033[0m {svc}  (exit code: {c.get(\"ExitCode\",\"?\")})')
        errors += 1
    else:
        print(f'  \033[1;33m!\033[0m {svc}  ({c[\"State\"]})')

print(errors)
" 2>/dev/null)

  # Last line is the error count, rest is display output
  COMPOSE_ERROR_COUNT=$(echo "$COMPOSE_ERRORS" | tail -1)
  echo "$COMPOSE_ERRORS" | sed '$d'
  ERRORS=$((ERRORS + COMPOSE_ERROR_COUNT))
fi

# =========================================================
# 3. Kubernetes resources
# =========================================================
section "Kubernetes (namespace: $NAMESPACE)"

# Pods
PODS_JSON=$(kubectl get pods -n "$NAMESPACE" -o json 2>/dev/null || echo '{"items":[]}')
POD_COUNT=$(echo "$PODS_JSON" | python3 -c "import sys,json; print(len(json.load(sys.stdin)['items']))")

if [ "$POD_COUNT" = "0" ]; then
  fail "No pods found"
  ERRORS=$((ERRORS+1))
else
  echo "$PODS_JSON" | python3 -c "
import sys, json
pods = json.load(sys.stdin)['items']
for p in pods:
    name = p['metadata']['name']
    phase = p['status']['phase']
    ready = all(c.get('ready', False) for c in p['status'].get('containerStatuses', []))
    restarts = sum(c.get('restartCount', 0) for c in p['status'].get('containerStatuses', []))
    if phase == 'Running' and ready:
        status = 'Ready'
        mark = '\033[0;32m✔\033[0m'
    else:
        status = phase
        mark = '\033[0;31m✘\033[0m'
    restart_info = f'  \033[1;33mrestarts: {restarts}\033[0m' if restarts > 0 else ''
    print(f'  {mark} {name}  \033[2m({status}){restart_info}\033[0m')
"
fi

# HPA
echo ""
HPA_JSON=$(kubectl get hpa -n "$NAMESPACE" -o json 2>/dev/null || echo '{"items":[]}')
HPA_COUNT=$(echo "$HPA_JSON" | python3 -c "import sys,json; print(len(json.load(sys.stdin)['items']))")

if [ "$HPA_COUNT" = "0" ]; then
  warn "No HPA found"
else
  echo "$HPA_JSON" | python3 -c "
import sys, json
items = json.load(sys.stdin)['items']
for h in items:
    name = h['metadata']['name']
    replicas = h['status'].get('currentReplicas', '?')
    desired = h['status'].get('desiredReplicas', '?')
    min_r = h['spec']['minReplicas']
    max_r = h['spec']['maxReplicas']
    metrics = h['status'].get('currentMetrics', [])
    cpu = '?'
    for m in metrics:
        if m.get('type') == 'Resource' and m['resource']['name'] == 'cpu':
            cpu = m['resource']['current'].get('averageUtilization', '?')
    print(f'  \033[0;32m✔\033[0m HPA: {name}  \033[2mreplicas={replicas}/{desired} (min={min_r}, max={max_r})  cpu={cpu}%/70%\033[0m')
"
fi

# Metrics Server
echo ""
if kubectl get deployment metrics-server -n kube-system &>/dev/null; then
  READY=$(kubectl get deployment metrics-server -n kube-system -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")
  if [ "$READY" = "1" ]; then
    ok "Metrics Server  ${DIM}(ready)${NC}"
  else
    warn "Metrics Server  ${DIM}(not ready yet)${NC}"
  fi
else
  warn "Metrics Server not installed"
fi

# =========================================================
# 4. Monitoring (Prometheus + Grafana)
# =========================================================
section "Monitoring (namespace: monitoring)"

if kubectl get namespace monitoring &>/dev/null; then
  MON_PODS_JSON=$(kubectl get pods -n monitoring -o json 2>/dev/null || echo '{"items":[]}')
  echo "$MON_PODS_JSON" | python3 -c "
import sys, json
pods = json.load(sys.stdin)['items']
for p in pods:
    name = p['metadata']['name']
    phase = p['status']['phase']
    ready = all(c.get('ready', False) for c in p['status'].get('containerStatuses', []))
    if phase == 'Running' and ready:
        print(f'  \033[0;32m✔\033[0m {name}')
    elif phase == 'Succeeded':
        print(f'  \033[0;32m✔\033[0m {name}  \033[2m(completed)\033[0m')
    else:
        print(f'  \033[0;31m✘\033[0m {name}  \033[2m({phase})\033[0m')
"
else
  warn "monitoring namespace not found (run up-app-only.sh to install)"
fi

# =========================================================
# 5. Health checks
# =========================================================
section "Health checks"

# Direct (NodePort)
DIRECT=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 http://localhost:8080/actuator/health 2>/dev/null || echo "000")
if [ "$DIRECT" = "200" ]; then
  ok "localhost:8080  ${DIM}(NodePort direct)${NC}"
else
  fail "localhost:8080  ${DIM}(HTTP $DIRECT)${NC}"
  ERRORS=$((ERRORS+1))
fi

# via nginx (HTTPS)
NGINX=$(curl -sk -o /dev/null -w "%{http_code}" --connect-timeout 3 https://api.local.dev/actuator/health 2>/dev/null || echo "000")
if [ "$NGINX" = "200" ]; then
  ok "api.local.dev   ${DIM}(nginx → kind)${NC}"
else
  fail "api.local.dev   ${DIM}(HTTP $NGINX)${NC}"
  ERRORS=$((ERRORS+1))
fi

# Auth UI
AUTH_UI=$(curl -sk -o /dev/null -w "%{http_code}" --connect-timeout 3 https://auth.local.dev/ 2>/dev/null || echo "000")
if [ "$AUTH_UI" = "200" ]; then
  ok "auth.local.dev  ${DIM}(auth UI)${NC}"
else
  warn "auth.local.dev  ${DIM}(HTTP $AUTH_UI)${NC}"
fi

# Sample Web
SAMPLE=$(curl -sk -o /dev/null -w "%{http_code}" --connect-timeout 3 https://sample.local.dev/ 2>/dev/null || echo "000")
if [ "$SAMPLE" = "200" ]; then
  ok "sample.local.dev ${DIM}(sample app)${NC}"
else
  warn "sample.local.dev ${DIM}(HTTP $SAMPLE)${NC}"
fi

# Grafana
GRAFANA=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 http://localhost:3100/api/health 2>/dev/null || echo "000")
if [ "$GRAFANA" = "200" ]; then
  ok "localhost:3100  ${DIM}(Grafana)${NC}"
else
  warn "localhost:3100  ${DIM}(Grafana HTTP $GRAFANA)${NC}"
fi

# =========================================================
# 6. Summary
# =========================================================
section "Summary"

if [ "$ERRORS" -eq 0 ]; then
  echo -e "  ${GREEN}${BOLD}All systems operational${NC}"
else
  echo -e "  ${RED}${BOLD}$ERRORS issue(s) detected${NC}"
fi

echo ""
echo -e "${DIM}  Commands:${NC}"
echo -e "${DIM}    Pods:     kubectl get pods -n idp${NC}"
echo -e "${DIM}    Logs:     kubectl logs -n idp -l app=idp-server -f${NC}"
echo -e "${DIM}    HPA:      kubectl get hpa -n idp -w${NC}"
echo -e "${DIM}    Metrics:  kubectl top pods -n idp${NC}"
echo -e "${DIM}    Grafana:  http://localhost:3100 (admin / prom-operator)${NC}"
echo -e "${DIM}    Stop:     bash k8s/local/down-app-only.sh${NC}"
echo ""
