package com.digitalocean.llmgateway.http;

import com.digitalocean.llmgateway.router.GatewayError;
import com.digitalocean.llmgateway.router.Router;
import com.digitalocean.llmgateway.types.UnifiedChatRequest;
import com.digitalocean.llmgateway.types.UnifiedSseFrame;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/v1/chat/completions")
public class ChatCompletionsController {

  private final Router router;

  public ChatCompletionsController(Router router) {
    this.router = router;
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
  public Mono<ResponseEntity<Object>> chatCompletions(
      @Valid @RequestBody UnifiedChatRequest request, ServerWebExchange exchange) {
    // Client disconnect cancels the response publisher; adapters honor that via Flux/Mono cancel.
    Mono<Void> clientAbort = Mono.never();

    if (request.streaming()) {
      @SuppressWarnings("unchecked")
      ResponseEntity<Object> body =
          (ResponseEntity<Object>) (ResponseEntity<?>) streamResponse(request, clientAbort, exchange.getResponse());
      return Mono.just(body);
    }

    return router
        .complete(request, clientAbort)
        .map(
            json ->
                ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body((Object) json))
        .onErrorResume(
            GatewayError.class,
            error -> Mono.just(errorEntity(error)));
  }

  private ResponseEntity<Flux<ServerSentEvent<String>>> streamResponse(
      UnifiedChatRequest request, Mono<Void> clientAbort, ServerHttpResponse response) {
    response.getHeaders().setContentType(MediaType.TEXT_EVENT_STREAM);
    response.getHeaders().set("Cache-Control", "no-cache");
    response.getHeaders().set("Connection", "keep-alive");

    Flux<ServerSentEvent<String>> events =
        router
            .stream(request, clientAbort)
            .map(this::toSse)
            .onErrorResume(
                error -> {
                  // If headers/body already started, best-effort emit an error frame then complete.
                  if (response.isCommitted()) {
                    String message =
                        error instanceof GatewayError ge
                            ? ge.getMessage()
                            : String.valueOf(error.getMessage());
                    return Flux.just(
                        ServerSentEvent.<String>builder()
                            .data(
                                "{\"error\":{\"message\":"
                                    + jsonString(message)
                                    + ",\"type\":\"gateway_error\"}}")
                            .build());
                  }
                  return Flux.error(error);
                });

    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .body(events);
  }

  private ServerSentEvent<String> toSse(UnifiedSseFrame frame) {
    return ServerSentEvent.<String>builder().data(frame.payload()).build();
  }

  private ResponseEntity<Object> errorEntity(GatewayError error) {
    int status = error.getHttpStatus() > 0 ? error.getHttpStatus() : 500;
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            GatewayExceptionHandler.errorBody(
                error.getErrorClass().name().toLowerCase(), error.getMessage()));
  }

  private static String jsonString(String value) {
    if (value == null) {
      return "\"\"";
    }
    return "\""
        + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        + "\"";
  }
}
