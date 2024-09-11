package care.smith.fts.cda.impl;

import static java.util.Objects.requireNonNull;

import care.smith.fts.api.cda.CohortSelector;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientSsl;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component("trustCenterAgentCohortSelector")
public class TCACohortSelectorFactory implements CohortSelector.Factory<TCACohortSelectorConfig> {
  private final WebClient.Builder clientBuilder;
  private final WebClientSsl ssl;
  private final MeterRegistry meterRegistry;

  public TCACohortSelectorFactory(
      WebClient.Builder clientBuilder, WebClientSsl ssl, MeterRegistry meterRegistry) {
    this.ssl = ssl;
    this.meterRegistry = meterRegistry;
    log.info("Factory client {}", clientBuilder);
    this.clientBuilder = requireNonNull(clientBuilder);
  }

  @Override
  public Class<TCACohortSelectorConfig> getConfigType() {
    return TCACohortSelectorConfig.class;
  }

  @Override
  public CohortSelector create(CohortSelector.Config ignored, TCACohortSelectorConfig config) {
    var client = config.server().createClient(clientBuilder, ssl);
    log.info("Created Client {}", client);
    return new TCACohortSelector(config, client, meterRegistry);
  }
}
