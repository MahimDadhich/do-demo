package com.digitalocean.llmgateway.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.digitalocean.llmgateway.adapters.AdapterRegistry;
import com.digitalocean.llmgateway.adapters.AttemptContext;
import com.digitalocean.llmgateway.adapters.ProviderAdapter;
import com.digitalocean.llmgateway.config.GatewayProperties;
import com.digitalocean.llmgateway.config.RouteConfig;
import com.digitalocean.llmgateway.types.ChatMessage;
import com.digitalocean.llmgateway.types.UnifiedChatRequest;
import com.digitalocean.llmgateway.types.UnifiedSseFrame;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class RouterTest {

  private GatewayProperties properties;
  private RouteConfig routeConfig;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    properties = new GatewayProperties();
    properties.setDefaultModel("assistant-default");
    properties.setMaxTargets(3);

    GatewayProperties.Target primary = target("primary", "model-a");
    GatewayProperties.Target secondary = target("secondary", "model-b");
    GatewayProperties.ModelRoute route = new GatewayProperties.ModelRoute();
    route.setTargets(List.of(primary, secondary));
    properties.getModels().put("assistant-default", route);
    properties.getModels().put("known-model", route);

    routeConfig = new RouteConfig(properties);
  }

  @Test
  void unknownModelReturns404WithoutCallingAdapters() {
    AtomicInteger calls = new AtomicInteger();
    Router router =
        routerWith(
            fake("primary", (req, t, ctx) -> {
              calls.incrementAndGet();
              return Flux.just(new UnifiedSseFrame("{\"ok\":true}"));
            }),
            fake("secondary", (req, t, ctx) -> {
              calls.incrementAndGet();
              return Flux.just(new UnifiedSseFrame("{\"ok\":true}"));
            }));

    assertThatThrownBy(() -> router.stream(request("missing-model", true), Mono.never()).blockFirst())
        .isInstanceOf(GatewayError.class)
        .satisfies(
            error -> {
              GatewayError ge = (GatewayError) error;
              assertThat(ge.getHttpStatus()).isEqualTo(404);
              assertThat(ge.getMessage()).contains("model_not_found");
            });
    assertThat(calls.get()).isZero();
  }

  @Test
  void blankModelUsesAssistantDefault() {
    AtomicInteger primaryCalls = new AtomicInteger();
    Router router =
        routerWith(
            fake(
                "primary",
                (req, t, ctx) -> {
                  primaryCalls.incrementAndGet();
                  return Flux.just(new UnifiedSseFrame("{\"delta\":\"hi\"}"), UnifiedSseFrame.done());
                }),
            fake("secondary", (req, t, ctx) -> Flux.error(new IllegalStateException("should not"))));

    StepVerifier.create(router.stream(request(null, true), Mono.never()).map(UnifiedSseFrame::payload))
        .expectNext("{\"delta\":\"hi\"}")
        .expectNext("[DONE]")
        .verifyComplete();
    assertThat(primaryCalls.get()).isEqualTo(1);
  }

  @Test
  void preStreamFailoverUsesSecondarySilently() {
    AtomicInteger secondaryCalls = new AtomicInteger();
    Router router =
        routerWith(
            fake(
                "primary",
                (req, t, ctx) -> Flux.error(GatewayError.unavailable(503, "primary down"))),
            fake(
                "secondary",
                (req, t, ctx) -> {
                  secondaryCalls.incrementAndGet();
                  return Flux.just(new UnifiedSseFrame("{\"from\":\"secondary\"}"), UnifiedSseFrame.done());
                }));

    StepVerifier.create(router.stream(request("assistant-default", true), Mono.never()).map(UnifiedSseFrame::payload))
        .expectNext("{\"from\":\"secondary\"}")
        .expectNext("[DONE]")
        .verifyComplete();
    assertThat(secondaryCalls.get()).isEqualTo(1);
  }

  @Test
  void midStreamFailureDoesNotCallSecondary() {
    AtomicInteger secondaryCalls = new AtomicInteger();
    Router router =
        routerWith(
            fake(
                "primary",
                (req, t, ctx) ->
                    Flux.concat(
                        Flux.just(new UnifiedSseFrame("{\"partial\":true}")),
                        Flux.error(GatewayError.fatal("upstream dropped")))),
            fake(
                "secondary",
                (req, t, ctx) -> {
                  secondaryCalls.incrementAndGet();
                  return Flux.just(new UnifiedSseFrame("{\"should\":\"not\"}"));
                }));

    StepVerifier.create(router.stream(request("assistant-default", true), Mono.never()))
        .expectNext(new UnifiedSseFrame("{\"partial\":true}"))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(GatewayError.class);
              assertThat(((GatewayError) error).getMessage()).contains("upstream dropped");
            })
        .verify();
    assertThat(secondaryCalls.get()).isZero();
  }

  @Test
  void clientAbortStopsWithoutFailover() {
    AtomicInteger secondaryCalls = new AtomicInteger();
    Sinks.One<Void> abort = Sinks.one();

    Router router =
        routerWith(
            fake(
                "primary",
                (req, t, ctx) ->
                    Flux.concat(
                        Flux.just(new UnifiedSseFrame("{\"partial\":true}")),
                        Flux.never())),
            fake(
                "secondary",
                (req, t, ctx) -> {
                  secondaryCalls.incrementAndGet();
                  return Flux.just(new UnifiedSseFrame("{\"nope\":true}"));
                }));

    StepVerifier.create(router.stream(request("assistant-default", true), abort.asMono()))
        .expectNext(new UnifiedSseFrame("{\"partial\":true}"))
        .then(() -> abort.tryEmitEmpty())
        .verifyComplete();

    assertThat(secondaryCalls.get()).isZero();
  }

  @Test
  void preStreamClientErrorDoesNotFailover() {
    AtomicInteger secondaryCalls = new AtomicInteger();
    Router router =
        routerWith(
            fake(
                "primary",
                (req, t, ctx) -> Flux.error(GatewayError.client(400, "bad request"))),
            fake(
                "secondary",
                (req, t, ctx) -> {
                  secondaryCalls.incrementAndGet();
                  return Flux.just(new UnifiedSseFrame("{\"no\":true}"));
                }));

    StepVerifier.create(router.stream(request("assistant-default", true), Mono.never()))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(GatewayError.class);
              assertThat(((GatewayError) error).getHttpStatus()).isEqualTo(400);
              assertThat(((GatewayError) error).isFailoverEligible()).isFalse();
            })
        .verify();
    assertThat(secondaryCalls.get()).isZero();
  }

  @Test
  void failoverPrefersCheaperSecondaryBeforeExpensive() {
    GatewayProperties.Target primary = target("primary", "model-a", 15);
    GatewayProperties.Target expensive = target("expensive", "model-x", 20);
    GatewayProperties.Target cheap = target("cheap", "model-y", 2);
    GatewayProperties.ModelRoute route = new GatewayProperties.ModelRoute();
    // Expensive listed before cheap in config; cost sort should try cheap first after primary.
    route.setTargets(List.of(primary, expensive, cheap));
    properties.getModels().put("assistant-default", route);
    routeConfig = new RouteConfig(properties);

    List<String> attemptOrder = new ArrayList<>();
    Router router =
        routerWith(
            fake(
                "primary",
                (req, t, ctx) -> {
                  attemptOrder.add("primary");
                  return Flux.error(GatewayError.unavailable(503, "primary down"));
                }),
            fake(
                "expensive",
                (req, t, ctx) -> {
                  attemptOrder.add("expensive");
                  return Flux.just(new UnifiedSseFrame("{\"from\":\"expensive\"}"), UnifiedSseFrame.done());
                }),
            fake(
                "cheap",
                (req, t, ctx) -> {
                  attemptOrder.add("cheap");
                  return Flux.just(new UnifiedSseFrame("{\"from\":\"cheap\"}"), UnifiedSseFrame.done());
                }));

    StepVerifier.create(router.stream(request("assistant-default", true), Mono.never()).map(UnifiedSseFrame::payload))
        .expectNext("{\"from\":\"cheap\"}")
        .expectNext("[DONE]")
        .verifyComplete();
    assertThat(attemptOrder).containsExactly("primary", "cheap");
  }

  @Test
  void completeFailoversBeforeSuccess() {
    ObjectMapper mapper = objectMapper;
    Router router =
        routerWith(
            completeFake(
                "primary",
                (req, t, ctx) -> Mono.error(GatewayError.rateLimited("slow down"))),
            completeFake(
                "secondary",
                (req, t, ctx) ->
                    Mono.just(mapper.createObjectNode().put("id", "ok").put("object", "chat.completion"))));

    StepVerifier.create(router.complete(request("assistant-default", false), Mono.never()))
        .assertNext(node -> assertThat(node.path("id").asText()).isEqualTo("ok"))
        .verifyComplete();
  }

  private Router routerWith(ProviderAdapter... adapters) {
    return new Router(routeConfig, new AdapterRegistry(List.of(adapters)));
  }

  private static GatewayProperties.Target target(String provider, String model) {
    return target(provider, model, 0);
  }

  private static GatewayProperties.Target target(String provider, String model, double cost) {
    GatewayProperties.Target target = new GatewayProperties.Target();
    target.setProvider(provider);
    target.setUpstreamModel(model);
    target.setCredentialsRef(provider + "_default");
    target.setCost(cost);
    return target;
  }

  private UnifiedChatRequest request(String model, boolean stream) {
    return new UnifiedChatRequest(
        model,
        List.of(new ChatMessage("user", TextNode.valueOf("hello"))),
        stream,
        null,
        null,
        null);
  }

  private static ProviderAdapter fake(String id, StreamFn streamFn) {
    return new ProviderAdapter() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public Flux<UnifiedSseFrame> stream(
          UnifiedChatRequest request, GatewayProperties.Target target, AttemptContext context) {
        return streamFn.apply(request, target, context);
      }

      @Override
      public Mono<JsonNode> complete(
          UnifiedChatRequest request, GatewayProperties.Target target, AttemptContext context) {
        return Mono.error(new UnsupportedOperationException("not used"));
      }
    };
  }

  private static ProviderAdapter completeFake(String id, CompleteFn completeFn) {
    return new ProviderAdapter() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public Flux<UnifiedSseFrame> stream(
          UnifiedChatRequest request, GatewayProperties.Target target, AttemptContext context) {
        return Flux.error(new UnsupportedOperationException("not used"));
      }

      @Override
      public Mono<JsonNode> complete(
          UnifiedChatRequest request, GatewayProperties.Target target, AttemptContext context) {
        return completeFn.apply(request, target, context);
      }
    };
  }

  @FunctionalInterface
  private interface StreamFn {
    Flux<UnifiedSseFrame> apply(
        UnifiedChatRequest request, GatewayProperties.Target target, AttemptContext context);
  }

  @FunctionalInterface
  private interface CompleteFn {
    Mono<JsonNode> apply(
        UnifiedChatRequest request, GatewayProperties.Target target, AttemptContext context);
  }
}
