#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Verify the local AI runtime end to end: Ollama reachable, GPU in use, required models
# present, embedding dimension correct, chat + streaming working. Non-destructive.
#
#   OLLAMA_URL   default http://localhost:11434
#   CHAT_MODEL   default qwen3:14b
#   EMBED_MODEL  default bge-m3
#   EMBED_DIM    default 1024   (the platform's fixed vector dimension)
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}"
CHAT_MODEL="${CHAT_MODEL:-qwen3:14b}"
EMBED_MODEL="${EMBED_MODEL:-bge-m3}"
EMBED_DIM="${EMBED_DIM:-1024}"
fail=0
pass() { echo "  [PASS] $1"; }
bad()  { echo "  [FAIL] $1"; fail=1; }

echo "==> Ollama reachable at $OLLAMA_URL"
if curl -sf "$OLLAMA_URL/api/tags" >/dev/null; then pass "GET /api/tags"; else bad "cannot reach $OLLAMA_URL"; fi

echo "==> GPU present"
if command -v nvidia-smi >/dev/null 2>&1 && nvidia-smi >/dev/null 2>&1; then
  nvidia-smi --query-gpu=name,memory.used,memory.total,utilization.gpu --format=csv,noheader | sed 's/^/  /'
  pass "nvidia-smi ok"
else
  bad "nvidia-smi unavailable — inference may be on CPU"
fi

echo "==> Required models present"
tags="$(curl -sf "$OLLAMA_URL/api/tags" || echo '{}')"
for m in "$CHAT_MODEL" "$EMBED_MODEL"; do
  if echo "$tags" | python3 -c "import sys,json;d=json.load(sys.stdin);import sys as s;s.exit(0 if any(x['name']=='$m' or x['name'].startswith('$m') for x in d.get('models',[])) else 1)"; then
    pass "model $m"
  else
    bad "model $m missing — run: ollama pull $m"
  fi
done

echo "==> Embedding dimension == $EMBED_DIM"
dim="$(curl -sf "$OLLAMA_URL/api/embed" -d "{\"model\":\"$EMBED_MODEL\",\"input\":\"verify\"}" \
  | python3 -c "import sys,json;print(len(json.load(sys.stdin)['embeddings'][0]))" 2>/dev/null || echo 0)"
if [ "$dim" = "$EMBED_DIM" ]; then pass "dim=$dim"; else bad "dim=$dim (expected $EMBED_DIM) — wrong embed model?"; fi

echo "==> Chat generation (non-streaming)"
ans="$(curl -sf "$OLLAMA_URL/api/chat" -d "{\"model\":\"$CHAT_MODEL\",\"stream\":false,\"messages\":[{\"role\":\"user\",\"content\":\"Reply with one word: ok\"}]}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['message']['content'][:40])" 2>/dev/null || echo '')"
if [ -n "$ans" ]; then pass "chat responded: ${ans}"; else bad "chat produced no content"; fi

echo "==> Streaming (first NDJSON token arrives)"
if curl -sf -N "$OLLAMA_URL/api/chat" -d "{\"model\":\"$CHAT_MODEL\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"count 1 2 3\"}]}" \
  | head -c 200 | grep -q '"message"'; then pass "stream produced tokens"; else bad "no streaming tokens"; fi

echo
if [ "$fail" -eq 0 ]; then echo "ALL CHECKS PASSED"; else echo "SOME CHECKS FAILED"; fi
exit "$fail"
