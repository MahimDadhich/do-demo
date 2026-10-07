# Class diagrams (conceptual)

Structural view after [high-level](high-level.md) and [request flows](request-flows.md). These are **design-time** shapes — names may shift slightly when we pick a stack, but the boundaries should not.

## Core types

```mermaid
classDiagram
  direction TB

  class ChatCompletionsHandler {
    +handle(req, res)
    -bindClientAbort(req)
  }

  class Router {
    +handle(request, abortSignal) AsyncIterable~Frame~|Completion
    -resolveTargets(model) Target[]
    -runAttempts(targets, request, signal)
  }

  class RouteConfig {
    +getTargets(logicalModel) Target[]
  }

  class Target {
    +provider: string
    +upstreamModel: string
    +credentialsRef: string
  }

  class AdapterRegistry {
    +get(provider) ProviderAdapter
  }

  class ProviderAdapter {
    <<interface>>
    +stream(ctx, request, target, signal) AsyncIterable~UnifiedSseFrame~
    +complete(ctx, request, target, signal) UnifiedCompletion
  }

  class AnthropicAdapter {
    +stream(...)
    +complete(...)
  }

  class OpenAIAdapter {
    +stream(...)
    +complete(...)
  }

  class TimeoutPolicy {
    +connectTimeout
    +ttfbTimeout
    +interChunkIdleTimeout
    +attemptOverallTimeout
    +requestOverallTimeout
  }

  class GatewayError {
    +class: ErrorClass
    +failoverEligible: bool
    +message: string
  }

  class UnifiedChatRequest {
    +model: string?
    +messages: Message[]
    +stream: bool
    +...
  }
  note for UnifiedChatRequest "model optional; default assistant-default"

  class UnifiedSseFrame {
    +payload: string
  }

  ChatCompletionsHandler --> Router : delegates
  ChatCompletionsHandler --> UnifiedChatRequest : validates
  Router --> RouteConfig : resolves
  Router --> AdapterRegistry : looks up
  Router --> TimeoutPolicy : applies
  Router --> Target : attempts
  AdapterRegistry --> ProviderAdapter : returns
  ProviderAdapter <|.. AnthropicAdapter
  ProviderAdapter <|.. OpenAIAdapter
  ProviderAdapter --> GatewayError : throws
  ProviderAdapter --> UnifiedSseFrame : yields
  Router --> GatewayError : classifies
```

## Responsibility boundaries

| Type | May know | Must not know |
|---|---|---|
| `ChatCompletionsHandler` | HTTP, SSE headers, validation | Provider URLs, failover ordering details beyond calling Router |
| `Router` | Targets, attempt loop, failover eligibility, timeouts | Anthropic/OpenAI event names or auth headers |
| `ProviderAdapter` | One provider’s wire protocol | Other providers; global route table |
| `RouteConfig` | Model → target list | How to call HTTP |
| `TimeoutPolicy` | Duration budgets | Provider-specific APIs |

## Error classification (shared vocabulary)

```mermaid
classDiagram
  class ErrorClass {
    <<enumeration>>
    client_error
    rate_limited
    upstream_unavailable
    timeout
    upstream_fatal
  }

  class GatewayError {
    +class: ErrorClass
    +failoverEligible: bool
  }

  GatewayError --> ErrorClass
```

Router uses `failoverEligible` **and** “has the client stream locked?” to decide the next action (see flows).

## Suggested package sketch (when code exists)

```text
gateway/
  http/          ChatCompletionsHandler
  router/        Router, TimeoutPolicy, GatewayError
  config/        RouteConfig, Target
  adapters/      ProviderAdapter, AdapterRegistry
    anthropic/
    openai/
  types/         UnifiedChatRequest, UnifiedSseFrame, ...
```

## Next / deeper

- Adapter method contracts: [adapters.md](adapters.md)
- Failover matrix: [routing-and-failover.md](routing-and-failover.md)
- Test doubles against these boundaries: [testing.md](testing.md)
