# Unified LLM Gateway

OpenRouter-style API gateway in **Java 21 + Spring WebFlux**.

- Unified `POST /v1/chat/completions` (OpenAI chat schema)
- Config-driven routing with **primary** + **cost-ordered secondary** failover
- SSE streaming proxy (no full-response buffering)
- Pre-stream silent failover; mid-stream failures are hard errors

Default listen address: **[http://127.0.0.1:43127](http://127.0.0.1:43127)**

---

## Prerequisites

- Java **21+**
- Maven **3.9+** (`mvn`)
- Python **3** (only for local mock demos)

```bash
git clone <your-remote> llm-model-router
cd llm-model-router
```

---

## Local deployment

### 1) Configuration

Main config: [`src/main/resources/application.yml`](src/main/resources/application.yml)

| Setting | Default | Notes |
|---|---|---|
| `server.port` | `43127` | HTTP port |
| `gateway.default-model` | `assistant-default` | Used when request omits `model` |
| `gateway.models.*.targets` | Anthropic primary, OpenAI secondary | First target = primary; other targets sorted by `cost` ascending on failover |
| `gateway.timeouts.*` | connect 5s, TTFB 15s, idle 30s, … | Per-attempt / stream timeouts |

Cost-failover demo profile: [`src/main/resources/application-cost.yml`](src/main/resources/application-cost.yml) (activated with `--spring.profiles.active=cost`).

### 2) API keys & provider URLs

Export env vars before starting (or put them in a local `.env` you source yourself — do not commit secrets).

| Variable | Purpose |
|---|---|
| `ANTHROPIC_API_KEY` | Anthropic Messages API key |
| `ANTHROPIC_BASE_URL` | Optional override (default `https://api.anthropic.com`) |
| `OPENAI_API_KEY` | OpenAI Chat Completions API key |
| `OPENAI_BASE_URL` | Optional override (default `https://api.openai.com/v1`) |
| `OPENAI_CHEAP_API_KEY` / `OPENAI_CHEAP_BASE_URL` | Optional second OpenAI-compatible provider (`openai-cheap`) |
| `OPENAI_EXPENSIVE_API_KEY` / `OPENAI_EXPENSIVE_BASE_URL` | Optional third OpenAI-compatible provider (`openai-expensive`) |

Example (real providers):

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export OPENAI_API_KEY=sk-...
# optional:
# export ANTHROPIC_BASE_URL=https://api.anthropic.com
# export OPENAI_BASE_URL=https://api.openai.com/v1
```

Without keys, live upstream calls fail (failover may still run if another target is configured and healthy).

See also [`.env.example`](.env.example).

### 3) Start the gateway

```bash
mvn spring-boot:run
```

Health check:

```bash
curl -sS http://127.0.0.1:43127/health
# {"status":"ok"}
```

---

## Sample curl commands

### Streaming chat (default logical model)

Omit `model` → uses `assistant-default` (primary Anthropic, then cheaper secondaries on pre-stream failure).

```bash
curl -sN http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{
    "messages": [{"role": "user", "content": "Say hello in one short sentence."}],
    "stream": true
  }'
```

### Streaming with explicit logical model

```bash
curl -sN http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "assistant-default",
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": true
  }'
```

### Non-streaming JSON

```bash
curl -sS http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": false
  }'
```

### Unknown model → 404

```bash
curl -sS -i http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "does-not-exist",
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": false
  }'
```

---

## Local demos (no real API keys)

Copy/paste walkthrough: **[DEMO.md](DEMO.md)**  
Full curl matrix: **[docs/manual-test-plan.md](docs/manual-test-plan.md)**

### Basic silent failover

```bash
# terminal 1 — Anthropic mock 503, OpenAI mock streams
ANTHROPIC_MOCK_MODE=fail OPENAI_MOCK_MODE=stream python3 scripts/demo_mocks.py

# terminal 2
./scripts/run-demo-gateway.sh

# terminal 3
curl -sN http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"Hello"}],"stream":true}'
```

### Cost-effective failover

Primary fails → cheapest secondary wins (even if listed after a pricier one in YAML).

```bash
# terminal 1
DEMO_COST=1 ANTHROPIC_MOCK_MODE=fail OPENAI_MOCK_MODE=stream python3 scripts/demo_mocks.py

# terminal 2
./scripts/run-cost-demo-gateway.sh

# terminal 3 — expect "CHEAP provider" in the stream
curl -sN http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"Hello"}],"stream":true}'
```

---

## Tests / CI

```bash
mvn test
```

GitHub Actions runs the same suite on push/PR: [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

---

## Documentation

| Document | Purpose |
|---|---|
| [DEMO.md](DEMO.md) | Follow-along local demos |
| [docs/manual-test-plan.md](docs/manual-test-plan.md) | Curl test matrix |
| [docs/problem-and-functional-requirements.md](docs/problem-and-functional-requirements.md) | FRs |
| [docs/non-functional-requirements.md](docs/non-functional-requirements.md) | NFRs |
| [docs/architecture/README.md](docs/architecture/README.md) | Architecture (high-level → flows → classes) |

---

## Layout

```text
src/main/java/com/digitalocean/llmgateway/
  http/        Chat completions + health + errors
  router/      Failover / attempt loop
  adapters/    Anthropic + OpenAI-compatible providers
  config/      Routes, timeouts, adapter beans
  types/       Unified request / SSE DTOs
scripts/
  demo_mocks.py              Local fake upstreams
  run-demo-gateway.sh        Gateway → basic mocks
  run-cost-demo-gateway.sh   Gateway → cost-demo profile
```
