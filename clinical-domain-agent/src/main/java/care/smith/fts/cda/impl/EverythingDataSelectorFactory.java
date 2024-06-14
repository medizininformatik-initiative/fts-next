package care.smith.fts.cda.impl;

import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.cda.services.PatientIdResolver;
import org.hl7.fhir.r4.model.IdType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component("everythingDataSelector")
public class EverythingDataSelectorFactory
    implements DataSelector.Factory<EverythingDataSelectorConfig> {

  private final WebClient.Builder clientBuilder;

  public EverythingDataSelectorFactory(WebClient.Builder clientBuilder) {
    this.clientBuilder = clientBuilder;
  }

  @Override
  public Class<EverythingDataSelectorConfig> getConfigType() {
    return EverythingDataSelectorConfig.class;
  }

  @Override
  public DataSelector create(DataSelector.Config common, EverythingDataSelectorConfig config) {
    var client = config.fhirServer().createClient(clientBuilder);
    PatientIdResolver resolver = createResolver(config, client);
    return new EverythingDataSelector(common, client, resolver);
  }

  private PatientIdResolver createResolver(EverythingDataSelectorConfig config, WebClient client) {
    if (config.resolve() != null) {
      return config.resolve().createService(client);
    } else {
      return pid -> new IdType("Patient", pid);
    }
  }
}
