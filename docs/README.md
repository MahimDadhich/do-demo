# Documentation

Design artifacts for the unified LLM gateway. Keep product decisions here before (and alongside) code.

## Index

1. [Problem statement & functional requirements](problem-and-functional-requirements.md) — what we are building and the MVP behavior rules.
2. [Non-functional requirements](non-functional-requirements.md) — streaming memory, adapter separation, disconnects/timeouts, testing (more topics deferred).
3. [Architecture](architecture/README.md) — request path, stream pipe, adapters, routing/failover, tests.

## Conventions

- Prefer short, dated decision notes over sprawling wikis.
- Locked product rules belong in the FR doc until an ADR says otherwise.
- New architecture write-ups go under `architecture/` and are linked from that folder’s README.
