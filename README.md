# Unified LLM Gateway

OpenRouter-style API gateway: one `POST /v1/chat/completions` contract, runtime routing to upstream LLM providers, SSE streaming proxy, and pre-stream silent failover.

This repository is in the **design phase**. Product and architecture documents live under [`docs/`](docs/). Implementation follows once non-functional requirements are agreed.

## Documentation

| Document | Status |
|---|---|
| [Problem statement & functional requirements](docs/problem-and-functional-requirements.md) | Agreed |
| [Non-functional requirements](docs/non-functional-requirements.md) | Placeholder — next |
| [Architecture](docs/architecture/README.md) | Placeholder — next |

## Next steps

1. Capture non-functional requirements (timeouts, fallback depth, observability, config/secrets, stack).
2. Write architecture documents under `docs/architecture/`.
3. Scaffold and implement the gateway against those docs.
