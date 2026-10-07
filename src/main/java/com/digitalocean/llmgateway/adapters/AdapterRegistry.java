package com.digitalocean.llmgateway.adapters;

import com.digitalocean.llmgateway.router.GatewayError;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AdapterRegistry {

  private final Map<String, ProviderAdapter> adapters;

  public AdapterRegistry(List<ProviderAdapter> adapterList) {
    this.adapters =
        adapterList.stream()
            .collect(Collectors.toMap(a -> a.id().toLowerCase(Locale.ROOT), Function.identity()));
  }

  public ProviderAdapter get(String provider) {
    ProviderAdapter adapter = adapters.get(provider.toLowerCase(Locale.ROOT));
    if (adapter == null) {
      throw GatewayError.client(500, "no adapter registered for provider '" + provider + "'");
    }
    return adapter;
  }
}
