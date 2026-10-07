# Unified LLM Gateway

OpenRouter-style API gateway: one `POST /v1/chat/completions` contract, runtime routing to upstream LLM providers, SSE streaming proxy, and pre-stream silent failover.

This repository is in the **design phase**. Product and architecture documents live under [`docs/`](docs/).

## Documentation

| Document | Status |
|---|---|
| [Problem statement & functional requirements](docs/problem-and-functional-requirements.md) | Agreed |
| [Non-functional requirements](docs/non-functional-requirements.md) | First slice drafted |
| [Architecture](docs/architecture/README.md) | Layered: high-level → flows → classes |

**Architecture reading order:** [10,000 ft](docs/architecture/high-level.md) → [flows](docs/architecture/request-flows.md) → [classes](docs/architecture/class-diagrams.md)

## Next steps

1. Review the layered architecture docs (high-level, flows, classes).
2. Fill deferred NFR topics (SLOs, observability catalog, stack/deploy, auth).
3. Scaffold and implement the gateway against these docs.
