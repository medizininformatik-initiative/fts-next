package care.smith.fts.cda.services;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static com.google.common.base.Strings.emptyToNull;
import static java.util.Objects.requireNonNull;

import care.smith.fts.util.error.TransferProcessException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Mono;

@Slf4j
public class FhirResolveService implements PatientIdResolver {

  private final WebClient client;
  private final String identifierSystem;

  public FhirResolveService(String identifierSystem, WebClient client) {
    this.identifierSystem = identifierSystem;
    this.client = client;
  }

  /**
   * Resolves the given <code>patientId</code> to the ID of the matching {@link Patient} resource
   * object in the FHIR server accessed with the rest configuration.
   *
   * @param patientId the patient ID (PID) to resolve
   * @return the ID of the FHIR resource
   */
  @Override
  public Mono<IIdType> resolve(String patientId) {
    return this.resolveFromPatient(patientId).map(IBaseResource::getIdElement);
  }

  private Mono<IBaseResource> resolveFromPatient(String patientId) {
    requireNonNull(emptyToNull(patientId), "patientId must not be null or empty");
    return fetchPatientBundle(patientId)
        .doOnNext(ps -> requireNonNull(ps, "Patient bundle must not be null"))
        .doOnNext(ps -> checkBundleNotEmpty(ps, patientId))
        .doOnNext(ps -> checkSinglePatient(ps, patientId))
        .map(ps -> ps.getEntryFirstRep().getResource());
  }

  private Mono<Bundle> fetchPatientBundle(String patientId) {
    return client
        .get()
        .uri(
            uri ->
                uri.pathSegment("Patient")
                    .queryParam("identifier", identifierSystem + "|" + patientId)
                    .build())
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .bodyToMono(Bundle.class)
        .doOnError(e -> log.error(e.getMessage()))
        .onErrorResume(
            WebClientException.class,
            e -> Mono.error(new TransferProcessException("Cannot resolve patient id", e)));
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
