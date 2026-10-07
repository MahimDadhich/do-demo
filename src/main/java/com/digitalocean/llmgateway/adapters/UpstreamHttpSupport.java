package com.digitalocean.llmgateway.adapters;

import com.digitalocean.llmgateway.router.GatewayError;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

final class UpstreamHttpSupport {

  private UpstreamHttpSupport() {}

  static Mono<ClientResponse> ensureSuccess(ClientResponse response) {
    HttpStatusCode status = response.statusCode();
    if (status.is2xxSuccessful()) {
      return Mono.just(response);
    }
    return response
        .bodyToMono(String.class)
        .defaultIfEmpty("")
        .flatMap(body -> Mono.error(GatewayError.fromUpstreamStatus(status.value(), body)));
  }

  static Throwable mapTransportError(Throwable error) {
    if (error instanceof GatewayError gatewayError) {
      return gatewayError;
    }
    if (error instanceof TimeoutException
        || (error.getMessage() != null && error.getMessage().toLowerCase().contains("timeout"))) {
      return GatewayError.timeout("upstream timeout: " + error.getMessage());
    }
    if (error instanceof WebClientRequestException requestException) {
      return GatewayError.unavailable(
          502, "upstream connection failed: " + requestException.getMessage());
    }
    return GatewayError.unavailable(502, "upstream transport error: " + error.getMessage());
  }

  static Duration orDefault(Duration value, Duration fallback) {
    return value == null ? fallback : value;
  }
}
