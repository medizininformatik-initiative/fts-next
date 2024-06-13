package care.smith.fts.cda.impl;

import static java.util.Objects.requireNonNull;

import care.smith.fts.api.CohortSelector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component("trustCenterAgentCohortSelector")
public class TCACohortSelectorFactory implements CohortSelector.Factory<TCACohortSelectorConfig> {
  private final WebClient.Builder clientBuilder;

  public TCACohortSelectorFactory(WebClient.Builder clientBuilder) {
    this.clientBuilder = requireNonNull(clientBuilder);
  }

  @Override
  public Class<TCACohortSelectorConfig> getConfigType() {
    return TCACohortSelectorConfig.class;
  }

  @Override
  public CohortSelector create(CohortSelector.Config ignored, TCACohortSelectorConfig config) {
    var client = config.server().createClient(clientBuilder);
    return new TCACohortSelector(config, client);
  }
}
