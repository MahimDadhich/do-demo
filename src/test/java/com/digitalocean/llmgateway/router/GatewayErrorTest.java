package com.digitalocean.llmgateway.router;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GatewayErrorTest {

  @Test
  void classifiesRateLimitAsFailoverEligible() {
    GatewayError error = GatewayError.fromUpstreamStatus(429, "too many");
    assertThat(error.getErrorClass()).isEqualTo(ErrorClass.RATE_LIMITED);
    assertThat(error.isFailoverEligible()).isTrue();
    assertThat(error.getHttpStatus()).isEqualTo(429);
  }

  @Test
  void classifies5xxAsUnavailable() {
    GatewayError error = GatewayError.fromUpstreamStatus(503, "down");
    assertThat(error.getErrorClass()).isEqualTo(ErrorClass.UPSTREAM_UNAVAILABLE);
    assertThat(error.isFailoverEligible()).isTrue();
  }

  @Test
  void classifies4xxAsClientErrorNotEligible() {
    GatewayError error = GatewayError.fromUpstreamStatus(400, "bad");
    assertThat(error.getErrorClass()).isEqualTo(ErrorClass.CLIENT_ERROR);
    assertThat(error.isFailoverEligible()).isFalse();
  }

  @Test
  void modelNotFoundIs404() {
    GatewayError error = GatewayError.modelNotFound("x");
    assertThat(error.getHttpStatus()).isEqualTo(404);
    assertThat(error.getMessage()).contains("model_not_found");
  }
}
