package care.smith.fts.cda.impl;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;

import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.DateShiftPreserve;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.Deidentificator;
import care.smith.fts.util.fhir.DateShiftAnonymizer;
import care.smith.fts.util.tca.TcaDomains;
import care.smith.fts.util.tca.TransportMappingResponse;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Deidentification step using an external FHIR Pseudonymizer service. Handles date nullification
 * locally, delegates ID pseudonymization to FP (which calls TCA), then consolidates all mappings
 * via TCA.
 */
@Slf4j
class FhirPseudonymizerStep implements Deidentificator {

  private static final Pattern TID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{32}");

  private final WebClient fpClient;
  private final WebClient tcaClient;
  private final TcaDomains domains;
  private final Duration maxDateShift;
  private final DateShiftPreserve preserve;
  private final List<String> dateShiftPaths;
  private final MeterRegistry meterRegistry;

  FhirPseudonymizerStep(
      WebClient fpClient,
      WebClient tcaClient,
      TcaDomains domains,
      Duration maxDateShift,
      DateShiftPreserve preserve,
      List<String> dateShiftPaths,
      MeterRegistry meterRegistry) {
    this.fpClient = fpClient;
    this.tcaClient = tcaClient;
    this.domains = domains;
    this.maxDateShift = maxDateShift;
    this.preserve = preserve;
    this.dateShiftPaths = dateShiftPaths;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Mono<TransportBundle> deidentify(ConsentedPatientBundle bundle) {
    return Mono.defer(
        () -> {
          var patient = bundle.consentedPatient();
          var dateMappings = DateShiftAnonymizer.nullifyDates(bundle.bundle(), dateShiftPaths);

          log.trace(
              "Nullified {} date elements, sending to FHIR Pseudonymizer", dateMappings.size());

          return sendToFhirPseudonymizer(bundle.bundle())
              .flatMap(
                  pseudonymizedBundle -> {
                    var identityTIds = extractTransportIds(pseudonymizedBundle);
                    if (identityTIds.isEmpty() && dateMappings.isEmpty()) {
                      return Mono.empty();
                    }
                    return consolidateViaTca(patient.identifier(), identityTIds, dateMappings)
                        .map(transferId -> new TransportBundle(pseudonymizedBundle, transferId));
                  });
        });
  }

  private Mono<Bundle> sendToFhirPseudonymizer(Bundle bundle) {
    return fpClient
        .post()
        .uri("/$de-identify")
        .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON)))
        .bodyValue(bundle)
        .retrieve()
        .bodyToMono(Bundle.class)
        .timeout(Duration.ofSeconds(60))
        .retryWhen(defaultRetryStrategy(meterRegistry, "sendToFhirPseudonymizer"))
        .doOnError(e -> log.error("FHIR Pseudonymizer call failed: {}", e.getMessage()));
  }

  /**
   * Extracts transport IDs from the pseudonymized bundle. After FP processing, resource IDs that
   * were pseudonymized will be 32-char Base64URL tIDs from TCA.
   */
  static Set<String> extractTransportIds(Bundle bundle) {
    return bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(r -> r != null && r.hasId())
        .map(r -> r.getIdElement().getIdPart())
        .filter(id -> id != null && TID_PATTERN.matcher(id).matches())
        .collect(Collectors.toSet());
  }

  private Mono<String> consolidateViaTca(
      String patientIdentifier, Set<String> identityTIds, Map<String, String> dateMappings) {

    var request =
        new FpTransportMappingRequest(
            patientIdentifier,
            identityTIds,
            dateMappings,
            domains.dateShift(),
            maxDateShift,
            preserve);

    log.trace(
        "Consolidating {} identity tIDs + {} date mappings via TCA",
        identityTIds.size(),
        dateMappings.size());

    return tcaClient
        .post()
        .uri("/api/v2/cd/fhir-pseudonymizer/transport-mapping")
        .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
        .bodyValue(request)
        .retrieve()
        .bodyToMono(TransportMappingResponse.class)
        .timeout(Duration.ofSeconds(30))
        .retryWhen(defaultRetryStrategy(meterRegistry, "consolidateViaTca"))
        .doOnError(e -> log.error("TCA consolidation failed: {}", e.getMessage()))
        .map(TransportMappingResponse::transferId);
  }

  /**
   * DTO matching TCA's FpTransportMappingRequest. Duplicated here to avoid cross-module dependency
   * on the TCA rest package.
   */
  record FpTransportMappingRequest(
      String patientIdentifier,
      Set<String> transportIds,
      Map<String, String> dateMappings,
      String dateShiftDomain,
      Duration maxDateShift,
      DateShiftPreserve dateShiftPreserve) {}
}
