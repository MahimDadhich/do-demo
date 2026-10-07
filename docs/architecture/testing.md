# Testing architecture

Maps NFR §4 to concrete test layers. No live provider keys required for CI.

## Unit tests

| Module | Doubles | Examples |
|---|---|---|
| Router | Fake adapters with scripted outcomes | Primary 503 → secondary streams; mid-stream fail does not call secondary; disconnect does not failover |
| Error classifier | Raw status + exceptions | 429 → rate_limited/failover; 400 → client_error |
| Anthropic adapter | Fixture SSE payloads | Event → unified chunk mapping |
| OpenAI adapter | Fixture SSE payloads | Passthrough / small remap |
| Timeout policy | Fake clocks | TTFB expiry vs idle expiry actions |

## Integration tests

Spin a **mock upstream HTTP server** that can:

- Stream SSE with controllable delays
- Return 429/5xx before body
- Hang before headers (TTFB)
- Pause between chunks (idle timeout)
- Observe whether the incoming request was aborted

Gateway under test points route config at that mock (as Anthropic-shaped and/or OpenAI-shaped endpoints).

### Critical scenarios

1. **Pipe integrity** — chunks appear on the client as mock emits them.
2. **Pre-stream failover** — primary fails, secondary serves; one client stream.
3. **Mid-stream hard fail** — secondary never contacted after lock.
4. **Client abort** — mock sees connection abort / incomplete request; no fallback attempt.
5. **Timeouts** — connect/TTFB → failover; idle → hard fail.

## Naming / layout (when code exists)

Suggested (adjust to chosen stack later):

```text
src/
  router/
  adapters/
  http/
tests/
  unit/
  integration/
```

Keep router tests free of real provider codecs to enforce the adapter boundary in CI.
