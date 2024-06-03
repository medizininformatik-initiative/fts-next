package care.smith.fts.cda.impl;

import care.smith.fts.api.DataSelector;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

@Component("mockDataSelector")
public class MockDataSelector implements DataSelector.Factory<MockDataSelector.Config> {
  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public DataSelector create(DataSelector.Config commonConfig, Config implConfig) {
    return consentedPatient ->
    {
      Resource patient = new Patient().setId(consentedPatient.pid());
      List<BundleEntryComponent> entries = List.of(new BundleEntryComponent().setResource(patient));
      return new Bundle().setEntry(entries);
    };
  }

  public record Config() {}
}
