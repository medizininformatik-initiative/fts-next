package care.smith.fts.rda.impl;

import static care.smith.fts.test.WebClientTestUtil.matchRequest;
import static care.smith.fts.util.auth.HTTPClientAuthMethod.AuthMethod.NONE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.web.reactive.function.client.WebClient.builder;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.util.HTTPClientConfig;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class FhirStoreBundleSenderTest {

  private final HTTPClientConfig server = new HTTPClientConfig("http://localhost", NONE);
  private final FhirStoreBundleSenderConfig config =
      new FhirStoreBundleSenderConfig(server, "example");

  @Test
  void requestErrors() {
    var client =
        builder()
            .exchangeFunction(
                matchRequest(HttpMethod.POST)
                    .willRespond(ClientResponse.create(BAD_REQUEST).build()));
    var bundleSender = new FhirStoreBundleSender(config, config.server().createClient(client));

    create(bundleSender.send(Mono.just(new Bundle()))).expectError().verify();
  }

  @Test
  void bundleSent() {
    var client =
        builder()
            .exchangeFunction(
                matchRequest(HttpMethod.POST).willRespond(ClientResponse.create(OK).build()));
    var bundleSender = new FhirStoreBundleSender(config, config.server().createClient(client));

    create(bundleSender.send(Mono.just(new Bundle())))
        .expectNext(new BundleSender.Result())
        .verifyComplete();
  }
}
