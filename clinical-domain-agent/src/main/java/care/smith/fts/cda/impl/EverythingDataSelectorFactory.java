package care.smith.fts.cda.impl;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import care.smith.fts.api.DataSelector;

import care.smith.fts.cda.services.PatientIdResolver;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.web.reactive.function.client.WebClient.builder;

@Component("everythingDataSelector")
public class EverythingDataSelectorFactory
    implements DataSelector.Factory<Bundle, EverythingDataSelectorConfig> {

  private final FhirContext fhir;

  public EverythingDataSelectorFactory(FhirContext fhir) {
    this.fhir = fhir;
  }

  @Override
  public Class<EverythingDataSelectorConfig> getConfigType() {
    return EverythingDataSelectorConfig.class;
  }

  @Override
  public DataSelector<Bundle> create(DataSelector.Config common, EverythingDataSelectorConfig config) {
    IGenericClient client = config.fhirServer().createClient(WebClient.builder());
    PatientIdResolver resolver = createResolver(config, client);
    return new EverythingDataSelector(common, builder().build(), resolver);
  }

  private PatientIdResolver createResolver(
      EverythingDataSelectorConfig config, IGenericClient client) {
    if (config.resolve() != null) {
      return config.resolve().createService(client, fhir);
    } else {
      return pid -> new IdType("Patient", pid);
    }
  }
}
