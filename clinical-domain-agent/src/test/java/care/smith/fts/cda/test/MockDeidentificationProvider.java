package care.smith.fts.cda.test;

import care.smith.fts.api.DeidentificationProvider;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.cda.services.deidentifhir.ConsentedPatientBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Component;

@Component("mockDeidentificationProvider")
public class MockDeidentificationProvider
    implements DeidentificationProvider.Factory<
        ConsentedPatientBundle<Bundle>,
        TransportBundle<Bundle>,
        MockDeidentificationProvider.Config> {
  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public DeidentificationProvider<ConsentedPatientBundle<Bundle>, TransportBundle<Bundle>> create(
      DeidentificationProvider.Config commonConfig, Config implConfig) {
    return (b) -> {
      throw new UnsupportedOperationException();
    };
  }

  public record Config(boolean deidentify) {}
}
