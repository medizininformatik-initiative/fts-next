package care.smith.fts.cda.services;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static com.google.common.base.Strings.emptyToNull;
import static java.util.Objects.requireNonNull;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.util.error.TransferProcessException;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

@Slf4j
public class FhirResolveService implements PatientIdResolver {

  private final WebClient hdsClient;
  private final MeterRegistry meterRegistry;

  public FhirResolveService(WebClient hdsClient, MeterRegistry meterRegistry) {
    this.hdsClient = hdsClient;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Resolves the given <code>patientId</code> to the ID of the matching {@link Patient} resource
   * object in the FHIR server accessed with the rest configuration.
   *
   * @param patient the patient ID (PID) to resolve
   * @return the ID of the FHIR resource
   */
  @Override
  public Mono<IIdType> resolve(ConsentedPatient patient) {
    return this.resolveFromPatient(patient).map(IBaseResource::getIdElement);
  }

  private Mono<IBaseResource> resolveFromPatient(ConsentedPatient patient) {
    requireNonNull(emptyToNull(patient.id()), "patientId must not be null or empty");
    return fetchPatientBundle(patient)
        .doOnNext(ps -> requireNonNull(ps, "Patient bundle must not be null"))
        .doOnNext(ps -> checkBundleNotEmpty(ps, patient.id()))
        .doOnNext(ps -> checkSinglePatient(ps, patient.id()))
        .map(ps -> ps.getEntryFirstRep().getResource());
  }

  private Mono<Bundle> fetchPatientBundle(ConsentedPatient patient) {
    log.trace("fetchPatientBundle {}", patient);
    return hdsClient
        .get()
        .uri("/Patient", uri -> buildUri(patient, uri))
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .bodyToMono(Bundle.class)
        .retryWhen(defaultRetryStrategy(meterRegistry, "fetchPatientBundleResolvePID"))
        .doOnError(e -> log.error("Unable to fetch patient ID from HDS: {}", e.getMessage()))
        .onErrorResume(
            WebClientException.class,
            e -> Mono.error(new TransferProcessException("Cannot resolve patient id", e)));
  }

  private URI buildUri(ConsentedPatient patient, UriBuilder uri) {
    return uri.queryParam("identifier", patient.patientIdentifierSystem() + "|" + patient.id())
        .build();
  }

  private void checkSinglePatient(Bundle patients, String patientId) {
    if (patients.getEntry().size() != 1) {
      throw new IllegalStateException(
          "Received more then one result while resolving patient ID %s".formatted(patientId));
    }
  }

  private void checkBundleNotEmpty(Bundle patients, String patientId) {
    if (patients.getEntry().isEmpty()) {
      throw new IllegalStateException("Unable to resolve patient ID %s".formatted(patientId));
    }
  }
}
