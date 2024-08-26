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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

@WebFluxTest(DeIdentificationController.class)
class DeIdentificationControllerTest {

  @MockBean PseudonymProvider pseudonymProvider;
  @MockBean ShiftedDatesProvider shiftedDatesProvider;
  @Autowired WebTestClient webClient;

  @Test
  void getTransportIdsAndDateShiftingValues() {
    var ids = Set.of("id1", "id2");
    var mapName = "tIDMapName";
    given(pseudonymProvider.retrieveTransportIds("patientId1", ids, "domain"))
        .willReturn(Mono.just(Tuples.of(mapName, Map.of("id1", "tid1", "id2", "tid2"))));
    given(shiftedDatesProvider.generateDateShift("patientId1", Duration.ofDays(14)))
        .willReturn(Mono.just(Duration.ofDays(1)));

    var body = new PseudonymizeRequest("patientId1", ids, "domain", Duration.ofDays(14));
    var expectedResponse =
        "{\"tIDMapName\":\"tIDMapName\", \"originalToTransportIDMap\":{\"id1\":\"tid1\",\"id2\":\"tid2\"},\"dateShiftValue\":86400}";
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
    given(pseudonymProvider.retrieveTransportIds("id1", Set.of("id1"), "unknown domain"))
        .willReturn(Mono.error(new UnknownDomainException("unknown domain")));
    given(shiftedDatesProvider.generateDateShift("id1", Duration.ofDays(14)))
        .willReturn(Mono.just(Duration.ofDays(1)));

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
    Set<String> ids = Set.of();
    var mapName = String.valueOf(ids.hashCode());
    given(pseudonymProvider.retrieveTransportIds("id1", ids, "domain"))
        .willReturn(Mono.just(Tuples.of(mapName, Map.of())));
    given(shiftedDatesProvider.generateDateShift("id1", Duration.ofDays(14)))
        .willReturn(Mono.just(Duration.ofDays(1)));

    var body = new PseudonymizeRequest("id1", ids, "domain", Duration.ofDays(14));
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
    given(pseudonymProvider.fetchPseudonymizedIds("tIDMapName"))
        .willReturn(Mono.just(Map.of("tid-1", "pid1", "tid2", "pid2")));

    var expectedResponse = "{\"tid-1\":\"pid1\",\"tid2\":\"pid2\"}";
    webClient
        .post()
        .uri("/api/v2/rd/resolve-pseudonyms")
        .contentType(MediaType.APPLICATION_JSON)
        .body(fromValue("tIDMapName"))
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
    given(pseudonymProvider.fetchPseudonymizedIds("tIDMapName")).willReturn(Mono.empty());

    webClient
        .post()
        .uri("/api/v2/rd/resolve-pseudonyms")
        .contentType(MediaType.APPLICATION_JSON)
        .body(fromValue("tIDMapName"))
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
}
