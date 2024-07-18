package care.smith.fts.rda.rest.it;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import care.smith.fts.rda.ResearchDomainAgent;
import care.smith.fts.rda.TransferProcessRunner.Phase;
import care.smith.fts.rda.TransferProcessRunner.Status;
import care.smith.fts.rda.rest.it.mock.MockBundleSender;
import care.smith.fts.rda.rest.it.mock.MockDeidentifier;
import care.smith.fts.test.FhirGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.Duration;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/* RDA IT


*/

@Slf4j
@SpringBootTest(classes = ResearchDomainAgent.class, webEnvironment = RANDOM_PORT)
public class TransferProcessControllerIT extends BaseIT {
  protected WebClient client;

  protected final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

  protected final MockDeidentifier mockDeidentifier = new MockDeidentifier(om, tca);
  protected final MockBundleSender mockBundleSender = new MockBundleSender(hds);

  @BeforeEach
  void setUp(@LocalServerPort int port, @Autowired WebClient.Builder builder) {
    client = builder.baseUrl("http://localhost:" + port).build();
  }

  @AfterEach
  void tearDown() {
    resetAll();
  }

  @Test
  void successfulPatientTransfer() throws IOException {
    mockDeidentifier.success();
    mockBundleSender.success();

    var transportBundle =
        (Bundle)
            new FhirGenerator("TransportBundleTemplate.json")
                .generateBundle(1, 1)
                .getEntryFirstRep()
                .getResource();

    log.info("Start process with transport bundle of size {}", transportBundle.getEntry().size());

    startProcess(
        transportBundle,
        Duration.ofSeconds(3),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.COMPLETED);
          assertThat(r.receivedResources()).isEqualTo(366);
          assertThat(r.sentResources()).isEqualTo(1);
        });
  }

  protected void startProcess(
      Bundle bundle, Duration duration, Consumer<Status> assertionConsumer) {
    client
        .post()
        .uri("/api/v2/process/test/patient")
        .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
        .bodyValue(bundle)
        .retrieve()
        .toBodilessEntity()
        .mapNotNull(r -> r.getHeaders().get("Content-Location"))
        .flatMap(
            r ->
                Mono.delay(duration)
                    .flatMap(
                        i -> client.get().uri(r.getFirst()).retrieve().bodyToMono(Status.class)))
        .as(
            response ->
                StepVerifier.create(response).assertNext(assertionConsumer).verifyComplete());
  }
}
