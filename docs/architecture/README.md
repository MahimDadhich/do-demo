# Architecture

Documentation is layered on purpose:

1. **[High-level (10,000 ft)](high-level.md)** — what the system is; big boxes; principles  
2. **[Request flows & sequences](request-flows.md)** — happy path, failover, mid-stream fail, disconnect  
3. **[Class diagrams](class-diagrams.md)** — conceptual types and package sketch  

Start with those three. Use [overview.md](overview.md) as the short plan/index.

### Deeper references

| Doc | Intent |
|---|---|
| [streaming.md](streaming.md) | SSE pipe model, memory bounds |
| [adapters.md](adapters.md) | Adapter interface and error classes |
| [routing-and-failover.md](routing-and-failover.md) | Route table and eligibility matrix |
| [testing.md](testing.md) | Unit + integration strategy |

Product rules: [FRs](../problem-and-functional-requirements.md) · [NFRs](../non-functional-requirements.md)
