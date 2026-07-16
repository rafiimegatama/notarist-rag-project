#!/usr/bin/env bash
# Quick runtime health probe — Ollama loaded models (/api/ps) + the backend's aiRuntime health.
#   OLLAMA_URL   default http://localhost:11434
#   BACKEND_URL  default http://localhost:8080
set -uo pipefail
OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"

echo "== Ollama loaded models (/api/ps) =="
curl -sf "$OLLAMA_URL/api/ps" | python3 -m json.tool 2>/dev/null || echo "  (ollama unreachable)"

echo "== Backend aiRuntime health =="
curl -sf "$BACKEND_URL/actuator/health" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print(json.dumps(d.get('components',{}).get('aiRuntime',d),indent=2))" 2>/dev/null \
  || echo "  (backend unreachable or health details not exposed)"
