# Streaming & memory architecture

## Goal

Proxy live generation to the client with **frame-sized** memory, not completion-sized buffers.

## Pipe model

```text
Upstream bytes/events
        │
        ▼
 Adapter decode (one frame)
        │
        ▼
 Unified SSE encode (one frame)
        │
        ▼
 HTTP response write (flush)
        │
        ▼
 Client
```

Rules:

1. Read upstream → map → write client → repeat.
2. Do **not** `join` all text deltas into a gateway-owned string for streaming responses.
3. Hold at most: read buffer, current frame, and any minimal parser state (e.g. incomplete SSE line).
4. On client write backpressure, stop pulling upstream until the write drains (or abort if the client is gone).

## SSE shape (client)

Unified stream follows OpenAI-style chat-completion chunks:

- `Content-Type: text/event-stream`
- Lines: `data: {json}\n\n`
- Terminal: `data: [DONE]\n\n` on clean completion

Adapters that speak a different upstream protocol (e.g. Anthropic SSE events) convert **each** upstream event into zero or more unified `data:` frames, then discard the upstream event.

## Abort & partial streams

| Event | Gateway action |
|---|---|
| Client disconnect / abort | Abort upstream; stop piping; log `client_disconnected`; no failover |
| Upstream error after first client frame | Stop piping; signal hard failure to client; cancel upstream; no failover |
| Idle gap > inter-chunk timeout | Same as upstream mid-stream failure |

Partial bytes already flushed to the client are never retracted.

## Non-stream path

For `stream: false`, the adapter may collect one upstream JSON body, map to unified completion JSON, and return once. Memory is O(response body) for that mode only.

## Test hooks

Integration tests should assert piping by:

- Counting client writes / frames as upstream emits (not a single bulk write of the full text).
- Failing the test if the handler materializes a full concatenated assistant string for `stream: true` (lint or explicit API discipline on the stream type: async iterable of frames only).
