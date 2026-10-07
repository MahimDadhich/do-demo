package com.digitalocean.llmgateway.auth;

import com.digitalocean.llmgateway.http.GatewayExceptionHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Thin gateway auth: API key + scope check before routing.
 *
 * <p>Accepts {@code Authorization: Bearer <key>} or {@code X-Api-Key: <key>}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class GatewayAuthFilter implements WebFilter {

  public static final String SCOPE_CHAT = "chat";

  private final ApiKeyAuthService authService;
  private final ObjectMapper objectMapper;

  public GatewayAuthFilter(ApiKeyAuthService authService, ObjectMapper objectMapper) {
    this.authService = authService;
    this.objectMapper = objectMapper;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (!authService.isEnabled() || isPublic(exchange.getRequest())) {
      return chain.filter(exchange);
    }

    String requiredScope = requiredScope(exchange.getRequest());
    if (requiredScope == null) {
      // Unknown protected surface — reject rather than accidentally open.
      return reject(exchange, HttpStatus.UNAUTHORIZED, "unauthorized", "authentication required");
    }

    Optional<String> presented = extractKey(exchange.getRequest());
    if (presented.isEmpty()) {
      return reject(exchange, HttpStatus.UNAUTHORIZED, "unauthorized", "missing api key");
    }

    Optional<GatewayPrincipal> principal = authService.authenticate(presented.get());
    if (principal.isEmpty()) {
      return reject(exchange, HttpStatus.UNAUTHORIZED, "unauthorized", "invalid api key");
    }

    if (!principal.get().hasScope(requiredScope)) {
      return reject(
          exchange,
          HttpStatus.FORBIDDEN,
          "forbidden",
          "missing required scope: " + requiredScope);
    }

    exchange.getAttributes().put(GatewayPrincipal.ATTRIBUTE, principal.get());
    return chain.filter(exchange);
  }

  private static boolean isPublic(ServerHttpRequest request) {
    String path = request.getURI().getPath();
    return "GET".equalsIgnoreCase(request.getMethod().name())
        && ("/health".equals(path) || "/".equals(path));
  }

  private static String requiredScope(ServerHttpRequest request) {
    String path = request.getURI().getPath();
    HttpMethod method = request.getMethod();
    if (method == HttpMethod.POST && "/v1/chat/completions".equals(path)) {
      return SCOPE_CHAT;
    }
    return null;
  }

  static Optional<String> extractKey(ServerHttpRequest request) {
    List<String> apiKeyHeaders = request.getHeaders().getOrEmpty("X-Api-Key");
    if (!apiKeyHeaders.isEmpty() && apiKeyHeaders.getFirst() != null) {
      String value = apiKeyHeaders.getFirst().trim();
      if (!value.isEmpty()) {
        return Optional.of(value);
      }
    }

    String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authorization == null || authorization.isBlank()) {
      return Optional.empty();
    }
    String trimmed = authorization.trim();
    if (trimmed.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
      String token = trimmed.substring(7).trim();
      return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
    return Optional.empty();
  }

  private Mono<Void> reject(
      ServerWebExchange exchange, HttpStatus status, String code, String message) {
    exchange.getResponse().setStatusCode(status);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    Map<String, Object> body = GatewayExceptionHandler.errorBody(code, message);
    byte[] bytes;
    try {
      bytes = objectMapper.writeValueAsBytes(body);
    } catch (JsonProcessingException e) {
      bytes = ("{\"error\":{\"message\":\"" + message + "\"}}").getBytes(StandardCharsets.UTF_8);
    }
    DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }
}
