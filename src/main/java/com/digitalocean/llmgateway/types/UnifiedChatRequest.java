package com.digitalocean.llmgateway.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UnifiedChatRequest(
    String model,
    @NotNull @NotEmpty List<ChatMessage> messages,
    Boolean stream,
    Double temperature,
    @JsonProperty("max_tokens") Integer maxTokens,
    Map<String, JsonNode> extra) {

  public boolean streaming() {
    return Boolean.TRUE.equals(stream);
  }
}
