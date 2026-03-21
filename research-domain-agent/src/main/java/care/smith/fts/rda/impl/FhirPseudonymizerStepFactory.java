package care.smith.fts.rda.impl;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.api.rda.Deidentificator;
import care.smith.fts.util.WebClientFactory;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Factory for creating FhirPseudonymizerStep instances in the Research Domain Agent.
 *
 * <p>This factory implements the transfer process step factory pattern, enabling
 * configuration-based instantiation of FHIR Pseudonymizer deidentificators for tID→sID resolution.
 *
 * <p>Configuration example:
 *
 * <pre>{@code
 * deidentificator:
 *   fhir-pseudonymizer:
 *     server:
 *       baseUrl: "https://fhir-pseudonymizer.research.example.com"
 *       auth:
 *         type: oauth2
 *         clientId: "rda-client"
 *         clientSecret: "${FHIR_PSEUDONYMIZER_SECRET}"
 *     timeout: 60s
 *     maxRetries: 3
 * }</pre>
 *
 * <p>The factory is registered as a Spring bean with name "fhir-pseudonymizerDeidentificator" to
 * enable configuration-based selection between deidentification methods (deidentifhir vs
 * fhir-pseudonymizer).
 */
@Slf4j
@Component("fhir-pseudonymizerDeidentificator")
public class FhirPseudonymizerStepFactory
    implements Deidentificator.Factory<FhirPseudonymizerConfig> {

  private final WebClientFactory clientFactory;
  private final MeterRegistry meterRegistry;
  private final FhirContext fhirContext;

  public FhirPseudonymizerStepFactory(
      WebClientFactory clientFactory, MeterRegistry meterRegistry, FhirContext fhirContext) {
    this.clientFactory = clientFactory;
    this.meterRegistry = meterRegistry;
    this.fhirContext = fhirContext;
  }

  @Override
  public Class<FhirPseudonymizerConfig> getConfigType() {
    return FhirPseudonymizerConfig.class;
  }

  @Override
  public Deidentificator create(
      Deidentificator.Config commonConfig, FhirPseudonymizerConfig implConfig) {
    var httpClient = clientFactory.create(implConfig.server());

    log.info(
        "Created RDA FhirPseudonymizerStep with service URL: {}", implConfig.server().baseUrl());

    return new FhirPseudonymizerStep(httpClient, implConfig, meterRegistry, fhirContext);
  }
}
