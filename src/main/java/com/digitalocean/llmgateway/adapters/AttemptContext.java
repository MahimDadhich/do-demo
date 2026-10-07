package com.digitalocean.llmgateway.adapters;

import com.digitalocean.llmgateway.config.GatewayProperties;
import java.time.Duration;

public record AttemptContext(
    String requestId,
    int targetIndex,
    Duration connectTimeout,
    Duration ttfbTimeout,
    Duration interChunkIdleTimeout,
    Duration attemptOverallTimeout) {

  public static AttemptContext of(
      String requestId, int targetIndex, GatewayProperties.Timeouts timeouts) {
    return new AttemptContext(
        requestId,
        targetIndex,
        timeouts.getConnect(),
        timeouts.getTtfb(),
        timeouts.getInterChunkIdle(),
        timeouts.getAttemptOverall());
  }
}
