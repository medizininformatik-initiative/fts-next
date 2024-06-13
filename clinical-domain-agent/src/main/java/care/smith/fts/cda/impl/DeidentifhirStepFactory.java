package care.smith.fts.cda.impl;

import static com.typesafe.config.ConfigFactory.parseFile;

import care.smith.fts.api.DeidentificationProvider;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component("deidentifhirDeidentificationProvider")
public class DeidentifhirStepFactory
    implements DeidentificationProvider.Factory<Resource, DeidentifhirStepConfig> {

  private final WebClient.Builder builder;

  public DeidentifhirStepFactory(WebClient.Builder builder) {
    this.builder = builder;
  }

  @Override
  public Class<DeidentifhirStepConfig> getConfigType() {
    return DeidentifhirStepConfig.class;
  }

  @Override
  public DeidentificationProvider<Resource> create(
      DeidentificationProvider.Config commonConfig, DeidentifhirStepConfig implConfig) {
    var httpClient = implConfig.tca().server().createClient(builder);

    return new DeidentifhirStep(
        httpClient,
        implConfig.tca().domain(),
        implConfig.dateShift(),
        parseFile(implConfig.deidentifhirConfig()),
        parseFile(implConfig.scraperConfig()));
  }
}
