# Follow-along demo (simple)

Three terminals. Copy/paste only. No real API keys.

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
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"Hello"}],"stream":true}'
```

**You should see:** SSE `data:` lines with `Hello` / `from OpenAI mock`, then `[DONE]`.  
**In Terminal 2 logs:** `pre_stream_failover` then `provider=openai`.

### 2. Unknown model (404)

```bash
curl -sS -i http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"model":"nope","messages":[{"role":"user","content":"x"}],"stream":false}'
```

**You should see:** HTTP `404` and `model_not_found`.

### 3. Bad request (400)

```bash
curl -sS -i http://127.0.0.1:43127/v1/chat/completions \
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

More cases: [docs/manual-test-plan.md](docs/manual-test-plan.md)
