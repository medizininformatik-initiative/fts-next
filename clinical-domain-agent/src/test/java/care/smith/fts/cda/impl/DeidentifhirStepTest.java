package care.smith.fts.cda.impl;

import static care.smith.fts.util.auth.HTTPClientAuthMethod.AuthMethod.NONE;
import static com.typesafe.config.ConfigFactory.parseResources;
import static java.time.Duration.ofDays;
import static org.mockserver.matchers.MatchType.ONLY_MATCHING_FIELDS;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static reactor.core.publisher.Flux.fromIterable;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.cda.services.deidentifhir.ConsentedPatientBundle;
import care.smith.fts.cda.services.deidentifhir.DeidentifhirUtil;
import care.smith.fts.test.TestPatientGenerator;
import care.smith.fts.util.HTTPClientConfig;
import com.typesafe.config.Config;
import java.io.IOException;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@ExtendWith(MockServerExtension.class)
class DeidentifhirStepTest {

  private DeidentifhirStep step;

  @BeforeEach
  void setUp(MockServerClient mockServer) {
    Config scraperConfig = parseResources(DeidentifhirUtil.class, "IDScraper.profile");
    Config deidentifhirConfig = parseResources(DeidentifhirUtil.class, "CDtoTransport.profile");
    var address = "http://localhost:%d".formatted(mockServer.getPort());
    var server = new HTTPClientConfig(address, NONE);

    step =
        new DeidentifhirStep(
            server.createClient(WebClient.builder()),
            "domain",
            ofDays(14),
            deidentifhirConfig,
            scraperConfig);
  }

  @Test
  void deidentify(MockServerClient mockServer) throws IOException {
    mockServer
        .when(
            request()
                .withMethod("POST")
                .withPath("/api/v2/process/example")
                .withBody(
                    json(
                        """
                                {"transportIds": {}, "duration": "5y"}
                                """,
                        ONLY_MATCHING_FIELDS)))
        .respond(response().withStatusCode(201));

    ConsentedPatient consentedPatient = new ConsentedPatient("id1");
    var bundle = TestPatientGenerator.generateOnePatient("id1", "2024", "identifierSystem");
    Flux<ConsentedPatientBundle<Bundle>> bundleFlux =
        fromIterable(List.of(new ConsentedPatientBundle<>(bundle, consentedPatient)));
    Flux<TransportBundle<Bundle>> deidentifiedFlux = step.deidentify(bundleFlux);
    create(deidentifiedFlux).verifyComplete();
  }
}
