package com.digitalocean.llmgateway.config;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

  @Bean
  public WebClient.Builder webClientBuilder(GatewayProperties gatewayProperties) {
    Duration connect = gatewayProperties.getTimeouts().getConnect();
    HttpClient httpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connect.toMillis())
            .responseTimeout(gatewayProperties.getTimeouts().getAttemptOverall());

    return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
  }
}
