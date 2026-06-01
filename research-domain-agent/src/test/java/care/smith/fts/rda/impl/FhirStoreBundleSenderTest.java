package care.smith.fts.rda.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;

import care.smith.fts.util.DefaultRetryStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Unit tests for the HDS grouping key derived from the base URL. */
class FhirStoreBundleSenderTest {

  private static String destinationId(String baseUrl) {
    return new FhirStoreBundleSender(
            null, new DefaultRetryStrategy(new SimpleMeterRegistry()), baseUrl, 2)
        .destinationId();
  }

  private static FhirStoreBundleSender sender(ExchangeFunction exchange) {
    var client =
        WebClient.builder().baseUrl("http://blaze/fhir").exchangeFunction(exchange).build();
    return new FhirStoreBundleSender(
        client, new DefaultRetryStrategy(new SimpleMeterRegistry()), "http://blaze/fhir", 2);
  }

  @Test
  void sendPostsTransactionBundleAndReturnsResult() {
    var sender = sender(req -> Mono.just(ClientResponse.create(OK).build()));
    var bundle =
        new Bundle().addEntry(new Bundle.BundleEntryComponent().setResource(new Patient()));

    StepVerifier.create(sender.send(bundle)).expectNextCount(1).verifyComplete();
  }

  @Test
  void lowercasesSchemeAndHostButPreservesPathCase() {
    assertThat(destinationId("HTTP://Blaze:8080/FHIR/Base"))
        .isEqualTo("http://blaze:8080/FHIR/Base");
  }

  @Test
  void stripsTrailingSlashes() {
    assertThat(destinationId("http://blaze:8080/fhir///")).isEqualTo("http://blaze:8080/fhir");
  }

  @Test
  void hostCaseDifferencesShareOneKey() {
    assertThat(destinationId("http://BLAZE/fhir")).isEqualTo(destinationId("http://blaze/fhir"));
  }

  @Test
  void pathCaseDifferencesStayDistinct() {
    assertThat(destinationId("http://blaze/Fhir")).isNotEqualTo(destinationId("http://blaze/fhir"));
  }

  @Test
  void urlWithoutSchemeOrHostIsReturnedTrimmed() {
    // No scheme/host -> the normalisation branch is skipped and the trimmed value is used as-is.
    assertThat(destinationId("fhir-store")).isEqualTo("fhir-store");
  }

  @Test
  void urlWithSchemeButNoHostIsReturnedTrimmed() {
    // Scheme present but no authority/host -> the normalisation branch is skipped, trimmed as-is.
    assertThat(destinationId("file:/var/fhir")).isEqualTo("file:/var/fhir");
  }

  @Test
  void malformedUrlFallsBackToRawValue() {
    // Illegal characters make URI parsing throw; the raw (trimmed) value is used for grouping.
    assertThat(destinationId("http://h o s t/fhir")).isEqualTo("http://h o s t/fhir");
  }
}
