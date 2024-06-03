package care.smith.fts.cda.impl;

import care.smith.fts.api.DataSelector;
import care.smith.fts.util.HTTPClientConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.springframework.stereotype.Component;

@Component("everythingDataSelector")
public class EverythingDataSelector implements DataSelector.Factory<EverythingDataSelector.Config> {

  public record Config(
      HTTPClientConfig fhirServer,
      boolean dataFilter) {}

  private final HttpClientBuilder clientBuilder;

  public EverythingDataSelector(HttpClientBuilder clientBuilder) {
    this.clientBuilder = clientBuilder;
  }

  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public DataSelector create(DataSelector.Config ignored, Config config) {
    CloseableHttpClient client = config.fhirServer().createClient(clientBuilder);
    // TODO Implement
    throw new UnsupportedOperationException();
  }
}
