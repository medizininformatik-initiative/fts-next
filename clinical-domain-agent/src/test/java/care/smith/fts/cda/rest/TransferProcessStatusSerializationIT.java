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
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@SpringBootTest(classes = ClinicalDomainAgent.class, webEnvironment = RANDOM_PORT)
@Import(TestWebClientFactory.class)
class TransferProcessStatusSerializationIT {

  @MockitoBean private TransferProcessRunner processRunner;

  @Autowired private ObjectMapper primaryObjectMapper;

  private WebClient client;

  @BeforeEach
  void setUp(@LocalServerPort int port, @Autowired TestWebClientFactory factory) {
    client = factory.webClient("https://localhost:" + port);
  }

  @Test
  void statusBodySerializesLocalDateTimeAsIsoString() {
    var processId = "iso-test";
    var status =
        new TransferProcessStatus(
            processId,
            Phase.COMPLETED,
            LocalDateTime.of(2026, 5, 13, 3, 0, 0, 818008066),
            LocalDateTime.of(2026, 5, 13, 3, 0, 10, 543337718),
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
              assertThat(body).contains("\"createdAt\":\"2026-05-13T03:00:00.818008066\"");
              assertThat(body).contains("\"finishedAt\":\"2026-05-13T03:00:10.543337718\"");
              assertThat(body).doesNotContain("\"createdAt\":[");
              assertThat(body).doesNotContain("\"finishedAt\":[");
            })
        .verifyComplete();
  }

  @Test
  void primaryObjectMapperSerializesLocalDateTimeAsIsoString() throws Exception {
    var status = TransferProcessStatus.create("direct");
    var json = primaryObjectMapper.writeValueAsString(status);
    assertThat(json).doesNotContain("\"createdAt\":[");
    assertThat(json).contains("\"createdAt\":\"2");
  }

  @Test
  void statusesBodySerializesLocalDateTimeAsIsoString() {
    var status =
        new TransferProcessStatus(
            "iso-list",
            Phase.RUNNING,
            LocalDateTime.of(2026, 5, 13, 3, 0, 0, 818008066),
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
              assertThat(body).contains("\"createdAt\":\"2026-05-13T03:00:00.818008066\"");
              assertThat(body).doesNotContain("\"createdAt\":[");
            })
        .verifyComplete();
  }
}
