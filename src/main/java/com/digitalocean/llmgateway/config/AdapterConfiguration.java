package com.digitalocean.llmgateway.config;

import com.digitalocean.llmgateway.adapters.OpenAiAdapter;
import com.digitalocean.llmgateway.adapters.ProviderAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AdapterConfiguration {

  @Bean
  public ProviderAdapter openaiAdapter(
      WebClient.Builder webClientBuilder,
      ProviderProperties providerProperties,
      ObjectMapper objectMapper) {
    return new OpenAiAdapter(
        "openai", webClientBuilder, providerProperties.getOpenai(), objectMapper);
  }

  @Bean
  public ProviderAdapter openaiCheapAdapter(
      WebClient.Builder webClientBuilder,
      ProviderProperties providerProperties,
      ObjectMapper objectMapper) {
    return new OpenAiAdapter(
        "openai-cheap", webClientBuilder, providerProperties.getOpenaiCheap(), objectMapper);
  }

  @Bean
  public ProviderAdapter openaiExpensiveAdapter(
      WebClient.Builder webClientBuilder,
      ProviderProperties providerProperties,
      ObjectMapper objectMapper) {
    return new OpenAiAdapter(
        "openai-expensive",
        webClientBuilder,
        providerProperties.getOpenaiExpensive(),
        objectMapper);
  }
}
