package care.smith.fts.cda.impl;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.Deidentificator;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Deidentificator implementation that delegates deidentification to an external FHIR Pseudonymizer
 * service.
 *
 * <p>This implementation provides an alternative to DeidentifhirStep, enabling deidentification via
 * a configurable external service. The FHIR Pseudonymizer service internally calls the TCA's
 * Vfps-compatible FHIR operations for transport ID generation.
 *
 * <p>Architecture:
 *
 * <pre>
 * ConsentedPatientBundle
 *     ↓
 * FhirPseudonymizerStep (this class)
 *     ↓ [HTTP POST /fhir with FHIR Bundle]
 * FHIR Pseudonymizer Service (external, clinical domain)
 *     ↓ [POST /$create-pseudonym]
 * TCA CdAgentFhirPseudonymizerController
 *     ↓ [transport ID generation, sID stored in Redis]
 * TransportBundle (with transport IDs)
 * </pre>
 *
 * <p>The transport IDs in the returned bundle are temporary identifiers that will be resolved to
 * real pseudonyms by the RDA via TCA's /rd-agent/fhir endpoint.
 */
@Slf4j
public class FhirPseudonymizerStep implements Deidentificator {

  private static final String FHIR_ENDPOINT = "/fhir";

  private final WebClient fhirPseudonymizerClient;
  private final FhirPseudonymizerConfig config;
  private final MeterRegistry meterRegistry;
  private final FhirContext fhirContext;

  public FhirPseudonymizerStep(
      WebClient fhirPseudonymizerClient,
      FhirPseudonymizerConfig config,
      MeterRegistry meterRegistry,
      FhirContext fhirContext) {
    this.fhirPseudonymizerClient = fhirPseudonymizerClient;
    this.config = config;
    this.meterRegistry = meterRegistry;
    this.fhirContext = fhirContext;
  }

  /**
   * Deidentifies a FHIR Bundle via external FHIR Pseudonymizer service.
   *
   * <p>Sends the ConsentedPatientBundle to the FHIR Pseudonymizer service, which processes the
   * bundle and returns a deidentified version containing transport IDs instead of real pseudonyms.
   *
   * @param bundle ConsentedPatientBundle containing patient data to deidentify
   * @return Mono of TransportBundle with deidentified bundle and transfer ID
   */
  @Override
  public Mono<TransportBundle> deidentify(ConsentedPatientBundle bundle) {
    log.debug(
        "Deidentifying bundle for patient {} via FHIR Pseudonymizer",
        bundle.consentedPatient().id());

    String bundleJson = fhirContext.newJsonParser().encodeResourceToString(bundle.bundle());

    return fhirPseudonymizerClient
        .post()
        .uri(FHIR_ENDPOINT)
        .contentType(APPLICATION_FHIR_JSON)
        .accept(APPLICATION_FHIR_JSON)
        .bodyValue(bundleJson)
        .retrieve()
        .bodyToMono(String.class)
        .map(this::parseDeidentifiedBundle)
        .map(this::createTransportBundle)
        .timeout(config.timeout())
        .retryWhen(defaultRetryStrategy(meterRegistry, "fhirPseudonymizerDeidentification"))
        .doOnSuccess(
            result ->
                log.debug(
                    "Successfully deidentified bundle for patient {}, transfer ID: {}",
                    bundle.consentedPatient().id(),
                    result.transferId()))
        .doOnError(
            error ->
                log.error(
                    "Failed to deidentify bundle for patient {}: {}",
                    bundle.consentedPatient().id(),
                    error.getMessage()));
  }

  private Bundle parseDeidentifiedBundle(String bundleJson) {
    return fhirContext.newJsonParser().parseResource(Bundle.class, bundleJson);
  }

  private TransportBundle createTransportBundle(Bundle deidentifiedBundle) {
    var transferId = deidentifiedBundle.getIdPart();

    // Note: HAPI FHIR's getIdPart() returns null for empty/missing IDs, never empty string
    if (transferId == null) {
      throw new IllegalStateException(
          "FHIR Pseudonymizer returned bundle without transfer ID (bundle.id is null)");
    }

    log.trace("Extracted transfer ID from deidentified bundle: {}", transferId);
    return new TransportBundle(deidentifiedBundle, transferId);
  }
}
