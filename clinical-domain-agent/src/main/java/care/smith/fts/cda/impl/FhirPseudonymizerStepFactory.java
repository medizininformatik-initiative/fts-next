package care.smith.fts.cda.impl;

import static java.util.Objects.requireNonNull;

import care.smith.fts.api.cda.Deidentificator;
import care.smith.fts.util.RetryStrategy;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.fhir.DateShiftAnonymizer;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component("fhir-pseudonymizerDeidentificator")
public class FhirPseudonymizerStepFactory
    implements Deidentificator.Factory<FhirPseudonymizerConfig> {

  private final WebClientFactory clientFactory;
  private final RetryStrategy retryStrategy;

  public FhirPseudonymizerStepFactory(WebClientFactory clientFactory, RetryStrategy retryStrategy) {
    this.clientFactory = clientFactory;
    this.retryStrategy = retryStrategy;
  }

  @Override
  public Class<FhirPseudonymizerConfig> getConfigType() {
    return FhirPseudonymizerConfig.class;
  }

  @Override
  public Deidentificator create(
      Deidentificator.Config commonConfig, FhirPseudonymizerConfig implConfig) {
    var fpClient = clientFactory.create(implConfig.server());
    var tcaClient = clientFactory.create(implConfig.trustCenterAgent().server());

    List<String> dateShiftPaths;
    try {
      dateShiftPaths =
          DateShiftAnonymizer.parseDateShiftPaths(requireNonNull(implConfig.anonymizationConfig()));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to parse anonymization config", e);
    }

    return new FhirPseudonymizerStep(
        fpClient,
        tcaClient,
        implConfig.trustCenterAgent().domains(),
        implConfig.maxDateShift(),
        implConfig.dateShiftPreserve(),
        dateShiftPaths,
        retryStrategy);
  }
}
