package com.digitalocean.llmgateway.auth;

import java.util.Set;

/** Authenticated gateway caller (expand later with quotas, tenants, etc.). */
public record GatewayPrincipal(String keyId, Set<String> scopes) {

  public static final String ATTRIBUTE = "gateway.principal";

  public boolean hasScope(String scope) {
    return scopes != null && scopes.contains(scope);
  }
}
