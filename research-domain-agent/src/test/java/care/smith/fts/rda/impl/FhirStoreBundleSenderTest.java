package care.smith.fts.rda.impl;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.test.MockServerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientSsl;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;

@SpringBootTest
@ExtendWith(MockServerExtension.class)
class FhirStoreBundleSenderTest {

  @Autowired MeterRegistry meterRegistry;
  private WebClient client;

  @BeforeEach
  void setUp(MockServerClient mockServer, @Autowired Builder builder) {
    var server = MockServerUtil.clientConfig(mockServer);
    client = server.createClient(builder, null);
  }

  @Test
  void requestErrors(MockServerClient mockServer) {
    mockServer
        .when(request().withMethod("POST"))
        .respond(response().withStatusCode(BAD_REQUEST.value()));

    var bundleSender = new FhirStoreBundleSender(client, meterRegistry);

    create(bundleSender.send(new Bundle())).expectError().verify();
  }

  @Test
  void bundleSent(MockServerClient mockServer) {
    mockServer
        .when(
            request()
                .withContentType(MediaType.parse(APPLICATION_FHIR_JSON.toString()))
                .withMethod("POST"))
        .respond(response().withStatusCode(OK.value()));
    var bundleSender = new FhirStoreBundleSender(client, meterRegistry);

    create(bundleSender.send(new Bundle())).expectNext(new BundleSender.Result()).verifyComplete();
  }

  @AfterEach
  void tearDown(MockServerClient mockServer) {
    mockServer.reset();
  }
}
