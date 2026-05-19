package care.smith.fts.cda.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static reactor.test.StepVerifier.create;

import care.smith.fts.cda.ClinicalDomainAgent;
import care.smith.fts.cda.TransferProcessRunner;
import care.smith.fts.cda.TransferProcessRunner.Phase;
import care.smith.fts.cda.TransferProcessStatus;
import care.smith.fts.test.TestWebClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@SpringBootTest(classes = ClinicalDomainAgent.class, webEnvironment = RANDOM_PORT)
@Import(TestWebClientFactory.class)
@TestPropertySource(properties = "spring.jackson.time-zone=Europe/Berlin")
class TransferProcessStatusSerializationIT {

  @MockitoBean private TransferProcessRunner processRunner;

  @Autowired private ObjectMapper primaryObjectMapper;

  private WebClient client;

  @BeforeEach
  void setUp(@LocalServerPort int port, @Autowired TestWebClientFactory factory) {
    client = factory.webClient("https://localhost:" + port);
  }

  @Test
  void statusBodySerializesInstantInConfiguredZone() {
    var processId = "iso-test";
    var status =
        new TransferProcessStatus(
            processId,
            Phase.COMPLETED,
            Instant.parse("2026-05-13T03:00:00.818008066Z"),
            Instant.parse("2026-05-13T03:00:10.543337718Z"),
            0,
            0,
            0,
            0,
            0);
    Mockito.when(processRunner.status(processId)).thenReturn(Mono.just(status));

    create(
            client
                .get()
                .uri("/api/v2/process/status/" + processId)
                .retrieve()
                .bodyToMono(String.class))
        .assertNext(
            body -> {
              assertThat(body).contains("\"createdAt\":\"2026-05-13T05:00:00.818+02:00\"");
              assertThat(body).contains("\"finishedAt\":\"2026-05-13T05:00:10.543+02:00\"");
              assertThat(body).doesNotContain("\"createdAt\":[");
              assertThat(body).doesNotContain("\"finishedAt\":[");
            })
        .verifyComplete();
  }

  @Test
  void primaryObjectMapperSerializesInstantWithOffset() throws Exception {
    var status = TransferProcessStatus.create("direct");
    var json = primaryObjectMapper.writeValueAsString(status);
    assertThat(json).doesNotContain("\"createdAt\":[");
    assertThat(json).containsPattern("\"createdAt\":\"[^\"]+\\+\\d\\d:\\d\\d\"");
  }

  @Test
  void statusesBodySerializesInstantInConfiguredZone() {
    var status =
        new TransferProcessStatus(
            "iso-list",
            Phase.RUNNING,
            Instant.parse("2026-05-13T03:00:00.818008066Z"),
            null,
            0,
            0,
            0,
            0,
            0);
    Mockito.when(processRunner.statuses()).thenReturn(Mono.just(List.of(status)));

    create(client.get().uri("/api/v2/process/statuses").retrieve().bodyToMono(String.class))
        .assertNext(
            body -> {
              assertThat(body).contains("\"createdAt\":\"2026-05-13T05:00:00.818+02:00\"");
              assertThat(body).doesNotContain("\"createdAt\":[");
            })
        .verifyComplete();
  }
}
