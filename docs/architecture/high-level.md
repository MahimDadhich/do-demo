# High-level architecture (10,000 ft)

A short map of the system. Details live in later docs — this page only answers: **what are the big boxes, and how do they relate?**

## What this system is

A **unified LLM API gateway**. Clients call one chat-completions API. The gateway picks an upstream provider, translates the call, and streams the answer back. If the first provider fails **before** any tokens are sent, it can try the next one silently.

```text
┌─────────────┐         ┌──────────────────────────────────────┐         ┌─────────────┐
│   Clients   │────────▶│         Unified LLM Gateway          │────────▶│  Upstream   │
│  (SDKs /    │  one    │                                      │  many    │  LLMs       │
│   apps)     │◀────────│  route → adapt → pipe stream         │◀────────│  (Anthropic │
└─────────────┘  SSE /  │                                      │  native  │   OpenAI …) │
                 JSON   └──────────────────────────────────────┘  APIs    └─────────────┘
```

## Big boxes

| Box | One-line job |
|---|---|
| **Edge API** | Expose `POST /v1/chat/completions` in a single OpenAI-style schema |
| **Router** | Turn logical `model` (default `assistant-default`) into an ordered target list; run failover policy |
| **Adapters** | Hide each provider’s URL, auth, body, and stream format |
| **Stream pipe** | Forward tokens as they arrive; do not hold the full answer in memory |
| **Config** | Declare routes (logical model → ordered targets) and credentials references |

## Design principles (keep these visible)

1. **One dialect in** — clients never speak Anthropic vs OpenAI wire formats.
2. **Adapters out** — provider quirks stop at the adapter boundary.
3. **Pipe, don’t buffer** — streaming responses are frame-sized in memory.
4. **Failover before first byte** — after the stream is open to the client, failures are hard failures.

## What this view intentionally omits

Timeouts, error matrices, class shapes, and step-by-step sequences — see the next architecture docs in the reading order.

## Next

→ [Request flows & sequences](request-flows.md)
