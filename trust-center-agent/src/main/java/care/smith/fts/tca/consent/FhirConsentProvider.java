package care.smith.fts.tca.consent;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatient.ConsentedPolicies;
import care.smith.fts.api.Period;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
public class FhirConsentProvider implements ConsentProvider {
  private final String patientIdentifierSystem;
  private final String policySystem;
  private final int pageSize;

  private final WebClient httpClient;
  private final PolicyHandler policyHandler;

  public FhirConsentProvider(
      WebClient httpClient,
      PolicyHandler policyHandler,
      String patientIdentifierSystem,
      String policySystem,
      int pageSize) {
    this.policyHandler = policyHandler;
    this.httpClient = httpClient;
    this.patientIdentifierSystem = patientIdentifierSystem;
    this.policySystem = policySystem;
    this.pageSize = pageSize;
  }

  @Override
  public Mono<List<ConsentedPatient>> consentedPatientsPage(
      String domain, HashSet<String> policies, int from, int to) {
    HashSet<String> policiesToCheck = policyHandler.getPoliciesToCheck(policies);
    if (policiesToCheck.isEmpty()) {
      return Mono.just(List.of());
    }
    return fetchConsentedPatientsFromGics(policiesToCheck, from, to);
  }

  private Mono<List<ConsentedPatient>> fetchConsentedPatientsFromGics(
      HashSet<String> policiesToCheck, int from, int to) {
    return fetchConsentPageFromGics(from, to)
        .map(b -> extractConsentedPatients(b, policiesToCheck));
  }

  private Mono<Bundle> fetchConsentPageFromGics(int from, int to) {
    return httpClient
        .post()
        .uri("/$allConsentsForDomain?_count=%s&_offset=%s".formatted(to, from))
        .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
        .retrieve()
        .bodyToMono(Bundle.class);
  }

  private List<ConsentedPatient> extractConsentedPatients(
      Bundle outerBundle, Set<String> policiesToCheck) {
    Stream<Bundle> resources = getResources(outerBundle, Bundle.class);
    Stream<ConsentedPatient> consentedPatients = getConsentedPatients(resources, policiesToCheck);
    return consentedPatients.collect(Collectors.toList());
  }

  private static <T> Stream<T> getResources(Bundle bundle, Class<T> type) {
    return bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(type::isInstance)
        .map(type::cast);
  }

  private Stream<ConsentedPatient> getConsentedPatients(
      Stream<Bundle> bundles, Set<String> policiesToCheck) {
    return bundles
        .map(b -> extractConsentedPatient(b, policiesToCheck))
        .filter(Optional::isPresent)
        .map(Optional::get);
  }

  /**
   * Extract the ConsentedPatient from bundle
   *
   * @param bundle the ConsentedPatient is extracted from
   * @param policiesToCheck the policies the patient has to consent
   * @return a {@link ConsentedPatient}, if all policiesToCheck are consented to
   */
  private Optional<ConsentedPatient> extractConsentedPatient(
      Bundle bundle, Set<String> policiesToCheck) {
    Optional<String> optionalPid = getPid(bundle);
    if (optionalPid.isEmpty()) {
      return Optional.empty();
    }
    String pid = optionalPid.get();

    var consentedPolicies = getConsentedPolicies(bundle, policiesToCheck);
    if (consentedPolicies.hasAllPolicies(policiesToCheck)) {
      return Optional.of(new ConsentedPatient(pid, consentedPolicies));
    } else {
      return Optional.empty();
    }
  }

  private Optional<String> getPid(Bundle bundle) {
    return getResources(bundle, Patient.class)
        .flatMap(p -> p.getIdentifier().stream())
        .filter(id -> id.getSystem().equals(patientIdentifierSystem))
        .map(Identifier::getValue)
        .findFirst();
  }

  private ConsentedPolicies getConsentedPolicies(Bundle bundle, Set<String> policiesToCheck) {
    return getPermitProvisionsStream(bundle)
        .map(
            (provisionComponent) ->
                getConsentedPoliciesFromProvision(provisionComponent, policiesToCheck))
        .reduce(
            new ConsentedPolicies(),
            (a, b) -> {
              a.merge(b);
              return a;
            });
  }

  private static Stream<Consent.provisionComponent> getPermitProvisionsStream(Bundle bundle) {
    return getResources(bundle, Consent.class)
        .flatMap(c -> c.getProvision().getProvision().stream());
  }

  private ConsentedPolicies getConsentedPoliciesFromProvision(
      Consent.provisionComponent provisionComponent, Set<String> policiesToCheck) {
    String start = provisionComponent.getPeriod().getStartElement().asStringValue();
    String end = provisionComponent.getPeriod().getEndElement().asStringValue();

    var code = provisionComponent.getCode();
    var consentedPolicies = new ConsentedPolicies();
    code.stream()
        .flatMap(c -> extractPolicyFromCode(policiesToCheck, c))
        .distinct()
        .forEach(p -> consentedPolicies.put(p, Period.parse(start, end)));
    return consentedPolicies;
  }

  private Stream<String> extractPolicyFromCode(Set<String> policiesToCheck, CodeableConcept c) {
    return c.getCoding().stream()
        .filter(coding -> coding.getSystem().equals(policySystem))
        .map(Coding::getCode)
        .filter(policiesToCheck::contains);
  }
}
