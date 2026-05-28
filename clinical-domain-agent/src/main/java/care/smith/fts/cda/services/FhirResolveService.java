package care.smith.fts.cda.services;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static com.google.common.base.Strings.emptyToNull;
import static java.util.Objects.requireNonNull;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.util.RetryStrategy;
import care.smith.fts.util.error.TransferProcessException;
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

  private static final String RESOLVED_MSG = "Resolved patient {} to FHIR ID {}";
  private static final String FETCH_ERROR_MSG = "Unable to fetch patient identifier from HDS: {}";

  private final WebClient hdsClient;
  private final RetryStrategy retryStrategy;

  public FhirResolveService(WebClient hdsClient, RetryStrategy retryStrategy) {
    this.hdsClient = hdsClient;
    this.retryStrategy = retryStrategy;
  }

  /**
   * Resolves the given patient identifier to the ID of the matching {@link Patient} resource object
   * in the FHIR server accessed with the rest configuration.
   *
   * @param patient the consented patient whose identifier to resolve
   * @return the ID of the FHIR resource
   */
  @Override
  public Mono<IIdType> resolve(ConsentedPatient patient) {
    log.trace("resolve patient identifier {}", patient.identifier());
    return this.resolveFromPatient(patient).map(IBaseResource::getIdElement);
  }

  private Mono<IBaseResource> resolveFromPatient(ConsentedPatient patient) {
    requireNonNull(
        emptyToNull(patient.identifier()), "patient identifier must not be null or empty");
    return fetchPatientBundle(patient)
        .doOnNext(ps -> requireNonNull(ps, "Patient bundle must not be null"))
        .doOnNext(ps -> checkBundleNotEmpty(ps, patient.identifier()))
        .doOnNext(ps -> checkSinglePatient(ps, patient.identifier()))
        .<IBaseResource>map(ps -> ps.getEntryFirstRep().getResource())
        .doOnNext(r -> log.trace(RESOLVED_MSG, patient.identifier(), r.getIdElement().getIdPart()));
  }

  private Mono<Bundle> fetchPatientBundle(ConsentedPatient patient) {
    log.trace("fetchPatientBundle {}", patient);
    return hdsClient
        .get()
        .uri("/Patient", uri -> buildUri(patient, uri))
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .bodyToMono(Bundle.class)
        .retryWhen(retryStrategy.forRequest("fetchPatientBundleResolvePID"))
        .doOnError(e -> log.error(FETCH_ERROR_MSG, e.getMessage()))
        .onErrorResume(
            WebClientException.class,
            e -> Mono.error(new TransferProcessException("Cannot resolve patient identifier", e)));
  }

  private URI buildUri(ConsentedPatient patient, UriBuilder uri) {
    var identifierParam = patient.patientIdentifierSystem() + "|" + patient.identifier();
    log.trace("buildUri: identifier={}", identifierParam);
    return uri.queryParam("identifier", identifierParam).build();
  }

  private void checkSinglePatient(Bundle patients, String patientIdentifier) {
    if (patients.getEntry().size() != 1) {
      var logMsg = "Expected single patient for identifier {}, got {}";
      log.trace(logMsg, patientIdentifier, patients.getEntry().size());
      var errMsg = "Received more then one result while resolving patient identifier %s";
      throw new IllegalStateException(errMsg.formatted(patientIdentifier));
    }
  }

  private void checkBundleNotEmpty(Bundle patients, String patientIdentifier) {
    if (patients.getEntry().isEmpty()) {
      log.trace("Empty bundle for patient identifier {}", patientIdentifier);
      var msg = "Unable to resolve patient identifier %s";
      throw new IllegalStateException(msg.formatted(patientIdentifier));
    }
  }
}
