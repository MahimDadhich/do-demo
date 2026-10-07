package com.digitalocean.llmgateway.auth;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.auth")
public class AuthProperties {

  /** When false, all requests pass (useful only for local experiments). */
  private boolean enabled = true;

  private List<ApiKey> keys = new ArrayList<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public List<ApiKey> getKeys() {
    return keys;
  }

  public void setKeys(List<ApiKey> keys) {
    this.keys = keys;
  }

  public static class ApiKey {
    private String id = "unnamed";
    private String key = "";
    /** Scopes this key may use. Empty means no routes (fail closed on scoped paths). */
    private List<String> scopes = new ArrayList<>();

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

    public List<String> getScopes() {
      return scopes;
    }

    public void setScopes(List<String> scopes) {
      this.scopes = scopes;
    }
  }
}
