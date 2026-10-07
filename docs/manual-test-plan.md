# Manual test / demo plan (curl)

Use this for a live demo of success, client errors, and **pre-stream failover**. Automated unit tests cover router logic; this plan proves the running HTTP surface.

**Gateway base URL:** `http://127.0.0.1:43127`  
**Endpoint:** `POST /v1/chat/completions`

---

## Demo modes

| Mode | When to use |
|---|---|
| **A — Mock upstreams (recommended for demos)** | No real Anthropic/OpenAI keys; full control over 503 / stream / mid-stream drop |
| **B — Real providers** | Keys available; shows production-shaped success only |

---

## Mode A — Mock upstreams (failover demo)

### A0. Terminal layout

Use **3 terminals**:

1. Mock upstreams  
2. Gateway  
3. curl commands  

### A1. Start mocks (primary fails → secondary streams)

```bash
cd /path/to/llm-model-router
ANTHROPIC_MOCK_MODE=fail OPENAI_MOCK_MODE=stream python3 scripts/demo_mocks.py
```

- Anthropic mock: `http://127.0.0.1:18081` → **503**  
- OpenAI mock: `http://127.0.0.1:18082` → short SSE success  

### A2. Start gateway pointed at mocks

```bash
export ANTHROPIC_API_KEY=demo-anthropic
export OPENAI_API_KEY=demo-openai
export ANTHROPIC_BASE_URL=http://127.0.0.1:18081
export OPENAI_BASE_URL=http://127.0.0.1:18082
mvn -q spring-boot:run
```

Watch gateway logs for `pre_stream_failover` and `stream_locked ... provider=openai`.

### A3. Curl cases

#### 1) Success via silent failover (stream)

Primary (Anthropic) returns 503; gateway falls back to OpenAI mock. Client sees a normal SSE stream (no failover banner).

```bash
curl -sN http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{
    "messages": [{"role":"user","content":"Hello"}],
    "stream": true
  }'
```

**Expect:**
- HTTP 200, `text/event-stream`
- `data: {...}` chunks containing `"Hello"` / `" from OpenAI mock"`
- Final `data: [DONE]`
- Gateway log: `pre_stream_failover` then `stream_locked ... provider=openai`

#### 2) Default logical model (omit `model`)

Same as above — omit `model` so router uses `assistant-default`.

```bash
curl -sN http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"ping"}],"stream":true}'
```

**Expect:** same successful stream (default route).

#### 3) Explicit logical model

```bash
curl -sN http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "assistant-default",
    "messages": [{"role":"user","content":"ping"}],
    "stream": true
  }'
```

**Expect:** success (same route).

#### 4) Unknown model → 404 (no upstream call)

```bash
curl -sS -i http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "does-not-exist",
    "messages": [{"role":"user","content":"ping"}],
    "stream": false
  }'
```

**Expect:**
- HTTP **404**
- JSON body with `model_not_found` / `gateway_error`
- Mock servers receive **no** request

#### 5) Validation error → 400

```bash
curl -sS -i http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"stream":true}'
```

**Expect:** HTTP **400** (missing `messages`). No failover.

#### 6) Non-stream success after failover

```bash
curl -sS -i http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{
    "messages": [{"role":"user","content":"Hello"}],
    "stream": false
  }'
```

**Expect:** HTTP 200 JSON completion; content like `Hello from OpenAI mock`; log shows failover from Anthropic → OpenAI.

#### 7) Both upstreams fail → final error

Restart mocks with both failing:

```bash
# stop previous demo_mocks.py, then:
ANTHROPIC_MOCK_MODE=fail OPENAI_MOCK_MODE=fail python3 scripts/demo_mocks.py
```

Restart or keep gateway (same env). Then:

```bash
curl -sS -i http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"x"}],"stream":false}'
```

**Expect:** error after both attempts (5xx-class gateway error); logs show two failed attempts.

#### 8) Primary success (no failover)

```bash
# stop mocks, restart:
ANTHROPIC_MOCK_MODE=stream OPENAI_MOCK_MODE=fail python3 scripts/demo_mocks.py
```

```bash
curl -sN http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"Hi"}],"stream":true}'
```

**Expect:** stream text `Hello from Anthropic mock`; log `stream_locked ... provider=anthropic`; OpenAI mock **not** called (or only if you force a failure).

#### 9) Mid-stream hard failure (no failover)

```bash
ANTHROPIC_MOCK_MODE=drop OPENAI_MOCK_MODE=stream python3 scripts/demo_mocks.py
```

```bash
curl -sN http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"Hi"}],"stream":true}'
```

**Expect:**
- One early SSE chunk (`Hello from Anthropic mock`)
- Then an error (not a clean `data:[DONE]` success)
- Gateway log: `stream_locked ... anthropic` then `mid_stream_hard_fail`
- OpenAI mock **not** called (no `[openai-mock]` line for that request)

#### 10) Client disconnect (optional)

```bash
curl -sN --max-time 1 http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"Hi"}],"stream":true}'
```

Or start a stream and press **Ctrl+C**.

**Expect:** curl exits; gateway may log `client_disconnected`; **no** failover to next provider.

---

## Mode B — Real providers

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export OPENAI_API_KEY=sk-...
# unset mock base URLs so defaults apply
unset ANTHROPIC_BASE_URL OPENAI_BASE_URL
mvn -q spring-boot:run
```

```bash
curl -sN http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{
    "messages": [{"role":"user","content":"Say hello in one short sentence."}],
    "stream": true
  }'
```

**Expect:** live tokens from Anthropic (primary). To force OpenAI fallback, temporarily set a bad `ANTHROPIC_API_KEY` and keep a valid `OPENAI_API_KEY`.

---

## Cost-effective failover demo

```bash
# terminal 1
DEMO_COST=1 ANTHROPIC_MOCK_MODE=fail OPENAI_MOCK_MODE=stream python3 scripts/demo_mocks.py

# terminal 2
./scripts/run-cost-demo-gateway.sh

# terminal 3
curl -sN http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"Hello"}],"stream":true}'
```

**Expect:** body mentions `CHEAP provider`; logs lock `openai-cheap` (cost 2) not `openai-expensive` (cost 20), even though expensive is listed earlier among secondaries in YAML.

## Suggested 5-minute demo script

1. **404 unknown model** (case 4) — shows routing is config-driven.  
2. **Failover stream** (case 1) with mocks — show 503 on Anthropic mock terminal + success SSE + `pre_stream_failover` log.  
3. **Cost-effective failover** — cheaper secondary wins over expensive.  
4. **Mid-stream hard fail** (case 9) — contrast: no silent switch after first token.  
5. (Optional) **Real key** one-liner (Mode B) if credentials are available.

---

## Checklist

| # | Scenario | Pass? |
|---|---|---|
| 1 | Stream success via Anthropic→OpenAI failover | |
| 2 | Default model when `model` omitted | |
| 3 | Explicit `assistant-default` | |
| 4 | Unknown model → 404 | |
| 5 | Missing messages → 400 | |
| 6 | Non-stream JSON after failover | |
| 7 | All targets fail → error | |
| 8 | Primary stream success (no failover) | |
| 9 | Mid-stream drop → hard fail, no OpenAI | |
| 10 | Client abort / max-time | |
