package care.smith.fts.tca.consent;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatient.ConsentedPolicies;
import care.smith.fts.api.Period;
import care.smith.fts.util.FhirUtils;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.hl7.fhir.r4.model.*;

@Slf4j
public class FhirConsentProvider implements ConsentProvider {
  private final String patientIdentifierSystem;
  private final String policySystem;

  private static final int PAGE_SIZE = 100;

  private final CloseableHttpClient httpClient;
  private final PolicyHandler policyHandler;

  FhirConsentProvider(
      CloseableHttpClient httpClient,
      PolicyHandler policyHandler,
      String patientIdentifierSystem,
      String policySystem) {
    this.policyHandler = policyHandler;
    this.httpClient = httpClient;
    this.patientIdentifierSystem = patientIdentifierSystem;
    this.policySystem = policySystem;
  }

  public List<ConsentedPatient> allConsentedPatients(String domain, HashSet<String> policies)
      throws IOException {
    log.debug("Retrieving all patient ids with consent status for Domain '{}'", domain);
    HashSet<String> policiesToCheck = policyHandler.getPoliciesToCheck(policies);
    if (policiesToCheck.isEmpty()) {
      return List.of();
    }

    var bundle = fetchConsentBundleFromGics();
    List<ConsentedPatient> consentedPatients = fetchConsentedPatients(bundle, policiesToCheck);
    log.info("Fetched {} patients", consentedPatients.size());
    return consentedPatients;
  }

  private Bundle fetchConsentBundleFromGics() throws IOException {
    int from = 0;
    var bundle = fetchConsentPageFromGics(from, PAGE_SIZE);

    log.info("total consents: {}", bundle.getTotal());

    while (from + PAGE_SIZE < bundle.getTotal()) {
      from += PAGE_SIZE;
      var nextBundle = fetchConsentPageFromGics(from, from + PAGE_SIZE);
      nextBundle.getEntry().forEach(bundle::addEntry);
    }
    return bundle;
  }

  private Bundle fetchConsentPageFromGics(int from, int to) throws IOException {
    return httpClient.execute(
        httpPost(from, to), r -> FhirUtils.inputStreamToFhirBundle(r.getEntity().getContent()));
  }

  private HttpPost httpPost(int from, int to) {
    HttpPost post = new HttpPost("/$allConsentsForDomain?_count=%s&_offset=%s".formatted(to, from));
    post.setEntity(
        new StringEntity(
            "{\"resourceType\": \"Parameters\", \"parameter\": [{\"name\": \"domain\", \"valueString\": \"MII\"}]}"));
    post.setHeader("Content-Type", "application/json");
    return post;
  }

  private static <T> Stream<T> getResources(Bundle bundle, Class<T> type) {
    return bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(type::isInstance)
        .map(type::cast);
  }

  private List<ConsentedPatient> fetchConsentedPatients(
      Bundle outerBundle, Set<String> policiesToCheck) {
    Stream<ConsentedPatient> consentedPatients =
        getConsentedPatients(getResources(outerBundle, Bundle.class), policiesToCheck);
    return consentedPatients.collect(Collectors.toList());
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
