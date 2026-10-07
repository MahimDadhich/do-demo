package com.digitalocean.llmgateway.config;

import com.digitalocean.llmgateway.router.GatewayError;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RouteConfig {

  private final GatewayProperties properties;

  public RouteConfig(GatewayProperties properties) {
    this.properties = properties;
  }

  public String resolveLogicalModel(String requestedModel) {
    if (requestedModel == null || requestedModel.isBlank()) {
      return properties.getDefaultModel();
    }
    return requestedModel.trim();
  }

  public List<GatewayProperties.Target> getTargets(String logicalModel) {
    GatewayProperties.ModelRoute route = properties.getModels().get(logicalModel);
    if (route == null || route.getTargets() == null || route.getTargets().isEmpty()) {
      throw GatewayError.modelNotFound(logicalModel);
    }
    int max = Math.max(1, properties.getMaxTargets());
    List<GatewayProperties.Target> targets = route.getTargets();
    return targets.size() <= max ? List.copyOf(targets) : List.copyOf(targets.subList(0, max));
  }

  public GatewayProperties.Timeouts timeouts() {
    return properties.getTimeouts();
  }
}
