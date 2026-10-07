# Architecture

Design docs for the unified LLM gateway. Start with the overview, then dive into streaming, adapters, routing, and testing.

| Doc | Intent |
|---|---|
| [overview.md](overview.md) | Request path, components, sequence/flow diagrams |
| [streaming.md](streaming.md) | SSE pipe model, memory bounds, abort/partial streams |
| [adapters.md](adapters.md) | Provider adapter interface and responsibility split |
| [routing-and-failover.md](routing-and-failover.md) | Route table, attempt loop, eligibility matrix |
| [testing.md](testing.md) | Unit + integration strategy aligned to NFRs |

Product rules: [Functional requirements](../problem-and-functional-requirements.md) · [NFRs](../non-functional-requirements.md)
