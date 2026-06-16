#!/usr/bin/env bash
set -euo pipefail

# Headroom + TokenTelemetry integration setup
# Routes Claude API calls through Headroom's proxy while TokenTelemetry
# captures session traces, token usage, and cost analytics locally.

HEADROOM_PORT="${HEADROOM_PORT:-8787}"
TT_API_PORT="${TT_API_PORT:-8000}"
TT_FRONTEND_PORT="${TT_FRONTEND_PORT:-3000}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== Headroom + TokenTelemetry Integration ==="
echo ""

# 1. Start Headroom proxy
echo "[1/3] Starting Headroom proxy on port $HEADROOM_PORT..."
if curl -s "http://127.0.0.1:$HEADROOM_PORT/health" >/dev/null 2>&1; then
    echo "  Headroom proxy already running."
else
    headroom proxy --port "$HEADROOM_PORT" &
    HEADROOM_PID=$!
    sleep 2
    echo "  Headroom proxy started (PID $HEADROOM_PID)."
fi

# 2. Start TokenTelemetry backend
echo "[2/3] Starting TokenTelemetry backend on port $TT_API_PORT..."
if curl -s "http://127.0.0.1:$TT_API_PORT/health" >/dev/null 2>&1; then
    echo "  TokenTelemetry backend already running."
else
    cd "$SCRIPT_DIR/tokentelemetry/backend"
    python3 -m uvicorn main:app --host 127.0.0.1 --port "$TT_API_PORT" &
    TT_PID=$!
    sleep 2
    echo "  TokenTelemetry backend started (PID $TT_PID)."
    cd "$SCRIPT_DIR"
fi

# 3. Export environment for Claude Code
echo "[3/3] Configuring environment..."
export ANTHROPIC_BASE_URL="http://127.0.0.1:$HEADROOM_PORT"

echo ""
echo "=== Ready ==="
echo "  Headroom proxy:         http://127.0.0.1:$HEADROOM_PORT"
echo "  TokenTelemetry API:     http://127.0.0.1:$TT_API_PORT"
echo "  TokenTelemetry UI:      http://127.0.0.1:$TT_FRONTEND_PORT"
echo "  ANTHROPIC_BASE_URL:     $ANTHROPIC_BASE_URL"
echo ""
echo "  API calls route through Headroom (compression + token optimization)."
echo "  TokenTelemetry reads Claude session logs from ~/.claude/ for"
echo "  real-time cost tracking, tool-call traces, and per-model analytics."
echo ""
echo "  To launch Claude Code through this stack:"
echo "    ANTHROPIC_BASE_URL=http://127.0.0.1:$HEADROOM_PORT claude"
