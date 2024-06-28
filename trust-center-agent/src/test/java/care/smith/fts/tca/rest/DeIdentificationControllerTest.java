package care.smith.fts.tca.rest;

import static org.mockito.BDDMockito.given;
import static org.springframework.web.reactive.function.BodyInserters.fromValue;

import care.smith.fts.tca.deidentification.PseudonymProvider;
import care.smith.fts.tca.deidentification.ShiftedDatesProvider;
import care.smith.fts.util.tca.PseudonymizeRequest;
import care.smith.fts.util.tca.TransportIdsRequest;
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

  //
  //  @Test
  //  void getTransportIdsAndDateShiftingValuesUnknownDomain() {
  //    given(pseudonymProvider.retrieveTransportIds(Set.of("id1"), "unknown domain"))
  //        .willReturn(Mono.just(Map.of("id1", "tid1")));
  //    given(shiftedDatesProvider.generateDateShift(Set.of("id1"), Duration.ofDays(14)))
  //        .willReturn(Mono.just(Map.of("id1", Duration.ofDays(1))));
  //
  //    var body = new PseudonymizeRequest("id1", Set.of("id1"), "unknown domain",
  // Duration.ofDays(14));
  //    webClient
  //        .post()
  //        .uri("/api/v2/cd/transport-ids-and-date-shifting-values")
  //        .contentType(MediaType.APPLICATION_JSON)
  //        .body(fromValue(body))
  //        .accept(MediaType.APPLICATION_JSON)
  //        .exchange()
  //        .expectStatus()
  //        .is5xxServerError();
  //  }

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
    var transportIdsRequest = new TransportIdsRequest("domain", Set.of("tid1", "tid2"));
    given(pseudonymProvider.fetchPseudonymizedIds(transportIdsRequest))
        .willReturn(Mono.just(Map.of("tid1", "pid1", "tid2", "pid2")));

    var expectedResponse = "{\"tid1\":\"pid1\",\"tid2\":\"pid2\"}";
    webClient
        .post()
        .uri("/api/v2/rd/resolve-pseudonyms")
        .contentType(MediaType.APPLICATION_JSON)
        .body(fromValue(transportIdsRequest))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(expectedResponse);
  }

  @Test
  void fetchPseudonymizedIdsEmptyIds() {
    var transportIdsRequest = new TransportIdsRequest("domain", Set.of());
    given(pseudonymProvider.fetchPseudonymizedIds(transportIdsRequest)).willReturn(Mono.empty());

    webClient
        .post()
        .uri("/api/v2/rd/resolve-pseudonyms")
        .contentType(MediaType.APPLICATION_JSON)
        .body(fromValue(transportIdsRequest))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .isEmpty();
  }

  @Disabled
  @Test
  void deleteTransportIds() {
    var transportIdsRequest = new TransportIdsRequest("domain", Set.of("tid1", "tid2"));
    given(pseudonymProvider.deleteTransportIds(transportIdsRequest)).willReturn(Mono.just(2L));

    var expectedResponse = "2";
    webClient
        .post()
        .uri("/api/v2/rd/delete-transport-ids")
        .contentType(MediaType.APPLICATION_JSON)
        .body(fromValue(transportIdsRequest))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(expectedResponse);
  }
}
