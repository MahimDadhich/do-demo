package com.digitalocean.llmgateway.types;

/** One SSE data payload (JSON chunk or the literal [DONE]). */
public record UnifiedSseFrame(String payload) {

  public static UnifiedSseFrame done() {
    return new UnifiedSseFrame("[DONE]");
  }

  public boolean isDone() {
    return "[DONE]".equals(payload);
  }
}
