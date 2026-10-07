package com.digitalocean.llmgateway.config;

import com.digitalocean.llmgateway.router.GatewayError;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
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

  /**
   * Resolve attempt order for a logical model:
   *
   * <ol>
   *   <li>Primary = first configured target (default strategy)
   *   <li>Secondaries = remaining targets sorted by {@code cost} ascending (cheapest first)
   *   <li>Trim to {@code max-targets}
   * </ol>
   */
  public List<GatewayProperties.Target> getTargets(String logicalModel) {
    GatewayProperties.ModelRoute route = properties.getModels().get(logicalModel);
    if (route == null || route.getTargets() == null || route.getTargets().isEmpty()) {
      throw GatewayError.modelNotFound(logicalModel);
    }

    List<GatewayProperties.Target> configured = route.getTargets();
    List<GatewayProperties.Target> ordered = new ArrayList<>(configured.size());
    ordered.add(configured.get(0));

    if (configured.size() > 1) {
      List<GatewayProperties.Target> secondaries = configured.subList(1, configured.size());
      List<Integer> rank =
          IntStream.range(0, secondaries.size())
              .boxed()
              .sorted(
                  Comparator.comparingDouble((Integer i) -> secondaries.get(i).getCost())
                      .thenComparingInt(i -> i))
              .toList();
      for (int i : rank) {
        ordered.add(secondaries.get(i));
      }
    }

    int max = Math.max(1, properties.getMaxTargets());
    return ordered.size() <= max
        ? List.copyOf(ordered)
        : List.copyOf(ordered.subList(0, max));
  }

  public GatewayProperties.Timeouts timeouts() {
    return properties.getTimeouts();
  }
}
