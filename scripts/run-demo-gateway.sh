#!/usr/bin/env bash
# Start the gateway against local demo mocks (ports 18081 / 18082).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY:-demo-anthropic}"
export OPENAI_API_KEY="${OPENAI_API_KEY:-demo-openai}"
export ANTHROPIC_BASE_URL="${ANTHROPIC_BASE_URL:-http://127.0.0.1:18081}"
export OPENAI_BASE_URL="${OPENAI_BASE_URL:-http://127.0.0.1:18082}"

echo "Gateway → Anthropic mock ${ANTHROPIC_BASE_URL} , OpenAI mock ${OPENAI_BASE_URL}"
echo "Docs: docs/manual-test-plan.md"
exec mvn -q spring-boot:run
