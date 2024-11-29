package care.smith.fts.rda.rest;

import static care.smith.fts.rda.rest.TransferProcessController.fromPlainBundle;
import static care.smith.fts.util.FhirUtils.resourceStream;
import static care.smith.fts.util.FhirUtils.toBundle;
import static care.smith.fts.util.HeaderTypes.X_PROGRESS;
import static java.util.List.of;
import static java.util.stream.Stream.concat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.rda.TransferProcessConfig;
import care.smith.fts.rda.TransferProcessDefinition;
import care.smith.fts.rda.TransferProcessRunner;
import care.smith.fts.rda.TransferProcessRunner.Phase;
import care.smith.fts.rda.TransferProcessRunner.Status;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

class TransferProcessControllerTest {

  private TransferProcessController api;

  @BeforeEach
  void setUp() {
    api =
        new TransferProcessController(
            new TransferProcessRunner() {
              @Override
              public String start(TransferProcessDefinition process, Mono<TransportBundle> data) {
                return "processId";
              }

              @Override
              public Mono<Status> status(String processId) {
                if ("processId".equals(processId)) {
                  return Mono.just(new Status("processId", Phase.RUNNING, 0, 0));
                } else {
                  return Mono.error(new RuntimeException("error"));
                }
              }
            },
            of(mockTransferProcess()));
  }

  @Test
  void startExistingProjectSucceeds() {

    Bundle bundle =
        concat(
                Stream.of(
                    new Parameters().addParameter("id", "transfer-142601").setId("transfer-id")),
                resourceStream(new Bundle()))
            .collect(toBundle());
    var start =
        api.start(
            "example",
            Mono.just(bundle),
            UriComponentsBuilder.fromUriString("http://localhost:1234"));
    var uri =
        UriComponentsBuilder.fromUriString("http://localhost:1234")
            .path("api/v2/process/status/processId")
            .build()
            .toUri();
    create(start)
        .expectNext(
            ResponseEntity.accepted()
                .headers(h -> h.add("Content-Location", uri.toString()))
                .build())
        .verifyComplete();
  }

  @Test
  void startNonExistingProjectErrors() {
    create(
            api.start(
                "non-existent",
                Mono.just(new Bundle()),
                UriComponentsBuilder.fromUriString("http://localhost:1234")))
        .expectNext(
            ResponseEntity.of(
                    ProblemDetail.forStatusAndDetail(
                        HttpStatus.NOT_FOUND, "Project 'non-existent' could not be found"))
                .build())
        .verifyComplete();
  }

  @Test
  void minimalTransportBundleConversionSucceeds() {
    Bundle bundle =
        Stream.of(
                new Parameters()
                    .addParameter("id", new StringType("transfer-142411"))
                    .setId("transfer-id"))
            .collect(toBundle());

    TransportBundle transportBundle = fromPlainBundle(bundle);
    assertThat(transportBundle.transferId()).isEqualTo("transfer-142411");
    assertThat(transportBundle.bundle().getEntry()).hasSize(0);
  }

  @Test
  void typicalTransportBundleConversionSucceeds() {
    Bundle bundle =
        Stream.of(
                new Parameters()
                    .addParameter("id", new StringType("transfer-142431"))
                    .setId("transfer-id"),
                new Patient(),
                new Observation())
            .collect(toBundle());

    TransportBundle transportBundle = fromPlainBundle(bundle);
    assertThat(transportBundle.transferId()).isEqualTo("transfer-142431");
    assertThat(transportBundle.bundle().getEntry()).hasSize(2);
  }

  @Test
  void unknownTransportBundleConversionParamErrors() {
    Bundle bundle =
        Stream.of(
                new Parameters()
                    .addParameter("unknown", new StringType("transfer-142437"))
                    .setId("transfer-id"))
            .collect(toBundle());

    assertThrows(IllegalArgumentException.class, () -> fromPlainBundle(bundle));
  }

  @Test
  void unknownTransportBundleConversionResourcePassesUntouched() {
    Bundle bundle =
        Stream.of(
                new Parameters()
                    .addParameter("id", new StringType("transfer-142448"))
                    .setId("unknown"))
            .collect(toBundle());

    assertThrows(IllegalArgumentException.class, () -> fromPlainBundle(bundle));
  }

  private static TransferProcessDefinition mockTransferProcess() {
    return new TransferProcessDefinition(
        "example",
        new TransferProcessConfig(null, null),
        (transportBundle) -> null,
        (patientBundle) -> null);
  }

  @Test
  void statusIsRunning() {
    var status = api.status("processId");
    create(status)
        .expectNext(
            ResponseEntity.accepted()
                .headers(
                    h -> {
                      h.add(X_PROGRESS, "Running");
                      h.add(RETRY_AFTER, "3");
                    })
                .body(new Status("processId", Phase.RUNNING, 0, 0)))
        .verifyComplete();
  }
}
