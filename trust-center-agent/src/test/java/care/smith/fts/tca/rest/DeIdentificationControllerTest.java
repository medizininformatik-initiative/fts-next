package care.smith.fts.tca.rest;

import static java.time.Duration.ofDays;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.DateShiftPreserve;
import care.smith.fts.tca.deidentification.MappingProvider;
import care.smith.fts.util.error.TransferProcessException;
import care.smith.fts.util.error.UnknownDomainException;
import care.smith.fts.util.tca.SecureMappingResponse;
import care.smith.fts.util.tca.TcaDomains;
import care.smith.fts.util.tca.TransportMappingRequest;
import care.smith.fts.util.tca.TransportMappingResponse;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Slf4j
@ExtendWith(MockitoExtension.class)
class DeIdentificationControllerTest {

  private static final TcaDomains DEFAULT_DOMAINS = new TcaDomains("domain", "domain", "domain");

  @Mock MappingProvider mappingProvider;
  private DeIdentificationController controller;

  @BeforeEach
  void setUp() {
    this.controller = new DeIdentificationController(mappingProvider);
  }

  @Test
  void transportMapping() {
    var idMappings = Map.of("patientId1.Patient:id1", "tid1", "patientId1.Patient:id2", "tid2");
    var dateMappings = Map.of("tId1", "2024-03-15");
    var mapName = "transferId";
    var request =
        new TransportMappingRequest(
            "patientId1",
            "patientIdentifierSystem",
            idMappings,
            dateMappings,
            DEFAULT_DOMAINS,
            ofDays(14),
            DateShiftPreserve.NONE);
    given(mappingProvider.generateTransportMapping(request))
        .willReturn(Mono.just(new TransportMappingResponse(mapName)));

    create(controller.transportMapping(Mono.just(request)))
        .assertNext(
            r -> {
              assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
              assertThat(r.getBody().transferId()).isEqualTo("transferId");
            })
        .verifyComplete();
  }

  @Test
  void transportMappingUnknownDomain() {
    var domains = new TcaDomains("unknown domain", "unknown domain", "unknown domain");
    var request =
        new TransportMappingRequest(
            "id1",
            "patientIdentifierSystem",
            Map.of("id1.Patient:id1", "tid1"),
            Map.of(),
            domains,
            ofDays(14),
            DateShiftPreserve.NONE);
    given(mappingProvider.generateTransportMapping(request))
        .willReturn(Mono.error(new UnknownDomainException("unknown domain")));

    create(controller.transportMapping(Mono.just(request)))
        .assertNext(
            r -> {
              assertThat(r.getStatusCode().is4xxClientError()).isTrue();
            })
        .verifyComplete();
  }

  @Test
  void transportMappingIllegalArgumentException() {
    var request =
        new TransportMappingRequest(
            "id1",
            "patientIdentifierSystem",
            Map.of("id1.Patient:id1", "tid1"),
            Map.of(),
            DEFAULT_DOMAINS,
            ofDays(14),
            DateShiftPreserve.NONE);

    given(mappingProvider.generateTransportMapping(request))
        .willReturn(Mono.error(new IllegalArgumentException("Illegal argument")));

    create(controller.transportMapping(Mono.just(request)))
        .assertNext(
            r -> {
              assertThat(r.getStatusCode().is4xxClientError()).isTrue();
            })
        .verifyComplete();
  }

  @Test
  void transportMappingEmptyIds() {
    var mapName = "transferId";
    var request =
        new TransportMappingRequest(
            "patientId1",
            "patientIdentifierSystem",
            Map.of(),
            Map.of(),
            DEFAULT_DOMAINS,
            ofDays(14),
            DateShiftPreserve.NONE);
    given(mappingProvider.generateTransportMapping(request))
        .willReturn(Mono.just(new TransportMappingResponse(mapName)));

    create(controller.transportMapping(Mono.just(request)))
        .assertNext(
            r -> {
              assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
              assertThat(r.getBody().transferId()).isEqualTo("transferId");
            })
        .verifyComplete();
  }

  @Test
  void transportMappingInternalServerError() {
    var idMappings = Map.of("id1.Patient:id1", "tid1", "id1.Patient:id2", "tid2");
    var request =
        new TransportMappingRequest(
            "id1",
            "patientIdentifierSystem",
            idMappings,
            Map.of(),
            DEFAULT_DOMAINS,
            ofDays(14),
            DateShiftPreserve.NONE);
    given(mappingProvider.generateTransportMapping(request))
        .willReturn(Mono.error(new ResponseStatusException(INTERNAL_SERVER_ERROR)));

    create(controller.transportMapping(Mono.just(request)))
        .assertNext(
            r -> {
              assertThat(r.getStatusCode().is5xxServerError()).isTrue();
            })
        .verifyComplete();
  }

  @Test
  void secureMapping() {
    given(mappingProvider.fetchSecureMapping("transferId"))
        .willReturn(
            Mono.just(
                new SecureMappingResponse(
                    Map.of("tid-1", "pid1", "tid-2", "pid2"), Map.of("2024-03-15", "2024-03-20"))));

    create(controller.secureMapping("transferId"))
        .assertNext(
            r -> {
              assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
              var body = r.getBody();
              assertThat(body.tidPidMap())
                  .containsEntry("tid-1", "pid1")
                  .containsEntry("tid-2", "pid2");
              assertThat(body.dateShiftMap()).containsEntry("2024-03-15", "2024-03-20");
            })
        .verifyComplete();
  }

  @Test
  void secureMappingEmpty() {
    given(mappingProvider.fetchSecureMapping("transferId")).willReturn(Mono.empty());

    create(controller.secureMapping("transferId")).verifyComplete();
  }

  @Test
  void secureMappingWithAnyException() {
    given(mappingProvider.fetchSecureMapping("transferId"))
        .willReturn(Mono.error(new TransferProcessException("error message")));

    create(controller.secureMapping("transferId"))
        .expectNext(
            ResponseEntity.of(
                    ProblemDetail.forStatusAndDetail(INTERNAL_SERVER_ERROR, "error message"))
                .build())
        .verifyComplete();
  }

  @Test
  void secureMappingInvalidDataInKeyValueStore() {
    given(mappingProvider.fetchSecureMapping("transferId"))
        .willReturn(Mono.error(new IllegalArgumentException("Invalid data in store.")));

    create(controller.secureMapping("transferId"))
        .expectNext(
            ResponseEntity.of(
                    ProblemDetail.forStatusAndDetail(
                        INTERNAL_SERVER_ERROR, "Invalid data in store."))
                .build())
        .verifyComplete();
  }
}
