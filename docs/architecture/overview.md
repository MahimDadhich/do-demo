# Architecture documentation plan

We document architecture in **layers**, coarse → fine. Read in order; do not start in the weeds.

## Reading order

| Step | Doc | Altitude |
|---|---|---|
| 1 | [High-level architecture (10,000 ft)](high-level.md) | Big boxes and principles only |
| 2 | [Request flows & sequences](request-flows.md) | End-to-end sequence/flow diagrams |
| 3 | [Class diagrams](class-diagrams.md) | Conceptual types and boundaries |

## Deeper references (after the three layers)

| Doc | When you need it |
|---|---|
| [streaming.md](streaming.md) | Memory/pipe rules |
| [adapters.md](adapters.md) | Provider interface details |
| [routing-and-failover.md](routing-and-failover.md) | Eligibility matrix |
| [testing.md](testing.md) | Unit/integration mapping |

## Product context

- [Functional requirements](../problem-and-functional-requirements.md)
- [Non-functional requirements](../non-functional-requirements.md)
