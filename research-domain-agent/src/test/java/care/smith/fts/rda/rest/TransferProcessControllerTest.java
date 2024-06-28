package care.smith.fts.rda.rest;

import static care.smith.fts.rda.rest.TransferProcessController.fromPlainBundle;
import static care.smith.fts.util.FhirUtils.toBundle;
import static java.util.List.of;
import static org.assertj.core.api.Assertions.assertThat;
import static reactor.core.publisher.Mono.just;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.rda.TransferProcess;
import care.smith.fts.rda.TransferProcessRunner.Result;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class TransferProcessControllerTest {

  private TransferProcessController api;

  @BeforeEach
  void setUp() {
    api =
        new TransferProcessController((r, p) -> just(new Result(0, 0)), of(mockTransferProcess()));
  }

  @Test
  void startExistingProjectSucceeds() {
    create(api.start("example", Mono.just(new Bundle())))
        .expectNext(new Result(0, 0))
        .verifyComplete();
  }

  @Test
  void startNonExistingProjectErrors() {
    create(api.start("non-existent", Mono.just(new Bundle())))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void minimalTransportBundleConversionSucceeds() {
    Bundle bundle =
        Stream.of(
                new Parameters()
                    .addParameter("transport-id", new StringType("some"))
                    .setId("transport-ids"))
            .collect(toBundle());

    TransportBundle transportBundle = fromPlainBundle(bundle);
    assertThat(transportBundle.transportIds()).containsExactlyInAnyOrder("some");
    assertThat(transportBundle.bundle().getEntry()).hasSize(0);
  }

  @Test
  void typicalTransportBundleConversionSucceeds() {
    Bundle bundle =
        Stream.of(
                new Parameters()
                    .addParameter("transport-id", new StringType("some"))
                    .setId("transport-ids"),
                new Patient(),
                new Observation())
            .collect(toBundle());

    TransportBundle transportBundle = fromPlainBundle(bundle);
    assertThat(transportBundle.transportIds()).containsExactlyInAnyOrder("some");
    assertThat(transportBundle.bundle().getEntry()).hasSize(2);
  }

  @Test
  void unknownTransportBundleConversionParamErrors() {
    Bundle bundle =
        Stream.of(
                new Parameters()
                    .addParameter("unknown", new StringType("some"))
                    .setId("transport-ids"))
            .collect(toBundle());

    TransportBundle transportBundle = fromPlainBundle(bundle);
    assertThat(transportBundle.transportIds()).isEmpty();
    assertThat(transportBundle.bundle().getEntry()).hasSize(0);
  }

  @Test
  void unknownTransportBundleConversionResourcePassesUntouched() {
    Bundle bundle =
        Stream.of(
                new Parameters()
                    .addParameter("transport-ids", new StringType("some"))
                    .setId("unknown"))
            .collect(toBundle());

    TransportBundle transportBundle = fromPlainBundle(bundle);
    assertThat(transportBundle.transportIds()).isEmpty();
    assertThat(transportBundle.bundle().getEntry()).hasSize(1);
  }

  private static TransferProcess mockTransferProcess() {
    return new TransferProcess("example", (transportBundle) -> null, (patientBundle) -> null);
  }
}
