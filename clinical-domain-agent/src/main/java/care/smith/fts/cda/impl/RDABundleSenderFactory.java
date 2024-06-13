package care.smith.fts.cda.impl;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.api.BundleSender;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component("researchDomainAgentBundleSender")
public class RDABundleSenderFactory implements BundleSender.Factory<Bundle, RDABundleSenderConfig> {

  private final WebClient.Builder builder;

  public RDABundleSenderFactory(WebClient.Builder builder) {
    this.builder = builder;
  }

  @Override
  public Class<RDABundleSenderConfig> getConfigType() {
    return RDABundleSenderConfig.class;
  }

  @Override
  public BundleSender<Bundle> create(
      BundleSender.Config commonConfig, RDABundleSenderConfig implConfig) {
    return new RDABundleSender(implConfig, implConfig.server().createClient(builder));
  }
}
