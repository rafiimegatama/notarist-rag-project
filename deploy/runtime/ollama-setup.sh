#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Install + configure a local Ollama runtime for the Notarist AI Runtime Platform.
#
# Target box:  RTX 5060 Ti 16 GB (Blackwell) · 32 GB DDR5 · Ryzen 5 7500F (6c/12t)
#              Windows 11 + WSL2 + Docker Desktop. Run this INSIDE the WSL2 distro.
#
# Provisions LLM_PROVIDER=ollama / EMBED_PROVIDER=ollama. Idempotent.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Models (override via env) ───────────────────────────────────────────────
# Chat/reasoning default: qwen3:14b (native thinking) at Q4_K_M ≈ 9 GB — fits 16 GB with room
# for the KV cache and a resident embedding model. See README for the full matrix.
CHAT_MODEL="${CHAT_MODEL:-qwen3:14b}"
LIGHT_MODEL="${LIGHT_MODEL:-llama3.1:8b-instruct-q8_0}"   # lighter fallback / higher concurrency
EMBED_MODEL="${EMBED_MODEL:-bge-m3}"                       # 1024-dim (matches the fixed index)
PULL_LIGHT="${PULL_LIGHT:-false}"                          # set true to also pull the 8B fallback

echo "==> 1/5 Install Ollama (if missing)"
if ! command -v ollama >/dev/null 2>&1; then
  curl -fsSL https://ollama.com/install.sh | sh
else
  echo "    already installed: $(ollama --version 2>/dev/null || true)"
fi

echo "==> 2/5 Verify the NVIDIA GPU is visible in WSL2"
if command -v nvidia-smi >/dev/null 2>&1; then
  nvidia-smi --query-gpu=name,memory.total,driver_version --format=csv,noheader || true
else
  echo "    WARNING: nvidia-smi not found in WSL2. Install the Windows NVIDIA driver (WSL CUDA is"
  echo "    exposed automatically) — without it Ollama runs on CPU and inference is unusably slow."
fi

echo "==> 3/5 Configure Ollama for the 16 GB GPU (systemd override)"
# ── GPU optimization rationale (RTX 5060 Ti 16 GB) ──────────────────────────
#   OLLAMA_FLASH_ATTENTION=1   Flash-Attention: lower KV memory + faster attention on Blackwell.
#   OLLAMA_KV_CACHE_TYPE=q8_0  Quantize the KV cache → ~half the VRAM of f16, negligible quality
#                              loss; this is what lets a 14B Q4 hold an 8–16k context in 16 GB.
#   OLLAMA_CONTEXT_LENGTH=8192 Default context. 8k is the safe GPU-resident ceiling for 14B Q4;
#                              raise to 16384 only after watching VRAM headroom (see verify.sh).
#   OLLAMA_NUM_PARALLEL=2      Two concurrent slots — the KV cache is split across them, so keep it
#                              at 2 on 16 GB (4 would starve context). 1 for max single-request ctx.
#   OLLAMA_MAX_LOADED_MODELS=2 Keep chat (~9 GB) + bge-m3 (~1.2 GB) co-resident (~10–11 GB) so the
#                              embed path never evicts the chat model. Leaves ~4–5 GB for KV.
#   OLLAMA_KEEP_ALIVE=30m      Keep models hot for 30 min of idle, then unload to free VRAM. Use -1
#                              to pin forever (single-tenant box), or 0 to unload immediately.
#   OLLAMA_SCHED_SPREAD=0      Single GPU — do not spread layers; keep the whole model on one device.
# GPU-first / avoid CPU fallback: with the above, qwen3:14b Q4 loads 100% onto the GPU. Ollama only
# spills layers to CPU (10–50× slower) when a model + KV exceeds VRAM — so cap context / model size
# rather than let it offload. num_gpu is left at its default (all layers) — do NOT lower it.
sudo mkdir -p /etc/systemd/system/ollama.service.d
sudo tee /etc/systemd/system/ollama.service.d/override.conf >/dev/null <<'EOF'
[Service]
Environment="OLLAMA_HOST=0.0.0.0:11434"
Environment="OLLAMA_FLASH_ATTENTION=1"
Environment="OLLAMA_KV_CACHE_TYPE=q8_0"
Environment="OLLAMA_CONTEXT_LENGTH=8192"
Environment="OLLAMA_NUM_PARALLEL=2"
Environment="OLLAMA_MAX_LOADED_MODELS=2"
Environment="OLLAMA_KEEP_ALIVE=30m"
Environment="OLLAMA_SCHED_SPREAD=0"
EOF

if command -v systemctl >/dev/null 2>&1 && systemctl list-unit-files ollama.service >/dev/null 2>&1; then
  sudo systemctl daemon-reload
  sudo systemctl enable --now ollama
  sudo systemctl restart ollama
  sleep 3
else
  echo "    systemd/ollama.service not managed here. Start manually with the same env, e.g.:"
  echo "      OLLAMA_HOST=0.0.0.0:11434 OLLAMA_FLASH_ATTENTION=1 OLLAMA_KV_CACHE_TYPE=q8_0 \\"
  echo "      OLLAMA_NUM_PARALLEL=2 OLLAMA_MAX_LOADED_MODELS=2 OLLAMA_KEEP_ALIVE=30m ollama serve &"
fi

echo "==> 4/5 Pull models"
echo "    chat/reasoning: $CHAT_MODEL"; ollama pull "$CHAT_MODEL"
echo "    embedding:      $EMBED_MODEL"; ollama pull "$EMBED_MODEL"
if [ "$PULL_LIGHT" = "true" ]; then
  echo "    light fallback: $LIGHT_MODEL"; ollama pull "$LIGHT_MODEL"
fi

echo "==> 5/5 Smoke test"
ollama run "$CHAT_MODEL" "Reply with one word: ready" || true
curl -s http://localhost:11434/api/embed -d "{\"model\":\"$EMBED_MODEL\",\"input\":\"halo dunia\"}" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('embedding dim =',len(d['embeddings'][0]))" || true

cat <<EOF

Done. Point the backend at this host:
    LLM_PROVIDER=ollama     LLM_MODEL=$CHAT_MODEL
    EMBED_PROVIDER=ollama   EMBED_MODEL=$EMBED_MODEL
    RERANK_PROVIDER=none    (or crossencoder + a bge-reranker sidecar)
    OLLAMA_BASE_URL=http://<wsl-host>:11434
Then run ./verify.sh and check GET /actuator/health → aiRuntime.
EOF
