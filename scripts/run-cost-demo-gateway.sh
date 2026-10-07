#!/usr/bin/env bash
# Gateway for cost-based failover demo (profile: cost).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export GATEWAY_API_KEY="${GATEWAY_API_KEY:-gw_demo_key}"
export ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY:-demo-anthropic}"
export ANTHROPIC_BASE_URL="${ANTHROPIC_BASE_URL:-http://127.0.0.1:18081}"
export OPENAI_EXPENSIVE_API_KEY="${OPENAI_EXPENSIVE_API_KEY:-demo-expensive}"
export OPENAI_EXPENSIVE_BASE_URL="${OPENAI_EXPENSIVE_BASE_URL:-http://127.0.0.1:18082}"
export OPENAI_CHEAP_API_KEY="${OPENAI_CHEAP_API_KEY:-demo-cheap}"
export OPENAI_CHEAP_BASE_URL="${OPENAI_CHEAP_BASE_URL:-http://127.0.0.1:18083}"

echo "Cost demo gateway (spring.profiles.active=cost)"
echo "  client auth: Authorization: Bearer ${GATEWAY_API_KEY}"
echo "  primary:    anthropic @ ${ANTHROPIC_BASE_URL} (cost 15)"
echo "  secondary:  openai-expensive @ ${OPENAI_EXPENSIVE_BASE_URL} (cost 20)"
echo "  secondary:  openai-cheap @ ${OPENAI_CHEAP_BASE_URL} (cost 2) ← should win on failover"
echo "See DEMO.md § Cost-effective failover"
exec mvn -q spring-boot:run -Dspring-boot.run.profiles=cost
