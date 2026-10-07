package com.digitalocean.llmgateway.auth;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyAuthService {

  private final boolean enabled;
  private final Map<String, GatewayPrincipal> principalsByKey;

  public ApiKeyAuthService(AuthProperties properties) {
    this.enabled = properties.isEnabled();
    Map<String, GatewayPrincipal> map = new LinkedHashMap<>();
    for (AuthProperties.ApiKey entry : properties.getKeys()) {
      if (entry.getKey() == null || entry.getKey().isBlank()) {
        continue;
      }
      map.put(
          entry.getKey(),
          new GatewayPrincipal(
              entry.getId() == null || entry.getId().isBlank() ? "unnamed" : entry.getId(),
              Set.copyOf(entry.getScopes() == null ? Set.of() : entry.getScopes())));
    }
    this.principalsByKey = Map.copyOf(map);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Optional<GatewayPrincipal> authenticate(String rawKey) {
    if (rawKey == null || rawKey.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(principalsByKey.get(rawKey.trim()));
  }
}
