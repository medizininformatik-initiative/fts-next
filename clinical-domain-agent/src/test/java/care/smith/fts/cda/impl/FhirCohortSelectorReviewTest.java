package care.smith.fts.cda.impl;

import static care.smith.fts.cda.impl.FhirCohortSelectorFactory.consentedPatientExtractor;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpStatus.OK;
import static reactor.test.StepVerifier.withVirtualTime;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.test.FhirCohortGenerator;
import care.smith.fts.util.DefaultRetryStrategy;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.RetryStrategy;
import care.smith.fts.util.fhir.FhirDecoder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

/** Proofs of the findings of the review of PR #2204. */
class FhirCohortSelectorReviewTest {

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

  private static final FhirCohortSelectorConfig CONFIG =
      new FhirCohortSelectorConfig(
          new HttpClientConfig("http://fhir"), PID_SYSTEM, POLICY_SYSTEM, POLICIES);

  private final List<Bundle> pages =
      new FhirCohortGenerator(PID_SYSTEM, POLICY_SYSTEM, POLICIES).generate(5, 1, 1).toList();

  private final AtomicInteger patientRequests = new AtomicInteger();
  private final List<URI> requests = new CopyOnWriteArrayList<URI>();

  /** F1: the timeout sits after retryWhen, so a timed-out attempt is never retried. */
  @Test
  void timedOutRequestIsRetried() {
    var selector =
        selector(
            request ->
                patientRequests.get() == 0 && isPatientSearch(request)
                    ? countPatientRequest(Mono.never())
                    : respond(request),
            new DefaultRetryStrategy(new SimpleMeterRegistry(), true));

    withVirtualTime(() -> selector.selectCohort(List.of()))
        .thenAwait(ofMinutes(1))
        .expectNextCount(5)
        .verifyComplete();
  }

  /** F6 (pre-existing): a comma in an identifier value must be escaped as in FHIR search. */
  @Test
  void commaInIdentifierIsEscaped() {
    var selector = selector(this::respond, name -> Retry.max(0));

    StepVerifier.create(selector.selectCohort(List.of("a,b"))).expectNextCount(0).verifyComplete();

    assertThat(requests.getFirst().getQuery()).contains(PID_SYSTEM + "|a\\,b");
  }

  private FhirCohortSelector selector(
      org.springframework.web.reactive.function.client.ExchangeFunction exchange,
      RetryStrategy retryStrategy) {
    var client =
        WebClient.builder()
            .baseUrl("http://fhir")
            .exchangeStrategies(STRATEGIES)
            .exchangeFunction(exchange)
            .build();
    return new FhirCohortSelector(
        CONFIG,
        client,
        consentedPatientExtractor(CONFIG),
        retryStrategy,
        Schedulers.immediate(),
        1,
        ofSeconds(5));
  }

  private static boolean isPatientSearch(ClientRequest request) {
    return request.url().getPath().endsWith("/Patient");
  }

  private Mono<ClientResponse> countPatientRequest(Mono<ClientResponse> response) {
    patientRequests.incrementAndGet();
    return response;
  }

  private Mono<ClientResponse> respond(ClientRequest request) {
    var uri = request.url();
    requests.add(uri);
    var bundle = isPatientSearch(request) ? patientPage(uri) : consentsOf(uri);
    if (isPatientSearch(request)) {
      patientRequests.incrementAndGet();
    }
    var json = FHIR.newJsonParser().encodeResourceToString(bundle);
    return Mono.just(
        ClientResponse.create(OK, STRATEGIES)
            .header(CONTENT_TYPE, "application/fhir+json")
            .body(json)
            .build());
  }

  private Bundle patientPage(URI uri) {
    var query = uri.getQuery();
    if (query != null && query.startsWith("identifier=")) {
      return new Bundle();
    }
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
