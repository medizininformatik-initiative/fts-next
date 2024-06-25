package care.smith.fts.rda.impl;

import care.smith.fts.api.rda.BundleSender;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component("researchDomainAgentBundleSender")
public class FhirStoreBundleSenderFactory
    implements BundleSender.Factory<FhirStoreBundleSenderConfig> {

  private final WebClient.Builder builder;

  public FhirStoreBundleSenderFactory(WebClient.Builder builder) {
    this.builder = builder;
  }

  @Override
  public Class<FhirStoreBundleSenderConfig> getConfigType() {
    return FhirStoreBundleSenderConfig.class;
  }

  @Override
  public BundleSender create(
      BundleSender.Config commonConfig, FhirStoreBundleSenderConfig implConfig) {
    return new FhirStoreBundleSender(implConfig, implConfig.fhirServer().createClient(builder));
  }
}