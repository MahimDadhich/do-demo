package com.digitalocean.llmgateway.types;

import com.fasterxml.jackson.databind.JsonNode;

public record ChatMessage(String role, JsonNode content) {}
