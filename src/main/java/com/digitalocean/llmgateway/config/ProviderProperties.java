package com.digitalocean.llmgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "providers")
public class ProviderProperties {

  private OpenAi openai = new OpenAi();
  private Anthropic anthropic = new Anthropic();

  public OpenAi getOpenai() {
    return openai;
  }

  public void setOpenai(OpenAi openai) {
    this.openai = openai;
  }

  public Anthropic getAnthropic() {
    return anthropic;
  }

  public void setAnthropic(Anthropic anthropic) {
    this.anthropic = anthropic;
  }

  public static class OpenAi {
    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey = "";

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }
  }

  public static class Anthropic {
    private String baseUrl = "https://api.anthropic.com";
    private String apiKey = "";
    private String version = "2023-06-01";

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getVersion() {
      return version;
    }

    public void setVersion(String version) {
      this.version = version;
    }
  }
}
