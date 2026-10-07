# Request flows & sequences

End-to-end behavior after the [10,000 ft view](high-level.md). This page is the **movie** of a request — not class internals.

## Actors

| Actor | Role |
|---|---|
| Client | App/SDK calling the gateway |
| Handler | HTTP edge: validate, open response, wire abort |
| Router | Target list + attempt/failover loop |
| Adapter | Provider-specific translate + upstream I/O |
| Upstream | Real LLM API |

---

## Flow 1 — Happy path (streaming)

```mermaid
sequenceDiagram
  participant C as Client
  participant H as Handler
  participant R as Router
  participant A as Adapter
  participant U as Upstream

  C->>H: POST /v1/chat/completions (stream=true)
  H->>H: Validate unified request
  H->>R: Handle(request, clientAbort)
  R->>R: model or default assistant-default
  R->>R: Resolve → [target1, target2, ...] or 404 unknown
  R->>A: stream(target1, signal)
  A->>U: Provider-native streaming HTTP
  U-->>A: headers OK + first event
  A-->>R: unified SSE frame
  R-->>H: frame
  H-->>C: data: chunk
  loop Until complete
    U-->>A: next event
    A-->>R: unified frame
    R-->>H: frame
    H-->>C: data: chunk
  end
  H-->>C: data: [DONE]
```

**Memory note:** each loop iteration carries one frame; the gateway does not assemble the full assistant text for streaming.

---

## Flow 2 — Pre-stream failover (silent)

Primary fails before any chunk reaches the client; secondary serves the stream.

```mermaid
sequenceDiagram
  participant C as Client
  participant H as Handler
  participant R as Router
  participant A1 as Adapter_primary
  participant U1 as Upstream_primary
  participant A2 as Adapter_secondary
  participant U2 as Upstream_secondary

  C->>H: POST stream=true
  H->>R: Handle(...)
  R->>A1: stream(target1)
  A1->>U1: open
  U1-->>A1: 503 / timeout / connect fail
  A1-->>R: failover-eligible error
  Note over R,C: No SSE bytes sent to client yet
  R->>A2: stream(target2)
  A2->>U2: open
  U2-->>A2: OK + events
  A2-->>R: unified frames
  R-->>H: pipe
  H-->>C: SSE stream (looks like a normal success)
```

---

## Flow 3 — Mid-stream hard failure

Tokens already started; upstream dies. No switch to another provider.

```mermaid
sequenceDiagram
  participant C as Client
  participant H as Handler
  participant R as Router
  participant A as Adapter
  participant U as Upstream

  C->>H: POST stream=true
  H->>R: Handle(...)
  R->>A: stream(target1)
  A->>U: open
  U-->>A: events
  A-->>R: frames
  H-->>C: data: chunk (stream locked)
  U-->>A: error / drop
  A-->>R: mid-stream failure
  R-->>H: hard fail (no next target)
  H-->>C: error / close stream
  Note over R: Secondary never called
```

---

## Flow 4 — Client disconnect mid-generation

```mermaid
sequenceDiagram
  participant C as Client
  participant H as Handler
  participant R as Router
  participant A as Adapter
  participant U as Upstream

  C->>H: POST stream=true
  H->>R: Handle(..., clientAbort)
  R->>A: stream(..., signal)
  A->>U: open + stream
  H-->>C: data: chunk
  C--xH: connection closed / abort
  H->>R: abort signal
  R->>A: cancel
  A->>U: abort upstream HTTP
  Note over R: No failover — request ends as client_disconnected
```

---

## Flow 5 — Non-stream completion

```mermaid
sequenceDiagram
  participant C as Client
  participant H as Handler
  participant R as Router
  participant A as Adapter
  participant U as Upstream

  C->>H: POST (stream=false)
  H->>R: Handle(...)
  R->>A: complete(target1)
  A->>U: provider request
  U-->>A: full JSON body
  A-->>R: unified completion
  R-->>H: result
  H-->>C: application/json completion
```

Pre-stream failover rules still apply if `complete()` fails before a successful body is accepted; there is no partial SSE lock, but once a final JSON success is chosen, the client gets that single response.

---

## Flow 6 — Attempt loop (control view)

```mermaid
flowchart TD
  recv[Receive validated request] --> resolve[Resolve ordered targets]
  resolve --> attempt[Attempt next target]
  attempt --> opened{Any bytes sent to client?}
  opened -->|Error before open| eligible{Failover eligible?}
  eligible -->|Yes and targets left| attempt
  eligible -->|No or exhausted| err[Error response]
  opened -->|Open and piping| pipe[Pipe frames]
  pipe --> endOK{Clean end?}
  endOK -->|Yes| done[Complete]
  endOK -->|Upstream fail or idle timeout| hard[Hard fail - no failover]
  opened -->|Client abort| cancel[Cancel upstream - stop]
```

---

## Next

→ [Class diagrams](class-diagrams.md)

Deeper policy detail: [routing-and-failover.md](routing-and-failover.md) · [streaming.md](streaming.md)
