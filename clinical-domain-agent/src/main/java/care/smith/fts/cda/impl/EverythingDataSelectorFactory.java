package care.smith.fts.cda.impl;

import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.cda.services.PatientIdResolver;
import io.micrometer.core.instrument.MeterRegistry;
import org.hl7.fhir.r4.model.IdType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component("everythingDataSelector")
public class EverythingDataSelectorFactory
    implements DataSelector.Factory<EverythingDataSelectorConfig> {

  private final WebClient.Builder clientBuilder;
  private final MeterRegistry meterRegistry;

  public EverythingDataSelectorFactory(
      WebClient.Builder clientBuilder, @Autowired MeterRegistry meterRegistry) {
    this.clientBuilder = clientBuilder;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Class<EverythingDataSelectorConfig> getConfigType() {
    return EverythingDataSelectorConfig.class;
  }

  @Override
  public DataSelector create(DataSelector.Config common, EverythingDataSelectorConfig config) {
    var client = config.fhirServer().createClient(clientBuilder);
    PatientIdResolver resolver = createResolver(config, client);
    return new EverythingDataSelector(common, client, resolver, meterRegistry);
  }

  private PatientIdResolver createResolver(EverythingDataSelectorConfig config, WebClient client) {
    if (config.resolve() != null) {
      return config.resolve().createService(client, meterRegistry);
    } else {
      return pid -> Mono.just(new IdType("Patient", pid));
    }
  }
}
