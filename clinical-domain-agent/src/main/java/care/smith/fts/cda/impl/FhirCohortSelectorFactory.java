package care.smith.fts.cda.impl;

import static care.smith.fts.util.ConsentedPatientExtractor.getPatientIdentifier;

import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.cda.TransferProcessRunnerConfig;
import care.smith.fts.util.ConsentedPatientExtractor;
import care.smith.fts.util.RequiredPolicies;
import care.smith.fts.util.RetryStrategy;
import care.smith.fts.util.WebClientFactory;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler;

@Component("fhirCohortSelector")
public class FhirCohortSelectorFactory implements CohortSelector.Factory<FhirCohortSelectorConfig> {

  private final WebClientFactory clientFactory;
  private final RetryStrategy retryStrategy;
  private final Scheduler cohortSelectionScheduler;
  private final int cohortSelectionConcurrency;

  public FhirCohortSelectorFactory(
      WebClientFactory clientFactory,
      RetryStrategy retryStrategy,
      Scheduler cohortSelectionScheduler,
      TransferProcessRunnerConfig runnerConfig) {
    this.clientFactory = clientFactory;
    this.retryStrategy = retryStrategy;
    this.cohortSelectionScheduler = cohortSelectionScheduler;
    this.cohortSelectionConcurrency = runnerConfig.cohortSelectionConcurrency();
  }

  @Override
  public Class<FhirCohortSelectorConfig> getConfigType() {
    return FhirCohortSelectorConfig.class;
  }

  @Override
  public CohortSelector create(CohortSelector.Config ignored, FhirCohortSelectorConfig config) {
    var client = clientFactory.create(config.server());
    return new FhirCohortSelector(
        config,
        client,
        consentedPatientExtractor(config),
        retryStrategy,
        cohortSelectionScheduler,
        cohortSelectionConcurrency);
  }

  /** Reads consented patients by their configured identifier from the FHIR server's bundles. */
  static ConsentedPatientExtractor consentedPatientExtractor(FhirCohortSelectorConfig config) {
    var patientIdentifierSystem = config.patientIdentifierSystem();
    return new ConsentedPatientExtractor(
        patientIdentifierSystem,
        new RequiredPolicies(config.policySystem(), config.policies()),
        b -> getPatientIdentifier(patientIdentifierSystem, b));
  }
}
