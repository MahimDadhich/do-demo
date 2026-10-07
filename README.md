# Unified LLM Gateway

OpenRouter-style API gateway in **Java (Spring WebFlux)**: one `POST /v1/chat/completions` contract, runtime routing to upstream LLM providers, SSE streaming proxy, and pre-stream silent failover.

## Run locally

```bash
# Optional provider keys (without them, upstream calls fail and failover may engage)
export OPENAI_API_KEY=...
export ANTHROPIC_API_KEY=...

./mvnw spring-boot:run
# or, if Maven is installed:
mvn spring-boot:run
```

Server listens on **[http://127.0.0.1:43127](http://127.0.0.1:43127)**.

Example:

```bash
curl -sN http://127.0.0.1:43127/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{
    "messages": [{"role":"user","content":"Hello"}],
    "stream": true
  }'
```

- Omit `model` (or leave it blank) to use logical model **`assistant-default`** (Anthropic → OpenAI).
- Unknown logical models return **404** `model_not_found`.

## Tests

```bash
mvn test
```

## Documentation

| Document | Status |
|---|---|
| [Problem statement & functional requirements](docs/problem-and-functional-requirements.md) | Agreed |
| [Non-functional requirements](docs/non-functional-requirements.md) | First slice drafted |
| [Architecture](docs/architecture/README.md) | Layered: high-level → flows → classes |

**Architecture reading order:** [10,000 ft](docs/architecture/high-level.md) → [flows](docs/architecture/request-flows.md) → [classes](docs/architecture/class-diagrams.md)

## Layout

```text
src/main/java/com/digitalocean/llmgateway/
  http/        Chat completions entrypoint + error handler
  router/      Ordered-target failover
  adapters/    OpenAI + Anthropic providers
  config/      Route table + timeouts + WebClient
  types/       Unified request/SSE DTOs
```
