package com.digitalocean.llmgateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RouteConfigTest {

  @Test
  void keepsPrimaryFirstAndSortsSecondariesByAscendingCost() {
    GatewayProperties properties = new GatewayProperties();
    properties.setMaxTargets(5);

    GatewayProperties.Target primary = target("anthropic", "claude", 15);
    GatewayProperties.Target expensive = target("expensive", "big", 20);
    GatewayProperties.Target cheap = target("cheap", "small", 2);
    GatewayProperties.Target mid = target("mid", "medium", 8);

    GatewayProperties.ModelRoute route = new GatewayProperties.ModelRoute();
    // YAML order: primary, expensive, cheap, mid — secondaries should become cheap, mid, expensive
    route.setTargets(List.of(primary, expensive, cheap, mid));
    properties.getModels().put("assistant-default", route);

    RouteConfig config = new RouteConfig(properties);
    List<GatewayProperties.Target> ordered = config.getTargets("assistant-default");

    assertThat(ordered).extracting(GatewayProperties.Target::getProvider)
        .containsExactly("anthropic", "cheap", "mid", "expensive");
  }

  @Test
  void equalCostKeepsConfiguredSecondaryOrder() {
    GatewayProperties properties = new GatewayProperties();
    GatewayProperties.Target primary = target("anthropic", "claude", 15);
    GatewayProperties.Target a = target("a", "a", 5);
    GatewayProperties.Target b = target("b", "b", 5);

    GatewayProperties.ModelRoute route = new GatewayProperties.ModelRoute();
    route.setTargets(List.of(primary, a, b));
    properties.getModels().put("assistant-default", route);

    List<GatewayProperties.Target> ordered = new RouteConfig(properties).getTargets("assistant-default");
    assertThat(ordered).extracting(GatewayProperties.Target::getProvider)
        .containsExactly("anthropic", "a", "b");
  }

  @Test
  void respectsMaxTargetsAfterCostSort() {
    GatewayProperties properties = new GatewayProperties();
    properties.setMaxTargets(2);

    GatewayProperties.Target primary = target("anthropic", "claude", 15);
    GatewayProperties.Target expensive = target("expensive", "big", 20);
    GatewayProperties.Target cheap = target("cheap", "small", 2);

    GatewayProperties.ModelRoute route = new GatewayProperties.ModelRoute();
    route.setTargets(List.of(primary, expensive, cheap));
    properties.getModels().put("assistant-default", route);

    List<GatewayProperties.Target> ordered = new RouteConfig(properties).getTargets("assistant-default");
    assertThat(ordered).extracting(GatewayProperties.Target::getProvider)
        .containsExactly("anthropic", "cheap");
  }

  private static GatewayProperties.Target target(String provider, String model, double cost) {
    GatewayProperties.Target t = new GatewayProperties.Target();
    t.setProvider(provider);
    t.setUpstreamModel(model);
    t.setCredentialsRef(provider + "_creds");
    t.setCost(cost);
    return t;
  }
}
