package care.smith.fts.rda.impl;

import care.smith.fts.api.rda.DeidentificationProvider;
import care.smith.fts.util.HTTPClientConfig;
import care.smith.fts.util.auth.HTTPClientAuthMethod.AuthMethod;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component("deidentifhirDeidentificationProvider")
public class DeidentifhirDeidentificationProviderFactory
    implements DeidentificationProvider.Factory<
        DeidentifhirDeidentificationProviderFactory.Config> {

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
  public DeidentificationProvider create(
      DeidentificationProvider.Config commonConfig, Config implConfig) {

    HTTPClientConfig httpClientConfig =
        new HTTPClientConfig(implConfig.tcaBaseUrl, implConfig.auth);
    var httpClient = httpClientConfig.createClient(WebClient.builder());
    var config = ConfigFactory.parseFile(implConfig.deidentifhirConfigFile);
    return new DeidentifhirDeidentificationProvider(
        config, httpClient, implConfig.domain, implConfig.dateShift);
  }
}
