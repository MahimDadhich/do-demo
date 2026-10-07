# Non-Functional Requirements

**Status:** Active draft — first slice agreed for streaming architecture, adapter separation, disconnect/timeout handling, and test strategy. Other NFR areas listed at the end remain deferred.

Related docs: [Functional requirements](problem-and-functional-requirements.md) · [Architecture overview](architecture/overview.md)

---

## 1. Request pathway & stream piping (memory)

### Intent

The hot path must move tokens from upstream → client without accumulating the full generation in gateway memory.

### Requirements

| ID | Requirement |
|---|---|
| NFR-S1 | For `stream: true`, the gateway **pipes** SSE (or equivalent chunk frames) as an async byte/event stream. It must not concatenate the full assistant message before responding. |
| NFR-S2 | Peak memory attributable to a single streaming request is **O(chunk)** (read buffer + one in-flight frame), not O(completion size). |
| NFR-S3 | Backpressure: if the client reads slowly, the gateway must not unbounded-buffer upstream chunks. Prefer pausing upstream read / relying on TCP backpressure over growing an in-memory queue. |
| NFR-S4 | Non-stream (`stream: false`) may buffer one full upstream JSON body (provider response size). That path is exempt from NFR-S2 but must still stream the HTTP response to the client once assembled only if the runtime requires a single JSON body (standard chat-completions non-stream). |
| NFR-S5 | No disk spill of completions for MVP. |

### Architecture reference

See [streaming.md](architecture/streaming.md) and the request-path diagram in [overview.md](architecture/overview.md).

---

## 2. Separation of routing vs provider variation (adapter pattern)

### Intent

Routing/failover policy must not contain Anthropic- or OpenAI-specific wire logic. Provider differences live only behind adapters.

### Requirements

| ID | Requirement |
|---|---|
| NFR-A1 | **Router** owns: resolve logical `model` → ordered targets; decide failover eligibility; invoke adapters; enforce pre-stream vs mid-stream rules. |
| NFR-A2 | **Adapter** owns: auth headers, URL, request body mapping, upstream stream parse, unified SSE emit, upstream error → error class. |
| NFR-A3 | Adding a provider is **new adapter + route config only** — no changes to the router’s failover state machine or the public `/v1/chat/completions` handler contract. |
| NFR-A4 | Adapters expose a single interface (see [adapters.md](architecture/adapters.md)): `complete` / `stream` (+ cancel/abort hook). Router never branches on provider name for protocol details. |
| NFR-A5 | Unit tests for routing/failover use **fake adapters**; provider wire tests live in adapter unit tests and mocked-HTTP integration tests. |

### Architecture reference

See [adapters.md](architecture/adapters.md) and [routing-and-failover.md](architecture/routing-and-failover.md).

---

## 3. Partial streams, client disconnects, timeouts

### 3.1 Mid-generation client disconnect

| ID | Requirement |
|---|---|
| NFR-D1 | When the client connection closes or aborts mid-stream, the gateway **cancels the upstream request** promptly (abort controller / connection close). It must not continue consuming or paying for a full generation after the client is gone. |
| NFR-D2 | Client disconnect is **not** a failover trigger. Do not start the next target for that request. |
| NFR-D3 | Treat disconnect as a normal terminal outcome in logs/metrics (`client_disconnected`), not as a 5xx attributed to an upstream. |

### 3.2 Partial / mid-stream upstream failure

| ID | Requirement |
|---|---|
| NFR-D4 | After the first unified SSE chunk has been written to the client, any upstream failure is a **hard failure**: end the client stream with an error frame or abrupt close per SSE error policy; **no** silent provider switch (aligned with FRs). |
| NFR-D5 | Partial content already sent is left as-is; the gateway does not rewind or rewrite prior chunks. |

### 3.3 Timeout strategy (targeted)

Timeouts are layered so “stuck connect” fails over, while “slow but live tokens” are allowed within idle bounds.

| ID | Timeout | Applies to | On expiry (pre-stream) | On expiry (mid-stream) |
|---|---|---|---|---|
| NFR-T1 | **Connect / TLS** | Opening upstream TCP+TLS | Failover-eligible | N/A |
| NFR-T2 | **Upstream headers / first byte** (TTFB) | Time until upstream response headers (and first body byte for stream) | Failover-eligible | N/A |
| NFR-T3 | **Inter-chunk idle** | Gap between successive upstream chunks after stream started | N/A | Hard failure to client; cancel upstream |
| NFR-T4 | **Overall request ceiling** | Wall clock for entire attempt (and optionally entire client request including fallbacks) | Failover-eligible if no bytes sent; else hard fail | Hard failure |

**Defaults for MVP docs** (tunable via config later):

| Timeout | Default |
|---|---|
| Connect | 5s |
| Headers / first byte | 15s |
| Inter-chunk idle | 30s |
| Overall per-attempt | 120s |
| Overall client request (all attempts) | 180s |
| Max fallback targets tried | 3 (including primary) |

Exact numbers can change in config; the **layering and failover eligibility** above are the NFR.

### 3.4 Client-side / request errors (no failover)

| ID | Requirement |
|---|---|
| NFR-E1 | Invalid JSON, schema validation failure, unsupported fields → **4xx** to client; no upstream call (or no failover). |
| NFR-E1b | Unknown logical `model` (after applying default) → **404** with code `model_not_found`; no upstream call; no failover. |
| NFR-E2 | Upstream **4xx** that indicate bad request mapping (after adapter translation) → surface to client; **do not** failover. |
| NFR-E3 | Upstream **429 / 5xx / network / timeout (pre-stream)** → failover-eligible. |

---

## 4. Testing strategy (unit + integration)

### Intent

Prove piping, adapter isolation, failover boundaries, disconnect abort, and timeouts without requiring live provider keys in CI.

### 4.1 Unit tests

| Area | Must cover |
|---|---|
| Router / failover | Ordered targets; stop after success; failover on 429/5xx/timeout; no failover after first chunk; no failover on client disconnect; max depth |
| Error classification | Maps status/exception → `failover_eligible` vs `client_error` vs `upstream_fatal` |
| Adapters (each) | Request mapping snapshots; SSE/chunk mapping from fixture bytes; error mapping |
| Timeout policy | Which timer fires → which action (failover vs hard fail) |

Use in-memory **fake adapters** that emit scripted chunk sequences and scripted failures.

### 4.2 Integration tests

| Scenario | Expectation |
|---|---|
| Happy stream | Mock upstream SSE → client receives chunked SSE; gateway heap/buffer stays bounded (assert via chunked write spy / no full-body join) |
| Pre-stream primary fail | Mock primary 503, secondary 200 stream → client sees one successful stream; no client-visible failover signal required |
| Mid-stream upstream drop | After N chunks, upstream closes with error → client sees hard failure; secondary **not** called |
| Client abort | Client cancels mid-stream → upstream fetch aborted (assert mock received abort); no fallback |
| Connect / TTFB timeout | Mock hang before headers → failover or final error per policy |
| Inter-chunk idle timeout | Mock pause between chunks beyond idle → hard fail, upstream cancelled |
| Non-stream completion | Mock JSON body → single JSON response |

Mock upstream with a local HTTP test server (or handler) that can stream, hang, abort, and return status codes.

### 4.3 Out of scope for first test slice

- Live calls to Anthropic/OpenAI in CI
- Load/soak performance benches (may come with later NFRs)
- Contract tests against real provider sandboxes (optional later)

---

## 5. Deferred NFR topics (later)

Not blocking this architecture slice; capture next:

- Broader latency SLOs and concurrency limits
- Observability field catalog (beyond disconnect/failover counters implied above)
- Config hot-reload and secrets management
- Target runtime/stack and deployment shape
- Auth for the gateway’s own clients
- Rate limiting / abuse protection
