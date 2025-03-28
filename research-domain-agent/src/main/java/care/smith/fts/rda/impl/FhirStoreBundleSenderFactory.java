package care.smith.fts.rda.impl;

import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.util.WebClientFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component("fhirStoreBundleSender")
public class FhirStoreBundleSenderFactory
    implements BundleSender.Factory<FhirStoreBundleSenderConfig> {

  private final MeterRegistry meterRegistry;
  private final WebClientFactory clientFactory;

  public FhirStoreBundleSenderFactory(WebClientFactory clientFactory, MeterRegistry meterRegistry) {
    this.clientFactory = clientFactory;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Class<FhirStoreBundleSenderConfig> getConfigType() {
    return FhirStoreBundleSenderConfig.class;
  }

  @Override
  public BundleSender create(
      BundleSender.Config commonConfig, FhirStoreBundleSenderConfig implConfig) {
    var client = clientFactory.create(implConfig.server());
    return new FhirStoreBundleSender(client, meterRegistry);
  }
}
