package care.smith.fts.cda.impl;

import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.cda.services.FhirResolveService;
import care.smith.fts.cda.services.PatientIdResolver;
import care.smith.fts.util.WebClientFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component("everythingDataSelector")
public class EverythingDataSelectorFactory
    implements DataSelector.Factory<EverythingDataSelectorConfig> {

  private final WebClientFactory clientFactory;
  private final MeterRegistry meterRegistry;

  public EverythingDataSelectorFactory(
      WebClientFactory clientFactory, MeterRegistry meterRegistry) {
    this.clientFactory = clientFactory;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Class<EverythingDataSelectorConfig> getConfigType() {
    return EverythingDataSelectorConfig.class;
  }

  @Override
  public DataSelector create(DataSelector.Config common, EverythingDataSelectorConfig config) {
    var client = clientFactory.create(config.fhirServer());
    var resolver = createResolver(config, client);
    return new EverythingDataSelector(common, client, resolver, meterRegistry, config.pageSize());
  }

  private PatientIdResolver createResolver(
      EverythingDataSelectorConfig config, WebClient hdsClient) {
    if (config.resolve() != null) {
      return config.resolve().createService(hdsClient, meterRegistry);
    } else {
      // Use FhirResolveService with null identifierSystem to fall back to
      // patient.patientIdentifierSystem()
      return new FhirResolveService(hdsClient, meterRegistry);
    }
  }
}
