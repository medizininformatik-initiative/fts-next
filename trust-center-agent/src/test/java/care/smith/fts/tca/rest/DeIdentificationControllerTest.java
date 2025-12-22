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
import java.time.Duration;
import java.util.Map;
import java.util.Set;
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
    var ids = Set.of("id1", "id2");
    var mapName = "transferId";
    var request =
        new TransportMappingRequest(
            "patientId1",
            "patientIdentifierSystem",
            ids,
            Set.of(),
            DEFAULT_DOMAINS,
            ofDays(14),
            DateShiftPreserve.NONE);
    given(mappingProvider.generateTransportMapping(request))
        .willReturn(
            Mono.just(
                new TransportMappingResponse(
                    mapName, Map.of("id1", "tid1", "id2", "tid2"), ofDays(1))));

    create(controller.transportMapping(Mono.just(request)))
        .assertNext(
            r -> {
              assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
              assertThat(r.getBody().dateShiftValue()).isEqualTo(Duration.ofSeconds(86400));
              assertThat(r.getBody().transportMapping())
                  .containsEntry("id1", "tid1")
                  .containsEntry("id2", "tid2");
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
            Set.of("id1"),
            Set.of(),
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
            Set.of("id1"),
            Set.of(),
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
            Set.of(),
            Set.of(),
            DEFAULT_DOMAINS,
            ofDays(14),
            DateShiftPreserve.NONE);
    given(mappingProvider.generateTransportMapping(request))
        .willReturn(Mono.just(new TransportMappingResponse(mapName, Map.of(), ofDays(1))));

    create(controller.transportMapping(Mono.just(request)))
        .assertNext(
            r -> {
              assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
              assertThat(r.getBody().dateShiftValue()).isEqualTo(Duration.ofSeconds(86400));
              assertThat(r.getBody().transportMapping()).isEmpty();
              assertThat(r.getBody().transferId()).isEqualTo("transferId");
            })
        .verifyComplete();
  }

  @Test
  void transportMappingInternalServerError() {
    var ids = Set.of("id1", "id2");
    var request =
        new TransportMappingRequest(
            "id1",
            "patientIdentifierSystem",
            ids,
            Set.of(),
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
                    Map.of("tid-1", "pid1", "tid-2", "pid2"), Duration.ofMillis(12345))));

    create(controller.secureMapping("transferId"))
        .assertNext(
            r -> {
              assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
              var body = r.getBody();
              assertThat(body.tidPidMap())
                  .containsEntry("tid-1", "pid1")
                  .containsEntry("tid-2", "pid2");
              assertThat(body.dateShiftBy()).isEqualTo(Duration.ofMillis(12345));
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
  void secureMappingInvalidDateShiftValueFromKeyValueStore() {
    given(mappingProvider.fetchSecureMapping("transferId"))
        .willReturn(Mono.error(new NumberFormatException("Invalid dateShiftMillis value.")));

    create(controller.secureMapping("transferId"))
        .expectNext(
            ResponseEntity.of(
                    ProblemDetail.forStatusAndDetail(
                        INTERNAL_SERVER_ERROR, "Invalid dateShiftMillis value."))
                .build())
        .verifyComplete();
  }
}
