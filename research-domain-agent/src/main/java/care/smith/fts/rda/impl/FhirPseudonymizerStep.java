package care.smith.fts.rda.impl;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.rda.Deidentificator;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * RDA Deidentificator implementation that delegates deidentification to an external FHIR
 * Pseudonymizer service.
 *
 * <p>This implementation provides an alternative to DeidentifhirStep in the Research Domain Agent.
 * It sends bundles containing transport IDs to a local FHIR Pseudonymizer service, which resolves
 * the tIDs to secure pseudonyms (sIDs) via TCA's /rd-agent/fhir endpoint.
 *
 * <p>Architecture:
 *
 * <pre>
 * TransportBundle (with transport IDs)
 *     ↓
 * FhirPseudonymizerStep (this class)
 *     ↓ [HTTP POST /fhir with FHIR Bundle]
 * FHIR Pseudonymizer Service (external, research domain)
 *     ↓ [POST /$create-pseudonym]
 * TCA RdAgentFhirPseudonymizerController
 *     ↓ [tID→sID resolution from Redis]
 * Bundle (with final sIDs)
 * </pre>
 *
 * <p>The returned bundle contains real secure pseudonyms (sIDs) ready for storage in the research
 * FHIR store.
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
   * <p>Sends the TransportBundle to the FHIR Pseudonymizer service, which resolves transport IDs to
   * secure pseudonyms via TCA and returns the final deidentified bundle.
   *
   * @param bundle TransportBundle containing data with transport IDs
   * @return Mono of Bundle with resolved secure pseudonyms (sIDs)
   */
  @Override
  public Mono<Bundle> deidentify(TransportBundle bundle) {
    log.debug(
        "Resolving transport IDs in bundle via FHIR Pseudonymizer, transfer ID: {}",
        bundle.transferId());

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
        .timeout(config.timeout())
        .retryWhen(defaultRetryStrategy(meterRegistry, "rdaFhirPseudonymizerDeidentification"))
        .doOnSuccess(
            result ->
                log.debug(
                    "Successfully resolved transport IDs for transfer ID: {}, bundle entries: {}",
                    bundle.transferId(),
                    result.getEntry().size()))
        .doOnError(
            error ->
                log.error(
                    "Failed to resolve transport IDs for transfer ID {}: {}",
                    bundle.transferId(),
                    error.getMessage()));
  }

  private Bundle parseDeidentifiedBundle(String bundleJson) {
    return fhirContext.newJsonParser().parseResource(Bundle.class, bundleJson);
  }
}
