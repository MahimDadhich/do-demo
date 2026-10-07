package com.digitalocean.llmgateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GatewayAuthFilterTest {

  private GatewayAuthFilter filter;
  private boolean chainCalled;

  @BeforeEach
  void setUp() {
    AuthProperties props = new AuthProperties();
    props.setEnabled(true);
    AuthProperties.ApiKey chatKey = new AuthProperties.ApiKey();
    chatKey.setId("demo");
    chatKey.setKey("gw_demo_key");
    chatKey.setScopes(List.of("chat"));
    AuthProperties.ApiKey noscope = new AuthProperties.ApiKey();
    noscope.setId("noscope");
    noscope.setKey("gw_noscope");
    noscope.setScopes(List.of());
    props.setKeys(List.of(chatKey, noscope));
    filter = new GatewayAuthFilter(new ApiKeyAuthService(props), new ObjectMapper());
    chainCalled = false;
  }

  @Test
  void healthIsPublic() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/health").build());
    StepVerifier.create(filter.filter(exchange, chain()))
        .verifyComplete();
    assertThat(chainCalled).isTrue();
    assertThat(exchange.getResponse().getStatusCode()).isNull();
  }

  @Test
  void missingKeyUnauthorized() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions").build());
    StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();
    assertThat(chainCalled).isFalse();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void invalidKeyUnauthorized() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions")
                .header("Authorization", "Bearer wrong")
                .build());
    StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();
    assertThat(chainCalled).isFalse();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void validBearerAllowsChat() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions")
                .header("Authorization", "Bearer gw_demo_key")
                .build());
    StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();
    assertThat(chainCalled).isTrue();
    Object principal = exchange.getAttribute(GatewayPrincipal.ATTRIBUTE);
    assertThat(principal).isInstanceOf(GatewayPrincipal.class);
  }

  @Test
  void validApiKeyHeaderAllowsChat() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions")
                .header("X-Api-Key", "gw_demo_key")
                .build());
    StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();
    assertThat(chainCalled).isTrue();
  }

  @Test
  void missingScopeForbidden() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions")
                .header("X-Api-Key", "gw_noscope")
                .build());
    StepVerifier.create(filter.filter(exchange, chain())).verifyComplete();
    assertThat(chainCalled).isFalse();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void disabledAuthBypasses() {
    AuthProperties props = new AuthProperties();
    props.setEnabled(false);
    GatewayAuthFilter open =
        new GatewayAuthFilter(new ApiKeyAuthService(props), new ObjectMapper());
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.post("/v1/chat/completions").build());
    StepVerifier.create(open.filter(exchange, chain())).verifyComplete();
    assertThat(chainCalled).isTrue();
  }

  private WebFilterChain chain() {
    return exchange -> {
      chainCalled = true;
      return Mono.empty();
    };
  }
}
