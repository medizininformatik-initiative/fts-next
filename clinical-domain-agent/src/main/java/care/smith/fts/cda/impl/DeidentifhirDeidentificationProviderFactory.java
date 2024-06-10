package care.smith.fts.cda.impl;

import care.smith.fts.api.DeidentificationProvider;
import care.smith.fts.util.HTTPClientConfig;
import care.smith.fts.util.auth.HTTPClientAuthMethod.AuthMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.time.Duration;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

@Component("deidentifhirDeidentificationProvider")
public class DeidentifhirDeidentificationProviderFactory
    implements DeidentificationProvider.Factory<
        Resource, DeidentifhirDeidentificationProviderFactory.Config> {

  public record Config(
      String tcaBaseUrl,
      AuthMethod auth,
      String domain,
      Duration dateShift,
      File deidentifhirConfigFile,
      File scraperConfigFile) {}

  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public DeidentificationProvider<Resource> create(
      DeidentificationProvider.Config commonConfig, Config implConfig) {

    HTTPClientConfig httpClientConfig =
        new HTTPClientConfig(implConfig.tcaBaseUrl, implConfig.auth);
    var httpClient = httpClientConfig.createClient(HttpClientBuilder.create());

    return new DeidentifhirDeidentificationProvider(
        implConfig.deidentifhirConfigFile,
        implConfig.scraperConfigFile,
        httpClient,
        new ObjectMapper(),
        implConfig.domain,
        implConfig.dateShift);
  }
}
