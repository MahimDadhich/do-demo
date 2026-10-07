package com.digitalocean.llmgateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApiKeyAuthServiceTest {

  @Test
  void authenticatesConfiguredKeyWithScopes() {
    AuthProperties props = new AuthProperties();
    props.setEnabled(true);
    AuthProperties.ApiKey key = new AuthProperties.ApiKey();
    key.setId("demo");
    key.setKey("gw_demo_key");
    key.setScopes(List.of("chat"));
    props.setKeys(List.of(key));

    ApiKeyAuthService service = new ApiKeyAuthService(props);

    assertThat(service.authenticate("gw_demo_key"))
        .isPresent()
        .get()
        .satisfies(
            p -> {
              assertThat(p.keyId()).isEqualTo("demo");
              assertThat(p.hasScope("chat")).isTrue();
              assertThat(p.hasScope("admin")).isFalse();
            });
    assertThat(service.authenticate("wrong")).isEmpty();
    assertThat(service.authenticate("  gw_demo_key  ")).isPresent();
  }

  @Test
  void ignoresBlankKeys() {
    AuthProperties props = new AuthProperties();
    AuthProperties.ApiKey blank = new AuthProperties.ApiKey();
    blank.setId("blank");
    blank.setKey("  ");
    blank.setScopes(List.of("chat"));
    props.setKeys(List.of(blank));

    assertThat(new ApiKeyAuthService(props).authenticate("  ")).isEmpty();
  }
}
