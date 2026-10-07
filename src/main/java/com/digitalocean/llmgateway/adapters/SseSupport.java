package com.digitalocean.llmgateway.adapters;

import com.digitalocean.llmgateway.types.UnifiedSseFrame;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Flux;

final class SseSupport {

  private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_STRING =
      new ParameterizedTypeReference<>() {};

  private SseSupport() {}

  /** Read upstream SSE as raw string payloads (supports literal [DONE]). */
  static Flux<String> dataLines(ClientResponse response) {
    return response
        .bodyToFlux(SSE_STRING)
        .mapNotNull(ServerSentEvent::data)
        .filter(data -> data != null && !data.isBlank());
  }

  static Flux<UnifiedSseFrame> openAiStyleFrames(ClientResponse response) {
    return dataLines(response)
        .takeUntil("[DONE]"::equals)
        .filter(data -> !"[DONE]".equals(data))
        .map(UnifiedSseFrame::new)
        .concatWithValues(UnifiedSseFrame.done());
  }
}
