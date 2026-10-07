package com.digitalocean.llmgateway.adapters;

import com.digitalocean.llmgateway.config.GatewayProperties;
import com.digitalocean.llmgateway.config.ProviderProperties;
import com.digitalocean.llmgateway.router.GatewayError;
import com.digitalocean.llmgateway.types.ChatMessage;
import com.digitalocean.llmgateway.types.UnifiedChatRequest;
import com.digitalocean.llmgateway.types.UnifiedSseFrame;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class AnthropicAdapter implements ProviderAdapter {

  private final WebClient.Builder webClientBuilder;
  private final ProviderProperties.Anthropic settings;
  private final ObjectMapper objectMapper;

  public AnthropicAdapter(
      WebClient.Builder webClientBuilder,
      ProviderProperties providerProperties,
      ObjectMapper objectMapper) {
    this.webClientBuilder = webClientBuilder;
    this.settings = providerProperties.getAnthropic();
    this.objectMapper = objectMapper;
  }

  @Override
  public String id() {
    return "anthropic";
  }

  @Override
  public Flux<UnifiedSseFrame> stream(
      UnifiedChatRequest request, GatewayProperties.Target target, AttemptContext context) {
    requireApiKey();
    String completionId = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    Map<String, Object> body = buildBody(request, target.getUpstreamModel(), true);

    return client(context)
        .post()
        .uri("/v1/messages")
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
                                .concatMap(data -> mapAnthropicEvent(data, completionId, target.getUpstreamModel()))
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
        .uri("/v1/messages")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchangeToMono(
            response ->
                UpstreamHttpSupport.ensureSuccess(response)
                    .flatMap(ok -> ok.bodyToMono(JsonNode.class)))
        .map(node -> toOpenAiCompletion(node, target.getUpstreamModel()))
        .timeout(context.attemptOverallTimeout())
        .onErrorMap(UpstreamHttpSupport::mapTransportError);
  }

  private WebClient client(AttemptContext context) {
    return webClientBuilder
        .baseUrl(trimTrailingSlash(settings.getBaseUrl()))
        .defaultHeader("x-api-key", settings.getApiKey())
        .defaultHeader("anthropic-version", settings.getVersion())
        .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
        .build()
        .mutate()
        .filter(
            (req, next) ->
                next.exchange(req)
                    .timeout(UpstreamHttpSupport.orDefault(context.ttfbTimeout(), Duration.ofSeconds(15))))
        .build();
  }

  private Map<String, Object> buildBody(
      UnifiedChatRequest request, String upstreamModel, boolean stream) {
    String system = null;
    List<Map<String, Object>> messages = new ArrayList<>();
    for (ChatMessage message : request.messages()) {
      if ("system".equalsIgnoreCase(message.role())) {
        system = textContent(message.content());
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("role", normalizeRole(message.role()));
      row.put("content", textContent(message.content()));
      messages.add(row);
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", upstreamModel);
    body.put("messages", messages);
    body.put("max_tokens", request.maxTokens() == null ? 1024 : request.maxTokens());
    body.put("stream", stream);
    if (system != null && !system.isBlank()) {
      body.put("system", system);
    }
    if (request.temperature() != null) {
      body.put("temperature", request.temperature());
    }
    return body;
  }

  private Flux<UnifiedSseFrame> mapAnthropicEvent(String data, String completionId, String model) {
    try {
      JsonNode node = objectMapper.readTree(data);
      String type = textOrEmpty(node.get("type"));
      if ("content_block_delta".equals(type)) {
        JsonNode delta = node.path("delta");
        if ("text_delta".equals(textOrEmpty(delta.get("type")))) {
          String text = textOrEmpty(delta.get("text"));
          return Flux.just(new UnifiedSseFrame(chunkJson(completionId, model, text, null)));
        }
      }
      if ("message_delta".equals(type)) {
        String stop = textOrEmpty(node.path("delta").path("stop_reason"));
        if (!stop.isBlank()) {
          String finish = mapStopReason(stop);
          return Flux.just(new UnifiedSseFrame(chunkJson(completionId, model, null, finish)));
        }
      }
      return Flux.empty();
    } catch (Exception e) {
      return Flux.error(GatewayError.fatal("failed to parse anthropic SSE: " + e.getMessage()));
    }
  }

  private JsonNode toOpenAiCompletion(JsonNode anthropic, String model) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("id", "chatcmpl-" + textOrEmpty(anthropic.get("id")));
    root.put("object", "chat.completion");
    root.put("model", model);
    ArrayNode choices = root.putArray("choices");
    ObjectNode choice = choices.addObject();
    choice.put("index", 0);
    ObjectNode message = choice.putObject("message");
    message.put("role", "assistant");
    message.put("content", extractText(anthropic.path("content")));
    choice.put("finish_reason", mapStopReason(textOrEmpty(anthropic.get("stop_reason"))));
    ObjectNode usage = root.putObject("usage");
    usage.put("prompt_tokens", anthropic.path("usage").path("input_tokens").asInt(0));
    usage.put("completion_tokens", anthropic.path("usage").path("output_tokens").asInt(0));
    usage.put(
        "total_tokens",
        usage.get("prompt_tokens").asInt() + usage.get("completion_tokens").asInt());
    return root;
  }

  private String chunkJson(String id, String model, String contentDelta, String finishReason)
      throws Exception {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("id", id);
    root.put("object", "chat.completion.chunk");
    root.put("model", model);
    ArrayNode choices = root.putArray("choices");
    ObjectNode choice = choices.addObject();
    choice.put("index", 0);
    ObjectNode delta = choice.putObject("delta");
    if (contentDelta != null) {
      delta.put("content", contentDelta);
    }
    if (finishReason != null) {
      choice.put("finish_reason", finishReason);
    } else {
      choice.putNull("finish_reason");
    }
    return objectMapper.writeValueAsString(root);
  }

  private String extractText(JsonNode content) {
    if (content == null || content.isNull()) {
      return "";
    }
    if (content.isTextual()) {
      return content.asText();
    }
    if (content.isArray()) {
      StringBuilder sb = new StringBuilder();
      content.forEach(
          block -> {
            if ("text".equals(textOrEmpty(block.get("type")))) {
              sb.append(textOrEmpty(block.get("text")));
            }
          });
      return sb.toString();
    }
    return content.toString();
  }

  private String textContent(JsonNode content) {
    return extractText(content);
  }

  private String normalizeRole(String role) {
    if ("assistant".equalsIgnoreCase(role)) {
      return "assistant";
    }
    return "user";
  }

  private String mapStopReason(String stop) {
    return switch (stop) {
      case "max_tokens" -> "length";
      case "end_turn", "stop_sequence" -> "stop";
      default -> stop == null || stop.isBlank() ? "stop" : stop;
    };
  }

  private String sseData(ServerSentEvent<?> event) {
    Object data = event.data();
    return data == null ? null : String.valueOf(data);
  }

  private String textOrEmpty(JsonNode node) {
    return node == null || node.isNull() ? "" : node.asText("");
  }

  private void requireApiKey() {
    if (settings.getApiKey() == null || settings.getApiKey().isBlank()) {
      throw GatewayError.unavailable(502, "ANTHROPIC_API_KEY is not configured");
    }
  }

  private static String trimTrailingSlash(String url) {
    if (url == null || url.isBlank()) {
      return url;
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
