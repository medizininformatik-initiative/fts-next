package care.smith.fts.cda.impl;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.api.BundleSender;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Component;

@Component("researchDomainAgentBundleSender")
public class RDABundleSenderFactory implements BundleSender.Factory<Bundle, RDABundleSenderConfig> {

  private final HttpClientBuilder builder;
  private final FhirContext fhir;

  public RDABundleSenderFactory(HttpClientBuilder builder, FhirContext fhir) {
    this.builder = builder;
    this.fhir = fhir;
  }

  @Override
  public Class<RDABundleSenderConfig> getConfigType() {
    return RDABundleSenderConfig.class;
  }

  @Override
  public BundleSender<Bundle> create(
      BundleSender.Config commonConfig, RDABundleSenderConfig implConfig) {
    return new RDABundleSender(implConfig, implConfig.server().createClient(builder), fhir);
  }
}
