package com.digitalocean.llmgateway.adapters;

import com.digitalocean.llmgateway.config.GatewayProperties;
import com.digitalocean.llmgateway.config.ProviderProperties;
import com.digitalocean.llmgateway.router.GatewayError;
import com.digitalocean.llmgateway.types.UnifiedChatRequest;
import com.digitalocean.llmgateway.types.UnifiedSseFrame;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class OpenAiAdapter implements ProviderAdapter {

  private final WebClient.Builder webClientBuilder;
  private final ProviderProperties.OpenAi settings;
  private final ObjectMapper objectMapper;

  public OpenAiAdapter(
      WebClient.Builder webClientBuilder,
      ProviderProperties providerProperties,
      ObjectMapper objectMapper) {
    this.webClientBuilder = webClientBuilder;
    this.settings = providerProperties.getOpenai();
    this.objectMapper = objectMapper;
  }

  @Override
  public String id() {
    return "openai";
  }

  @Override
  public Flux<UnifiedSseFrame> stream(
      UnifiedChatRequest request, GatewayProperties.Target target, AttemptContext context) {
    requireApiKey();
    Map<String, Object> body = buildBody(request, target.getUpstreamModel(), true);

    return client(context)
        .post()
        .uri("/chat/completions")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .bodyValue(body)
        .exchangeToFlux(
            response ->
                UpstreamHttpSupport.ensureSuccess(response)
                    .flatMapMany(
                        ok ->
                            ok.bodyToFlux(ServerSentEvent.class)
                                .mapNotNull(this::sseData)
                                .timeout(context.interChunkIdleTimeout())
                                .map(this::toFrame)
                                .concatWithValues(UnifiedSseFrame.done())))
        .timeout(context.attemptOverallTimeout())
        .onErrorMap(UpstreamHttpSupport::mapTransportError);
  }

  @Override
  public Mono<JsonNode> complete(
      UnifiedChatRequest request, GatewayProperties.Target target, AttemptContext context) {
    requireApiKey();
    Map<String, Object> body = buildBody(request, target.getUpstreamModel(), false);

    return client(context)
        .post()
        .uri("/chat/completions")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchangeToMono(
            response ->
                UpstreamHttpSupport.ensureSuccess(response)
                    .flatMap(ok -> ok.bodyToMono(JsonNode.class)))
        .timeout(context.attemptOverallTimeout())
        .onErrorMap(UpstreamHttpSupport::mapTransportError);
  }

  private WebClient client(AttemptContext context) {
    return webClientBuilder
        .baseUrl(trimTrailingSlash(settings.getBaseUrl()))
        .defaultHeader("Authorization", "Bearer " + settings.getApiKey())
        .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
        .build()
        .mutate()
        .filter(
            (req, next) ->
                next.exchange(req).timeout(UpstreamHttpSupport.orDefault(context.ttfbTimeout(), Duration.ofSeconds(15))))
        .build();
  }

  private Map<String, Object> buildBody(
      UnifiedChatRequest request, String upstreamModel, boolean stream) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", upstreamModel);
    body.put("messages", toMessages(request));
    body.put("stream", stream);
    if (request.temperature() != null) {
      body.put("temperature", request.temperature());
    }
    if (request.maxTokens() != null) {
      body.put("max_tokens", request.maxTokens());
    }
    return body;
  }

  private List<Map<String, Object>> toMessages(UnifiedChatRequest request) {
    List<Map<String, Object>> messages = new ArrayList<>();
    request
        .messages()
        .forEach(
            message -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("role", message.role());
              row.put("content", contentAsOpenAi(message.content()));
              messages.add(row);
            });
    return messages;
  }

  private Object contentAsOpenAi(JsonNode content) {
    if (content == null || content.isNull()) {
      return "";
    }
    if (content.isTextual()) {
      return content.asText();
    }
    return objectMapper.convertValue(content, Object.class);
  }

  private String sseData(ServerSentEvent<?> event) {
    Object data = event.data();
    return data == null ? null : String.valueOf(data);
  }

  private UnifiedSseFrame toFrame(String data) {
    if ("[DONE]".equals(data)) {
      return UnifiedSseFrame.done();
    }
    // Ensure model field reflects logical response shape; passthrough chunk JSON.
    try {
      JsonNode node = objectMapper.readTree(data);
      if (node instanceof ObjectNode objectNode && !objectNode.has("object")) {
        objectNode.put("object", "chat.completion.chunk");
      }
      return new UnifiedSseFrame(objectMapper.writeValueAsString(node));
    } catch (Exception ignored) {
      return new UnifiedSseFrame(data);
    }
  }

  private void requireApiKey() {
    if (settings.getApiKey() == null || settings.getApiKey().isBlank()) {
      throw GatewayError.unavailable(502, "OPENAI_API_KEY is not configured");
    }
  }

  private static String trimTrailingSlash(String url) {
    if (url == null || url.isBlank()) {
      return url;
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
