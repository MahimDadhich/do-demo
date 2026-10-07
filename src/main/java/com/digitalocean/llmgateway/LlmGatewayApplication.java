package com.digitalocean.llmgateway;

import com.digitalocean.llmgateway.auth.AuthProperties;
import com.digitalocean.llmgateway.config.GatewayProperties;
import com.digitalocean.llmgateway.config.ProviderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({GatewayProperties.class, ProviderProperties.class, AuthProperties.class})
public class LlmGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(LlmGatewayApplication.class, args);
  }
}
