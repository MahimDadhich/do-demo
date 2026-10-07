package com.digitalocean.llmgateway.adapters;

import com.digitalocean.llmgateway.config.GatewayProperties;
import com.digitalocean.llmgateway.types.UnifiedChatRequest;
import com.digitalocean.llmgateway.types.UnifiedSseFrame;
import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ProviderAdapter {

  String id();

  Flux<UnifiedSseFrame> stream(
      UnifiedChatRequest request, GatewayProperties.Target target, AttemptContext context);

  Mono<JsonNode> complete(
      UnifiedChatRequest request, GatewayProperties.Target target, AttemptContext context);
}
