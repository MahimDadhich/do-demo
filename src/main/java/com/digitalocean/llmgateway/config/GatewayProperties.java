package com.digitalocean.llmgateway.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

  private String defaultModel = "assistant-default";
  private int maxTargets = 3;
  private Timeouts timeouts = new Timeouts();
  private Map<String, ModelRoute> models = new LinkedHashMap<>();

  public String getDefaultModel() {
    return defaultModel;
  }

  public void setDefaultModel(String defaultModel) {
    this.defaultModel = defaultModel;
  }

  public int getMaxTargets() {
    return maxTargets;
  }

  public void setMaxTargets(int maxTargets) {
    this.maxTargets = maxTargets;
  }

  public Timeouts getTimeouts() {
    return timeouts;
  }

  public void setTimeouts(Timeouts timeouts) {
    this.timeouts = timeouts;
  }

  public Map<String, ModelRoute> getModels() {
    return models;
  }

  public void setModels(Map<String, ModelRoute> models) {
    this.models = models;
  }

  public static class Timeouts {
    private Duration connect = Duration.ofSeconds(5);
    private Duration ttfb = Duration.ofSeconds(15);
    private Duration interChunkIdle = Duration.ofSeconds(30);
    private Duration attemptOverall = Duration.ofSeconds(120);
    private Duration requestOverall = Duration.ofSeconds(180);

    public Duration getConnect() {
      return connect;
    }

    public void setConnect(Duration connect) {
      this.connect = connect;
    }

    public Duration getTtfb() {
      return ttfb;
    }

    public void setTtfb(Duration ttfb) {
      this.ttfb = ttfb;
    }

    public Duration getInterChunkIdle() {
      return interChunkIdle;
    }

    public void setInterChunkIdle(Duration interChunkIdle) {
      this.interChunkIdle = interChunkIdle;
    }

    public Duration getAttemptOverall() {
      return attemptOverall;
    }

    public void setAttemptOverall(Duration attemptOverall) {
      this.attemptOverall = attemptOverall;
    }

    public Duration getRequestOverall() {
      return requestOverall;
    }

    public void setRequestOverall(Duration requestOverall) {
      this.requestOverall = requestOverall;
    }
  }

  public static class ModelRoute {
    private List<Target> targets = new ArrayList<>();

    public List<Target> getTargets() {
      return targets;
    }

    public void setTargets(List<Target> targets) {
      this.targets = targets;
    }
  }

  public static class Target {
    private String provider;
    private String upstreamModel;
    private String credentialsRef;

    public String getProvider() {
      return provider;
    }

    public void setProvider(String provider) {
      this.provider = provider;
    }

    public String getUpstreamModel() {
      return upstreamModel;
    }

    public void setUpstreamModel(String upstreamModel) {
      this.upstreamModel = upstreamModel;
    }

    public String getCredentialsRef() {
      return credentialsRef;
    }

    public void setCredentialsRef(String credentialsRef) {
      this.credentialsRef = credentialsRef;
    }
  }
}
