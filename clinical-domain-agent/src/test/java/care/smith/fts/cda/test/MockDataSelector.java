package care.smith.fts.cda.test;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.DataSelector;
import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseBundle;
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
    return new Impl();
  }

  public record Config() {}

  public static class Impl implements DataSelector {
    @Override
    public IBaseBundle select(ConsentedPatient consentedPatient) {
        Resource patient = new Patient().setId(consentedPatient.id());
        List<BundleEntryComponent> entries = List.of(new BundleEntryComponent().setResource(patient));
        return new Bundle().setEntry(entries);
    }
  }
}
