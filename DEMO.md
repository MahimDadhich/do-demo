# Follow-along demo (simple)

Three terminals. Copy/paste only. No real upstream API keys.

Gateway auth default key: **`gw_demo_key`** (scope `chat`). Send  
`Authorization: Bearer gw_demo_key` on completions calls.

---

## Terminal 1 — fake providers

```bash
cd llm-model-router
ANTHROPIC_MOCK_MODE=fail OPENAI_MOCK_MODE=stream python3 scripts/demo_mocks.py
```

Leave it running. Anthropic mock will **fail** (503). OpenAI mock will **stream**.

---

## Terminal 2 — gateway

```bash
cd llm-model-router
./scripts/run-demo-gateway.sh
```

Wait until you see Spring Boot started on port **43127**.

---

## Terminal 3 — curl

### 1. Happy path (silent failover)

Anthropic fails → gateway tries OpenAI → you get a stream.

```bash
curl -sN http://127.0.0.1:43127/v1/chat/completions \
  -H 'Authorization: Bearer gw_demo_key' \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"Hello"}],"stream":true}'
```

**You should see:** SSE `data:` lines with `Hello` / `from OpenAI mock`, then `[DONE]`.  
**In Terminal 2 logs:** `pre_stream_failover` then `provider=openai`.

### 2. Reject missing key (401)

```bash
curl -sS -i http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"x"}],"stream":false}'
```

**You should see:** HTTP `401` (`missing api key`) — no upstream call.

### 3. Unknown model (404)

```bash
curl -sS -i http://127.0.0.1:43127/v1/chat/completions \
  -H 'Authorization: Bearer gw_demo_key' \
  -H 'Content-Type: application/json' \
  -d '{"model":"nope","messages":[{"role":"user","content":"x"}],"stream":false}'
```

**You should see:** HTTP `404` and `model_not_found`.

### 4. Bad request (400)

```bash
curl -sS -i http://127.0.0.1:43127/v1/chat/completions \
  -H 'Authorization: Bearer gw_demo_key' \
  -H 'Content-Type: application/json' \
  -d '{"stream":true}'
```

**You should see:** HTTP `400` (messages missing).
---

## Optional — mid-stream hard fail

Restart Terminal 1 with:

```bash
ANTHROPIC_MOCK_MODE=drop OPENAI_MOCK_MODE=stream python3 scripts/demo_mocks.py
```

Then run the same stream curl from step 1.

**You should see:** one Anthropic chunk (`Hello from Anthropic mock`), then an error frame (not a clean success).  
**You should not see:** a normal completion from OpenAI.  
**Logs:** `stream_locked ... provider=anthropic` then `mid_stream_hard_fail` — OpenAI is **not** called.

---

## Cost-effective failover

Shows: primary fails → among secondaries, **cheaper provider is tried first** even if it was listed after the expensive one in YAML.

Stop previous mocks/gateway if needed (`fuser -k 18081/tcp 18082/tcp 18083/tcp 43127/tcp`).

### Terminal 1 — mocks (primary fail + expensive + cheap)

```bash
DEMO_COST=1 ANTHROPIC_MOCK_MODE=fail OPENAI_MOCK_MODE=stream python3 scripts/demo_mocks.py
```

### Terminal 2 — gateway (cost profile)

```bash
./scripts/run-cost-demo-gateway.sh
```

Config intent (`application-cost.yml`):

| Target | Role | cost |
|---|---|---|
| anthropic | primary | 15 |
| openai-expensive | secondary (listed 2nd in YAML) | 20 |
| openai-cheap | secondary (listed 3rd in YAML) | 2 |

Runtime order after sort: **anthropic → openai-cheap → openai-expensive**.

### Terminal 3 — curl

```bash
curl -sN http://127.0.0.1:43127/v1/chat/completions \
  -H 'Authorization: Bearer gw_demo_key' \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"Hello"}],"stream":true}'
```

**You should see:** stream text `Hello from CHEAP provider` (not EXPENSIVE).  
**Mocks:** anthropic **503**, then **openai-cheap-mock** 200 — expensive mock stays quiet.  
**Logs:** `pre_stream_failover` from anthropic → `stream_locked ... provider=openai-cheap`.
---

More cases: [docs/manual-test-plan.md](docs/manual-test-plan.md)
