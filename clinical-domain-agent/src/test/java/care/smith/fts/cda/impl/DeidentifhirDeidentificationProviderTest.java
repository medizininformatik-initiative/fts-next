package care.smith.fts.cda.impl;

import static org.mockito.Mockito.when;
import static reactor.core.publisher.Flux.fromIterable;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.test.TestPatientGenerator;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

import care.smith.fts.util.tca.PseudonymizeResponse;
import care.smith.fts.util.tca.TransportIDs;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import static reactor.core.publisher.Mono.just;
import static reactor.test.StepVerifier.create;

@ExtendWith(MockServerExtension.class)
class DeidentifhirDeidentificationProviderTest {

  @Mock WebClient webClient;

  DeidentifhirDeidentificationProvider provider;

  @BeforeEach
  void setUp() {
    provider =
        new DeidentifhirDeidentificationProvider(
            new File(
                "src/test/resources/care/smith/fts/cda/services/deidentifhir/CDtoTransport.profile"),
            new File(
                "src/test/resources/care/smith/fts/cda/services/deidentifhir/IDScraper.profile"),
            webClient,
            "domain",
            Duration.ofDays(14));
  }

  @Test
  void deidentify() throws IOException {
    PseudonymizeResponse pseudonymizeResponse =
        new PseudonymizeResponse(new TransportIDs(), Duration.ofDays(1));

    when(webClient.post().retrieve().bodyToMono(PseudonymizeResponse.class))
        .thenReturn(just(pseudonymizeResponse));
    ConsentedPatient consentedPatient =
        new ConsentedPatient("id1", new ConsentedPatient.ConsentedPolicies());
    var bundle = TestPatientGenerator.generateOnePatient("id1", "2024", "identifierSystem");
    Flux<Resource> deidentifiedFlux =
        provider.deidentify(fromIterable(List.of(bundle)), consentedPatient);
    create(deidentifiedFlux).verifyComplete();
  }
}
