# Architecture overview

This document describes the request pathway for `POST /v1/chat/completions`: how routing, adapters, and stream piping interact. Detailed rules live in sibling docs.

## Components

| Component | Responsibility |
|---|---|
| **HTTP handler** | Validate unified request; open client response (JSON or SSE); attach abort on client disconnect |
| **Router** | Resolve `model` → ordered targets; run attempt loop; enforce pre-stream failover vs mid-stream hard fail |
| **Adapter registry** | Map `provider` id → adapter implementation |
| **Provider adapter** | Wire-format translate; call upstream; normalize chunks/errors; honor abort |
| **Timeout policy** | Connect, TTFB, inter-chunk idle, overall ceilings |
| **Route config** | Logical model → `[{ provider, upstreamModel, credentialsRef }, …]` |

## Request path (happy stream)

```mermaid
sequenceDiagram
  participant C as Client
  participant H as HTTPHandler
  participant R as Router
  participant A as Adapter
  participant U as UpstreamLLM

  C->>H: POST /v1/chat/completions stream=true
  H->>H: Validate unified schema
  H->>R: route(request, abortSignal)
  R->>R: Resolve ordered targets
  R->>A: stream(attempt, abortSignal)
  A->>U: Provider-native streaming request
  U-->>A: Chunk N
  A-->>R: Unified SSE frame
  R-->>H: Pipe frame
  H-->>C: SSE data line
  Note over R,H: No full-body buffer; frame-sized memory only
  U-->>A: Done
  A-->>R: End
  H-->>C: [DONE] / close
```

## Attempt loop (failover boundary)

```mermaid
flowchart TD
  startNode[Start attempt i] --> callAdapter[Adapter open upstream]
  callAdapter --> preCheck{Headers or first byte OK?}
  preCheck -->|No failover-eligible error| moreTargets{More targets?}
  moreTargets -->|Yes| nextAttempt[Attempt i+1]
  nextAttempt --> callAdapter
  moreTargets -->|No| failClient[Error to client]
  preCheck -->|Yes| markOpen[Mark stream opened to client]
  markOpen --> pipeLoop[Pipe chunks with idle timeout]
  pipeLoop --> chunkOK{Chunk or end?}
  chunkOK -->|Chunk| writeClient[Write SSE to client]
  writeClient --> pipeLoop
  chunkOK -->|Clean end| success[Complete]
  chunkOK -->|Upstream fail or idle timeout| hardFail[Hard fail to client]
  hardFail --> cancelUp[Cancel upstream]
  cancelUp --> stopNoFailover[Stop no failover]
```

## Trust / isolation boundaries

- **Client-facing:** unified OpenAI-style schema only.
- **Router:** provider-agnostic; only sees adapter results and error classes.
- **Adapter:** only place that knows Anthropic vs OpenAI URLs, headers, and stream codecs.
- **Secrets:** credentials resolved by `credentialsRef` inside adapter (or a small credentials helper used by adapters) — not logged.

## Sibling docs

- [Streaming & memory](streaming.md)
- [Adapters](adapters.md)
- [Routing & failover](routing-and-failover.md)
- [NFRs](../non-functional-requirements.md)
