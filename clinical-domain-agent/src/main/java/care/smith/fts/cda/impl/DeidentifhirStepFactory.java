package care.smith.fts.cda.impl;

import static com.typesafe.config.ConfigFactory.parseFile;

import care.smith.fts.api.DeidentificationProvider;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.cda.services.deidentifhir.ConsentedPatientBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component("deidentifhirDeidentificationProvider")
public class DeidentifhirStepFactory
    implements DeidentificationProvider.Factory<
        ConsentedPatientBundle<Bundle>, TransportBundle<Bundle>, DeidentifhirStepConfig> {

  private final WebClient.Builder builder;

  public DeidentifhirStepFactory(WebClient.Builder builder) {
    this.builder = builder;
  }

  @Override
  public Class<DeidentifhirStepConfig> getConfigType() {
    return DeidentifhirStepConfig.class;
  }

  @Override
  public DeidentificationProvider<ConsentedPatientBundle<Bundle>, TransportBundle<Bundle>> create(
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
