package care.smith.fts.cda.impl;

import static care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils.buildRegistry;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.DateShiftPreserve;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.Deidentificator;
import care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils;
import care.smith.fts.cda.services.deidentifhir.GeneratingReplacementProvider;
import care.smith.fts.util.RetryStrategy;
import care.smith.fts.util.error.TransferProcessException;
import care.smith.fts.util.tca.TcaDomains;
import care.smith.fts.util.tca.TransportMappingRequest;
import care.smith.fts.util.tca.TransportMappingsRequest;
import care.smith.fts.util.tca.TransportMappingsResponse;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
class DeidentifhirStep implements Deidentificator {
  private final WebClient tcaClient;
  private final TcaDomains domains;
  private final Duration maxDateShift;
  private final DateShiftPreserve preserve;
  private final com.typesafe.config.Config config;
  private final MeterRegistry meterRegistry;
  private final RetryStrategy retryStrategy;

  public DeidentifhirStep(
      WebClient tcaClient,
      TcaDomains domains,
      Duration maxDateShift,
      DateShiftPreserve preserve,
      com.typesafe.config.Config config,
      MeterRegistry meterRegistry,
      RetryStrategy retryStrategy) {
    this.tcaClient = tcaClient;
    this.domains = domains;
    this.maxDateShift = maxDateShift;
    this.preserve = preserve;
    this.config = config;
    this.meterRegistry = meterRegistry;
    this.retryStrategy = retryStrategy;
  }

  @Override
  public Mono<TransportBundle> deidentify(ConsentedPatientBundle bundle) {
    return deidentify(List.of(bundle))
        .next()
        .flatMap(
            result ->
                switch (result) {
                  case DeidentificationResult.Success s -> Mono.just(s.bundle());
                  case DeidentificationResult.Failure f -> Mono.error(f.error());
                });
  }

  /**
   * Deidentifies every bundle locally, then sends the transport mappings of all patients to the TCA
   * in a single request so the TCA can batch its gPAS pseudonym lookups. A failure of the local
   * deidentification is isolated per patient; a failure of the TCA request propagates and fails the
   * whole batch.
   */
  @Override
  public Flux<DeidentificationResult> deidentify(List<ConsentedPatientBundle> bundles) {
    return Flux.fromIterable(bundles)
        .concatMap(this::deidentifyLocally)
        .collectList()
        .flatMapMany(this::sendBatch);
  }

  private Mono<LocalOutcome> deidentifyLocally(ConsentedPatientBundle bundle) {
    return Mono.<LocalOutcome>fromCallable(() -> transformLocally(bundle))
        .onErrorResume(e -> Mono.just(new LocalOutcome.Failed(bundle.consentedPatient(), e)));
  }

  private LocalOutcome transformLocally(ConsentedPatientBundle bundle) {
    var patient = bundle.consentedPatient();
    log.trace(
        "deidentify for patient {}, input bundle has {} entries",
        patient.identifier(),
        bundle.bundle().getEntry().size());
    var provider = new GeneratingReplacementProvider(patient.identifier());
    var registry = buildRegistry(provider);
    var deidentified =
        DeidentifhirUtils.deidentify(
            config, registry, bundle.bundle(), patient.identifier(), meterRegistry);
    var idMappings = provider.getIdMappings();
    var dateMappings = provider.getDateMappings();
    log.trace(
        "deidentify produced {} ID mappings and {} date mappings for patient {}",
        idMappings.size(),
        dateMappings.size(),
        patient.identifier());
    if (idMappings.isEmpty() && dateMappings.isEmpty()) {
      log.warn("No mappings to send to TCA");
      return new LocalOutcome.Skipped(patient);
    }
    return new LocalOutcome.Mapped(patient, deidentified, idMappings, dateMappings);
  }

  private Flux<DeidentificationResult> sendBatch(List<LocalOutcome> outcomes) {
    var failures =
        outcomes.stream()
            .filter(LocalOutcome.Failed.class::isInstance)
            .map(LocalOutcome.Failed.class::cast)
            .map(
                f ->
                    (DeidentificationResult)
                        new DeidentificationResult.Failure(f.patient(), f.error()))
            .toList();
    var mapped =
        outcomes.stream()
            .filter(LocalOutcome.Mapped.class::isInstance)
            .map(LocalOutcome.Mapped.class::cast)
            .toList();
    if (mapped.isEmpty()) {
      return Flux.fromIterable(failures);
    }
    var request = new TransportMappingsRequest(mapped.stream().map(this::toRequest).toList());
    return sendMappingsToTca(request)
        .map(response -> toResults(mapped, response, failures))
        .flatMapMany(Flux::fromIterable);
  }

  private TransportMappingRequest toRequest(LocalOutcome.Mapped o) {
    return new TransportMappingRequest(
        o.patient().identifier(),
        o.patient().patientIdentifierSystem(),
        o.idMappings(),
        o.dateMappings(),
        domains,
        maxDateShift,
        preserve);
  }

  private static List<DeidentificationResult> toResults(
      List<LocalOutcome.Mapped> mapped,
      TransportMappingsResponse response,
      List<DeidentificationResult> failures) {
    var transferIds = response.transferIds();
    if (transferIds.size() != mapped.size()) {
      throw new IllegalStateException(
          "TCA returned %d transferIds for %d requests"
              .formatted(transferIds.size(), mapped.size()));
    }
    var results = new ArrayList<>(failures);
    for (var i = 0; i < mapped.size(); i++) {
      var o = mapped.get(i);
      results.add(
          new DeidentificationResult.Success(
              o.patient(), new TransportBundle(o.deidentified(), transferIds.get(i))));
    }
    return results;
  }

  private Mono<TransportMappingsResponse> sendMappingsToTca(TransportMappingsRequest request) {
    log.trace("Send transport mappings for {} requests to TCA", request.requests().size());
    return tcaClient
        .post()
        .uri("/api/v2/cd/transport-mappings")
        .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
        .bodyValue(request)
        .retrieve()
        .onStatus(r -> r.equals(HttpStatus.BAD_REQUEST), DeidentifhirStep::handleBadRequest)
        .bodyToMono(TransportMappingsResponse.class)
        .timeout(Duration.ofSeconds(30))
        .retryWhen(retryStrategy.forRequest("sendMappingsToTca"))
        .doOnError(DeidentifhirStep::handleError);
  }

  private static Mono<Throwable> handleBadRequest(ClientResponse s) {
    return s.bodyToMono(ProblemDetail.class)
        .flatMap(b -> Mono.error(new TransferProcessException(b.getDetail())));
  }

  private static void handleError(Throwable e) {
    log.error("Cannot send transport mappings to TCA: {}", e.getMessage());
  }

  /** Result of the local (non-TCA) deidentification of a single patient bundle. */
  private sealed interface LocalOutcome {
    ConsentedPatient patient();

    /** Local deidentification threw for this patient; isolated from the rest of the batch. */
    record Failed(ConsentedPatient patient, Throwable error) implements LocalOutcome {}

    /** Local deidentification produced no mappings, so there is nothing to send to the TCA. */
    record Skipped(ConsentedPatient patient) implements LocalOutcome {}

    /** Local deidentification produced mappings that must be sent to the TCA. */
    record Mapped(
        ConsentedPatient patient,
        Bundle deidentified,
        Map<String, String> idMappings,
        Map<String, String> dateMappings)
        implements LocalOutcome {}
  }
}
