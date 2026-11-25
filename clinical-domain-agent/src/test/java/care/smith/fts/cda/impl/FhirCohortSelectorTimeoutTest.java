package care.smith.fts.cda.impl;

import static care.smith.fts.cda.impl.FhirCohortSelectorFactory.consentedPatientExtractor;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpStatus.OK;
import static reactor.test.StepVerifier.withVirtualTime;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.test.FhirCohortGenerator;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.fhir.FhirDecoder;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/**
 * A consumer slower than the timeout of the FHIR requests must not fail the cohort selection, as
 * the selector fetches the next page only on demand.
 */
class FhirCohortSelectorTimeoutTest {

  private static final String PID_SYSTEM =
      "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym";
  private static final String POLICY_SYSTEM =
      "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy";
  private static final Set<String> POLICIES = Set.of("MDAT_erheben");

  private static final FhirContext FHIR = FhirContext.forR4Cached();

  private static final ExchangeStrategies STRATEGIES =
      ExchangeStrategies.builder()
          .codecs(c -> c.customCodecs().register(new FhirDecoder(FHIR)))
          .build();

  private final List<Bundle> pages =
      new FhirCohortGenerator(PID_SYSTEM, POLICY_SYSTEM, POLICIES).generate(5, 1, 1).toList();

  @Test
  void slowConsumerDoesNotTimeOutCohortSelection() {
    var config =
        new FhirCohortSelectorConfig(
            new HttpClientConfig("http://fhir"), PID_SYSTEM, POLICY_SYSTEM, POLICIES);
    var client =
        WebClient.builder()
            .baseUrl("http://fhir")
            .exchangeStrategies(STRATEGIES)
            .exchangeFunction(this::respond)
            .build();
    var selector =
        new FhirCohortSelector(
            config,
            client,
            consentedPatientExtractor(config),
            name -> Retry.max(0),
            Schedulers.immediate(),
            1);

    withVirtualTime(() -> selector.selectCohort(List.of()), 0)
        .thenRequest(1)
        .expectNextCount(1)
        .thenAwait(Duration.ofMinutes(1))
        .thenRequest(Long.MAX_VALUE)
        .expectNextCount(4)
        .verifyComplete();
  }

  private Mono<ClientResponse> respond(ClientRequest request) {
    var uri = request.url();
    var bundle = uri.getPath().endsWith("/Consent") ? consentsOf(uri) : patientPage(uri);
    var json = FHIR.newJsonParser().encodeResourceToString(bundle);
    return Mono.just(
        ClientResponse.create(OK, STRATEGIES)
            .header(CONTENT_TYPE, "application/fhir+json")
            .body(json)
            .build());
  }

  private Bundle patientPage(URI uri) {
    var query = uri.getQuery();
    var index =
        query != null && query.startsWith("_page=") ? Integer.parseInt(query.substring(6)) : 0;
    return pages.get(index);
  }

  private Bundle consentsOf(URI uri) {
    var query = uri.getQuery();
    return pages.stream()
        .filter(page ->
            typedResourceStream(page, Patient.class)
                .flatMap(p -> p.getIdentifier().stream())
                .anyMatch(id -> query.contains(id.getValue())))
        .flatMap(page -> page.getEntry().stream().map(Bundle.BundleEntryComponent::getResource))
        .collect(toBundle());
  }
}
