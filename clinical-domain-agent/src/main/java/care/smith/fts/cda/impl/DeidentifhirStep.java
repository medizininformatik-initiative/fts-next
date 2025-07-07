package care.smith.fts.cda.impl;

import static care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils.generateRegistry;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.DateShiftPreserve;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.Deidentificator;
import care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils;
import care.smith.fts.cda.services.deidentifhir.IdatScraper;
import care.smith.fts.util.error.TransferProcessException;
import care.smith.fts.util.tca.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
class DeidentifhirStep implements Deidentificator {
  private final WebClient tcaClient;
  private final TcaDomains domains;
  private final Duration maxDateShift;
  private final DateShiftPreserve preserve;
  private final com.typesafe.config.Config deidentifhirConfig;
  private final com.typesafe.config.Config scraperConfig;
  private final MeterRegistry meterRegistry;

  public DeidentifhirStep(
      WebClient tcaClient,
      TcaDomains domains,
      Duration maxDateShift,
      DateShiftPreserve preserve,
      com.typesafe.config.Config deidentifhirConfig,
      com.typesafe.config.Config scraperConfig,
      MeterRegistry meterRegistry) {
    this.tcaClient = tcaClient;
    this.domains = domains;
    this.maxDateShift = maxDateShift;
    this.preserve = preserve;
    this.deidentifhirConfig = deidentifhirConfig;
    this.scraperConfig = scraperConfig;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Mono<TransportBundle> deidentify(ConsentedPatientBundle bundle) {
    var patient = bundle.consentedPatient();
    var idatScraper = new IdatScraper(scraperConfig, patient);
    var ids = idatScraper.gatherIDs(bundle.bundle());

    return !ids.isEmpty()
        ? fetchTransportMapping(patient, ids)
            .map(
                response -> {
                  var transportMapping = response.transportMapping();
                  var dateShiftValue = response.dateShiftValue();
                  var registry = generateRegistry(patient.id(), transportMapping, dateShiftValue);
                  var deidentified =
                      DeidentifhirUtils.deidentify(
                          deidentifhirConfig,
                          registry,
                          bundle.bundle(),
                          patient.id(),
                          meterRegistry);
                  return new TransportBundle(deidentified, response.transferId());
                })
        : Mono.empty();
  }

  private Mono<TransportMappingResponse> fetchTransportMapping(
      ConsentedPatient patient, Set<String> ids) {
    var request =
        new TransportMappingRequest(
            patient.id(), patient.patientIdentifierSystem(), ids, domains, maxDateShift, preserve);

    log.trace("Fetch transport mapping for {} IDs", ids.size());
    return tcaClient
        .post()
        .uri("/api/v2/cd/transport-mapping")
        .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
        .bodyValue(request)
        .retrieve()
        .onStatus(r -> r.equals(HttpStatus.BAD_REQUEST), DeidentifhirStep::handleBadRequest)
        .bodyToMono(TransportMappingResponse.class)
        .timeout(Duration.ofSeconds(30))
        .retryWhen(defaultRetryStrategy(meterRegistry, "fetchTransportMapping"))
        .doOnError(DeidentifhirStep::handleError);
  }

  private static Mono<Throwable> handleBadRequest(ClientResponse s) {
    return s.bodyToMono(ProblemDetail.class)
        .flatMap(b -> Mono.error(new TransferProcessException(b.getDetail())));
  }

  private static void handleError(Throwable e) {
    log.error("Cannot fetch transport mapping from TCA: {}", e.getMessage());
  }
}
