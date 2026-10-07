# Unified LLM Gateway

OpenRouter-style API gateway in **Java 21 + Spring WebFlux**.

- Unified `POST /v1/chat/completions` (OpenAI chat schema)
- Thin **gateway API-key auth** with scopes (reject invalid traffic early)
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
| `gateway.auth.enabled` | `true` | Require API key on protected routes |
| `gateway.auth.keys` | demo key `gw_demo_key` scope `chat` | Override with `GATEWAY_API_KEY` |
| `gateway.default-model` | `assistant-default` | Used when request omits `model` |
| `gateway.models.*.targets` | Anthropic primary, OpenAI secondary | First target = primary; other targets sorted by `cost` ascending on failover |
| `gateway.timeouts.*` | connect 5s, TTFB 15s, idle 30s, … | Per-attempt / stream timeouts |

Cost-failover demo profile: [`src/main/resources/application-cost.yml`](src/main/resources/application-cost.yml) (activated with `--spring.profiles.active=cost`).

### 2) Gateway auth (client → this API)

Protected routes (today: `POST /v1/chat/completions`) require a gateway API key **and** scope `chat`.

Send either:

```http
Authorization: Bearer gw_demo_key
```

or:

```http
X-Api-Key: gw_demo_key
```

| Result | When |
|---|---|
| **401** | Missing / unknown key |
| **403** | Valid key without required scope |
| **200…** | Key ok + scope ok → request continues to router |

`GET /health` stays public. Disable only for experiments: `gateway.auth.enabled=false`.

```bash
export GATEWAY_API_KEY=gw_demo_key   # default in application.yml if unset
```

Add more keys under `gateway.auth.keys` in YAML (id, key, scopes) — expand later for tenants/quotas.

### 3) Upstream provider API keys

Export env vars before starting (do not commit secrets).

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
export GATEWAY_API_KEY=gw_demo_key
export ANTHROPIC_API_KEY=sk-ant-...
export OPENAI_API_KEY=sk-...
```

Without upstream keys, live provider calls fail (failover may still run if another target is healthy).

See also [`.env.example`](.env.example).

### 4) Start the gateway

```bash
export GATEWAY_API_KEY=gw_demo_key
mvn spring-boot:run
```

Health check (no auth):

```bash
curl -sS http://127.0.0.1:43127/health
# {"status":"ok"}
```

---

## Sample curl commands

Use the gateway key on every completions call.

### Streaming chat (default logical model)

```bash
curl -sN http://127.0.0.1:43127/v1/chat/completions \
  -H "Authorization: Bearer ${GATEWAY_API_KEY:-gw_demo_key}" \
  -H 'Content-Type: application/json' \
  -d '{
    "messages": [{"role": "user", "content": "Say hello in one short sentence."}],
    "stream": true
  }'
```

### Streaming with explicit logical model

```bash
curl -sN http://127.0.0.1:43127/v1/chat/completions \
  -H "Authorization: Bearer ${GATEWAY_API_KEY:-gw_demo_key}" \
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
  -H "Authorization: Bearer ${GATEWAY_API_KEY:-gw_demo_key}" \
  -H 'Content-Type: application/json' \
  -d '{
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": false
  }'
```

### Reject invalid traffic (401)

```bash
curl -sS -i http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"Hello"}],"stream":false}'
# HTTP/1.1 401 … missing api key
```

### Unknown model → 404 (auth must still succeed)

```bash
curl -sS -i http://127.0.0.1:43127/v1/chat/completions \
  -H "Authorization: Bearer ${GATEWAY_API_KEY:-gw_demo_key}" \
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
  -H "Authorization: Bearer ${GATEWAY_API_KEY:-gw_demo_key}" \
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
  -H "Authorization: Bearer ${GATEWAY_API_KEY:-gw_demo_key}" \
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
