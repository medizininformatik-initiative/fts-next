package care.smith.fts.cda.impl;

import static java.util.Objects.requireNonNull;

import care.smith.fts.api.CohortSelector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.springframework.stereotype.Component;

@Component("trustCenterAgentCohortSelector")
public class TCACohortSelectorFactory implements CohortSelector.Factory<TCACohortSelectorConfig> {
  private final HttpClientBuilder clientBuilder;
  private final ObjectMapper objectMapper;

  public TCACohortSelectorFactory(HttpClientBuilder clientBuilder, ObjectMapper objectMapper) {
    this.clientBuilder = requireNonNull(clientBuilder);
    this.objectMapper = requireNonNull(objectMapper);
  }

  @Override
  public Class<TCACohortSelectorConfig> getConfigType() {
    return TCACohortSelectorConfig.class;
  }

  @Override
  public CohortSelector create(CohortSelector.Config ignored, TCACohortSelectorConfig config) {
    CloseableHttpClient client = config.server().createClient(clientBuilder);
    return new TCACohortSelector(config, objectMapper, client);
  }
}
