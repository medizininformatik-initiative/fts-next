package care.smith.fts.tca.rest;

import static java.time.Duration.ofDays;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.deidentification.MappingProvider;
import care.smith.fts.util.error.TransferProcessException;
import care.smith.fts.util.error.UnknownDomainException;
import care.smith.fts.util.tca.ResearchMappingResponse;
import care.smith.fts.util.tca.TCADomains;
import care.smith.fts.util.tca.TransportMappingRequest;
import care.smith.fts.util.tca.TransportMappingResponse;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@Slf4j
@ExtendWith(MockitoExtension.class)
class DeIdentificationControllerTest {

  private static final TCADomains DEFAULT_DOMAINS = new TCADomains("domain", "domain", "domain");

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
    var request = new TransportMappingRequest("patientId1", ids, DEFAULT_DOMAINS, ofDays(14));
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
    var domains = new TCADomains("unknown domain", "unknown domain", "unknown domain");
    var request = new TransportMappingRequest("id1", Set.of("id1"), domains, ofDays(14));
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
    var request = new TransportMappingRequest("id1", Set.of("id1"), DEFAULT_DOMAINS, ofDays(14));

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
    var body = new TransportMappingRequest("id1", Set.of(), DEFAULT_DOMAINS, ofDays(14));

    create(controller.transportMapping(Mono.just(body))).verifyComplete();
  }

  @Test
  void transportMappingInternalServerError() {
    var ids = Set.of("id1", "id2");
    var request = new TransportMappingRequest("id1", ids, DEFAULT_DOMAINS, ofDays(14));
    given(mappingProvider.generateTransportMapping(request))
        .willReturn(Mono.error(new InternalServerErrorException("Internal Server Error")));

    create(controller.transportMapping(Mono.just(request)))
        .assertNext(
            r -> {
              assertThat(r.getStatusCode().is5xxServerError()).isTrue();
            })
        .verifyComplete();
  }

  @Test
  void researchMapping() {
    given(mappingProvider.fetchResearchMapping("transferId"))
        .willReturn(
            Mono.just(
                new ResearchMappingResponse(
                    Map.of("tid-1", "pid1", "tid-2", "pid2"), Duration.ofMillis(12345))));

    create(controller.researchMapping("transferId"))
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
  void researchMappingEmpty() {
    given(mappingProvider.fetchResearchMapping("transferId")).willReturn(Mono.empty());

    create(controller.researchMapping("transferId")).verifyComplete();
  }

  @Test
  void researchMappingWithAnyException() {
    given(mappingProvider.fetchResearchMapping("transferId"))
        .willReturn(Mono.error(new TransferProcessException("")));

    create(controller.researchMapping("transferId"))
        .expectError(TransferProcessException.class)
        .verify();
  }
}
