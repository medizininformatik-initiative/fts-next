package care.smith.fts.cda.impl;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import care.smith.fts.api.DataSelector;

import care.smith.fts.cda.services.FhirResolveService;
import care.smith.fts.cda.services.PatientIdResolver;
import org.hl7.fhir.r4.model.IdType;
import org.springframework.stereotype.Component;

@Component("everythingDataSelector")
public class EverythingDataSelectorFactory
    implements DataSelector.Factory<EverythingDataSelectorConfig> {

  private final IRestfulClientFactory clientFactory;

  public EverythingDataSelectorFactory(IRestfulClientFactory clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Class<EverythingDataSelectorConfig> getConfigType() {
    return EverythingDataSelectorConfig.class;
  }

  @Override
  public DataSelector create(DataSelector.Config common, EverythingDataSelectorConfig config) {
    IGenericClient client = config.fhirServer().createClient(clientFactory);
    PatientIdResolver resolver = createResolver(config, client);
    return new EverythingDataSelector(common, client, resolver);
  }

  private static PatientIdResolver createResolver(
      EverythingDataSelectorConfig config, IGenericClient client) {
    if (config.resolve() != null) {
      return config.resolve().createService(client);
    } else {
      return pid -> new IdType("Patient", pid);
    }
  }
}
