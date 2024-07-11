package care.smith.fts.tca.rest;

import static org.mockito.BDDMockito.given;
import static org.springframework.web.reactive.function.BodyInserters.fromValue;

import care.smith.fts.tca.deidentification.PseudonymProvider;
import care.smith.fts.tca.deidentification.ShiftedDatesProvider;
import care.smith.fts.util.error.UnknownDomainException;
import care.smith.fts.util.tca.PseudonymizeRequest;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(DeIdentificationController.class)
class DeIdentificationControllerTest {

  @MockBean PseudonymProvider pseudonymProvider;
  @MockBean ShiftedDatesProvider shiftedDatesProvider;
  @Autowired WebTestClient webClient;

  @Test
  void getTransportIdsAndDateShiftingValues() {
    given(pseudonymProvider.retrieveTransportIds(Set.of("id1", "id2"), "domain"))
        .willReturn(Mono.just(Map.of("id1", "tid1", "id2", "tid2")));
    given(shiftedDatesProvider.generateDateShift(Set.of("patientId1"), Duration.ofDays(14)))
        .willReturn(Mono.just(Map.of("patientId1", Duration.ofDays(1))));

    var body =
        new PseudonymizeRequest("patientId1", Set.of("id1", "id2"), "domain", Duration.ofDays(14));
    var expectedResponse =
        "{\"idMap\":{\"id1\":\"tid1\",\"id2\":\"tid2\"},\"dateShiftValue\":86400}";
    webClient
        .post()
        .uri("/api/v2/cd/transport-ids-and-date-shifting-values")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(expectedResponse);
  }

  @Test
  void getTransportIdsAndDateShiftingValuesUnknownDomain() {
    given(pseudonymProvider.retrieveTransportIds(Set.of("id1"), "unknown domain"))
        .willReturn(Mono.error(new UnknownDomainException("unknown domain")));
    given(shiftedDatesProvider.generateDateShift(Set.of("id1"), Duration.ofDays(14)))
        .willReturn(Mono.just(Map.of("id1", Duration.ofDays(1))));

    var body = new PseudonymizeRequest("id1", Set.of("id1"), "unknown domain", Duration.ofDays(14));
    webClient
        .post()
        .uri("/api/v2/cd/transport-ids-and-date-shifting-values")
        .contentType(MediaType.APPLICATION_JSON)
        .body(fromValue(body))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .is4xxClientError();
  }

  @Test
  void getTransportIdsAndDateShiftingValuesEmptyIds() {
    given(pseudonymProvider.retrieveTransportIds(Set.of(), "domain"))
        .willReturn(Mono.just(Map.of()));
    given(shiftedDatesProvider.generateDateShift(Set.of(), Duration.ofDays(14)))
        .willReturn(Mono.just(Map.of("id1", Duration.ofDays(1))));

    var body = new PseudonymizeRequest("id1", Set.of(), "domain", Duration.ofDays(14));
    webClient
        .post()
        .uri("/api/v2/cd/transport-ids-and-date-shifting-values")
        .contentType(MediaType.APPLICATION_JSON)
        .body(fromValue(body))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .isEmpty();
  }

  @Test
  void fetchPseudonymizedIds() {
    var ids = Set.of("tid-1", "tid2");
    given(pseudonymProvider.fetchPseudonymizedIds(ids))
        .willReturn(Mono.just(Map.of("tid-1", "pid1", "tid2", "pid2")));

    var expectedResponse = "{\"tid-1\":\"pid1\",\"tid2\":\"pid2\"}";
    webClient
        .post()
        .uri("/api/v2/rd/resolve-pseudonyms")
        .contentType(MediaType.APPLICATION_JSON)
        .body(fromValue(ids))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(expectedResponse);
  }

  @Test
  void fetchPseudonymizedIdsEmptyIds() {
    Set<String> ids = Set.of();
    given(pseudonymProvider.fetchPseudonymizedIds(ids)).willReturn(Mono.empty());

    webClient
        .post()
        .uri("/api/v2/rd/resolve-pseudonyms")
        .contentType(MediaType.APPLICATION_JSON)
        .body(fromValue(ids))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .isEmpty();
  }

  @Test
  void rejectInvalidIds() {
    webClient
        .post()
        .uri("/api/v2/rd/resolve-pseudonyms")
        .contentType(MediaType.APPLICATION_JSON)
        .body(fromValue(Set.of("username=Guest'%0AUser:'Admin")))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .is5xxServerError();
  }

  @Disabled
  @Test
  void deleteTransportIds() {
    var ids = Set.of("tid1", "tid2");
    given(pseudonymProvider.deleteTransportIds(ids)).willReturn(Mono.just(2L));

    var expectedResponse = "2";
    webClient
        .post()
        .uri("/api/v2/rd/delete-transport-ids")
        .contentType(MediaType.APPLICATION_JSON)
        .body(fromValue(ids))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(expectedResponse);
  }
}
