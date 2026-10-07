package com.digitalocean.llmgateway.router;

import com.digitalocean.llmgateway.adapters.AdapterRegistry;
import com.digitalocean.llmgateway.adapters.AttemptContext;
import com.digitalocean.llmgateway.adapters.ProviderAdapter;
import com.digitalocean.llmgateway.config.GatewayProperties;
import com.digitalocean.llmgateway.config.RouteConfig;
import com.digitalocean.llmgateway.types.UnifiedChatRequest;
import com.digitalocean.llmgateway.types.UnifiedSseFrame;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class Router {

  private static final Logger log = LoggerFactory.getLogger(Router.class);

  private final RouteConfig routeConfig;
  private final AdapterRegistry adapterRegistry;

  public Router(RouteConfig routeConfig, AdapterRegistry adapterRegistry) {
    this.routeConfig = routeConfig;
    this.adapterRegistry = adapterRegistry;
  }

  /**
   * Streams unified SSE frames for the request, applying pre-stream failover. Once the first frame
   * is emitted, the provider is locked — mid-stream failures and client aborts do not failover.
   */
  public Flux<UnifiedSseFrame> stream(UnifiedChatRequest request, Mono<Void> clientAbort) {
    String requestId = UUID.randomUUID().toString();
    String logicalModel = routeConfig.resolveLogicalModel(request.model());
    List<GatewayProperties.Target> targets = routeConfig.getTargets(logicalModel);

    AtomicBoolean locked = new AtomicBoolean(false);
    AtomicReference<GatewayError> lastError = new AtomicReference<>();

    return Flux.defer(
            () ->
                attemptStream(
                    request, targets, 0, requestId, locked, lastError, clientAbort))
        .takeUntilOther(clientAbort.then(Mono.just(true)))
        .doOnCancel(
            () ->
                log.info(
                    "client_disconnected requestId={} locked={}", requestId, locked.get()));
  }

  /** Non-streaming completion with the same pre-stream failover rules. */
  public Mono<JsonNode> complete(UnifiedChatRequest request, Mono<Void> clientAbort) {
    String requestId = UUID.randomUUID().toString();
    String logicalModel = routeConfig.resolveLogicalModel(request.model());
    List<GatewayProperties.Target> targets = routeConfig.getTargets(logicalModel);

    return attemptComplete(request, targets, 0, requestId)
        .takeUntilOther(clientAbort.thenReturn(true))
        .switchIfEmpty(
            Mono.error(GatewayError.client(499, "client_disconnected before completion")));
  }

  private Flux<UnifiedSseFrame> attemptStream(
      UnifiedChatRequest request,
      List<GatewayProperties.Target> targets,
      int index,
      String requestId,
      AtomicBoolean locked,
      AtomicReference<GatewayError> lastError,
      Mono<Void> clientAbort) {
    if (index >= targets.size()) {
      GatewayError error = lastError.get();
      if (error != null) {
        return Flux.error(error);
      }
      return Flux.error(GatewayError.unavailable(502, "all upstream targets failed"));
    }

    GatewayProperties.Target target = targets.get(index);
    ProviderAdapter adapter = adapterRegistry.get(target.getProvider());
    AttemptContext context = AttemptContext.of(requestId, index, routeConfig.timeouts());

    log.info(
        "attempt_start requestId={} targetIndex={} provider={} upstreamModel={}",
        requestId,
        index,
        target.getProvider(),
        target.getUpstreamModel());

    AtomicBoolean sawFrame = new AtomicBoolean(false);

    Flux<UnifiedSseFrame> upstream =
        adapter
            .stream(request, target, context)
            .doOnNext(
                frame -> {
                  if (sawFrame.compareAndSet(false, true)) {
                    locked.set(true);
                    log.info(
                        "stream_locked requestId={} targetIndex={} provider={}",
                        requestId,
                        index,
                        target.getProvider());
                  }
                })
            .takeUntilOther(clientAbort.then(Mono.just(true)));

    return upstream.onErrorResume(
        error -> {
          GatewayError gatewayError = toGatewayError(error);
          lastError.set(gatewayError);

          if (sawFrame.get() || locked.get()) {
            log.warn(
                "mid_stream_hard_fail requestId={} targetIndex={} provider={} reason={}",
                requestId,
                index,
                target.getProvider(),
                gatewayError.getMessage());
            return Flux.error(gatewayError);
          }

          if (gatewayError.isFailoverEligible() && index + 1 < targets.size()) {
            log.warn(
                "pre_stream_failover requestId={} fromIndex={} provider={} reason={}",
                requestId,
                index,
                target.getProvider(),
                gatewayError.getMessage());
            return attemptStream(
                request, targets, index + 1, requestId, locked, lastError, clientAbort);
          }

          log.warn(
              "attempt_failed_no_failover requestId={} targetIndex={} provider={} reason={}",
              requestId,
              index,
              target.getProvider(),
              gatewayError.getMessage());
          return Flux.error(gatewayError);
        });
  }

  private Mono<JsonNode> attemptComplete(
      UnifiedChatRequest request,
      List<GatewayProperties.Target> targets,
      int index,
      String requestId) {
    if (index >= targets.size()) {
      return Mono.error(GatewayError.unavailable(502, "all upstream targets failed"));
    }

    GatewayProperties.Target target = targets.get(index);
    ProviderAdapter adapter = adapterRegistry.get(target.getProvider());
    AttemptContext context = AttemptContext.of(requestId, index, routeConfig.timeouts());

    log.info(
        "complete_attempt requestId={} targetIndex={} provider={} upstreamModel={}",
        requestId,
        index,
        target.getProvider(),
        target.getUpstreamModel());

    return adapter
        .complete(request, target, context)
        .onErrorResume(
            error -> {
              GatewayError gatewayError = toGatewayError(error);
              if (gatewayError.isFailoverEligible() && index + 1 < targets.size()) {
                log.warn(
                    "pre_stream_failover_complete requestId={} fromIndex={} provider={} reason={}",
                    requestId,
                    index,
                    target.getProvider(),
                    gatewayError.getMessage());
                return attemptComplete(request, targets, index + 1, requestId);
              }
              return Mono.error(gatewayError);
            });
  }

  private static GatewayError toGatewayError(Throwable error) {
    if (error instanceof GatewayError gatewayError) {
      return gatewayError;
    }
    Throwable cause = error.getCause();
    if (cause instanceof GatewayError gatewayError) {
      return gatewayError;
    }
    return GatewayError.unavailable(502, "upstream error: " + error.getMessage());
  }
}
