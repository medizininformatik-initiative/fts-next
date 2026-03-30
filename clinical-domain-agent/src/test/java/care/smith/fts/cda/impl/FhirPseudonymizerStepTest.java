package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.DateShiftPreserve;
import care.smith.fts.util.tca.TcaDomains;
import care.smith.fts.util.tca.TransportMappingResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class FhirPseudonymizerStepTest {

  @Mock private WebClient fpClient;
  @Mock private WebClient tcaClient;
  @Mock private WebClient.RequestBodyUriSpec fpPostSpec;
  @Mock private WebClient.RequestBodySpec fpBodySpec;
  @Mock private WebClient.RequestHeadersSpec fpHeadersSpec;
  @Mock private WebClient.ResponseSpec fpResponseSpec;
  @Mock private WebClient.RequestBodyUriSpec tcaPostSpec;
  @Mock private WebClient.RequestBodySpec tcaBodySpec;
  @Mock private WebClient.RequestHeadersSpec tcaHeadersSpec;
  @Mock private WebClient.ResponseSpec tcaResponseSpec;

  private FhirPseudonymizerStep step;

  @BeforeEach
  void setUp() {
    var domains = new TcaDomains("pseudo-domain", "salt-domain", "dateshift-domain");
    step =
        new FhirPseudonymizerStep(
            fpClient,
            tcaClient,
            domains,
            Duration.ofDays(14),
            DateShiftPreserve.NONE,
            List.of(),
            new SimpleMeterRegistry());

    // FP client mock chain (lenient: not all tests exercise FP)
    lenient().when(fpClient.post()).thenReturn(fpPostSpec);
    lenient().when(fpPostSpec.uri(any(String.class))).thenReturn(fpBodySpec);
    lenient().when(fpBodySpec.headers(any(Consumer.class))).thenReturn(fpBodySpec);
    lenient().when(fpBodySpec.bodyValue(any())).thenReturn(fpHeadersSpec);
    lenient().when(fpHeadersSpec.retrieve()).thenReturn(fpResponseSpec);

    // TCA client mock chain (lenient: not all tests exercise TCA)
    lenient().when(tcaClient.post()).thenReturn(tcaPostSpec);
    lenient().when(tcaPostSpec.uri(any(String.class))).thenReturn(tcaBodySpec);
    lenient().when(tcaBodySpec.headers(any(Consumer.class))).thenReturn(tcaBodySpec);
    lenient().when(tcaBodySpec.bodyValue(any())).thenReturn(tcaHeadersSpec);
    lenient().when(tcaHeadersSpec.retrieve()).thenReturn(tcaResponseSpec);
  }

  @Test
  void extractTransportIdsFinds32CharBase64UrlIds() {
    var bundle = new Bundle();

    var patient = new Patient();
    patient.setId("AbCdEfGhIjKlMnOpQrStUvWxYz012345"); // 32 chars Base64URL
    bundle.addEntry().setResource(patient);

    var encounter = new Encounter();
    encounter.setId("short-id"); // Not a tID
    bundle.addEntry().setResource(encounter);

    var encounter2 = new Encounter();
    encounter2.setId("a-uuid-that-is-longer-than-32-chars-total"); // Not a 32-char tID
    bundle.addEntry().setResource(encounter2);

    var tIds = FhirPseudonymizerStep.extractTransportIds(bundle);

    assertThat(tIds).containsExactly("AbCdEfGhIjKlMnOpQrStUvWxYz012345");
  }

  @Test
  void extractTransportIdsReturnsEmptyForNoMatches() {
    var bundle = new Bundle();
    var patient = new Patient();
    patient.setId("regular-uuid-id");
    bundle.addEntry().setResource(patient);

    var tIds = FhirPseudonymizerStep.extractTransportIds(bundle);

    assertThat(tIds).isEmpty();
  }

  @Test
  void deidentifyReturnsBundleWithTransferId() {
    var pseudonymizedBundle = new Bundle();
    var pseudoPatient = new Patient();
    pseudoPatient.setId("AbCdEfGhIjKlMnOpQrStUvWxYz012345");
    pseudonymizedBundle.addEntry().setResource(pseudoPatient);

    when(fpResponseSpec.bodyToMono(Bundle.class)).thenReturn(Mono.just(pseudonymizedBundle));
    when(tcaResponseSpec.bodyToMono(TransportMappingResponse.class))
        .thenReturn(Mono.just(new TransportMappingResponse("transfer-id-abc")));

    var patient = new ConsentedPatient("patient-1", "http://system");
    var inputBundle = new Bundle();
    inputBundle.addEntry().setResource(new Patient());

    var result = step.deidentify(new ConsentedPatientBundle(inputBundle, patient));

    StepVerifier.create(result)
        .assertNext(
            transport -> {
              assertThat(transport.transferId()).isEqualTo("transfer-id-abc");
              assertThat(transport.bundle()).isEqualTo(pseudonymizedBundle);
            })
        .verifyComplete();
  }

  @Test
  void deidentifyReturnsEmptyWhenNoMappings() {
    var emptyBundle = new Bundle();

    when(fpResponseSpec.bodyToMono(Bundle.class)).thenReturn(Mono.just(emptyBundle));

    var patient = new ConsentedPatient("patient-1", "http://system");
    var inputBundle = new Bundle();

    var result = step.deidentify(new ConsentedPatientBundle(inputBundle, patient));

    StepVerifier.create(result).verifyComplete();
  }

  @Test
  void extractTransportIdsSkipsNullResource() {
    var bundle = new Bundle();
    bundle.addEntry(); // entry with null resource

    var tIds = FhirPseudonymizerStep.extractTransportIds(bundle);

    assertThat(tIds).isEmpty();
  }

  @Test
  void extractTransportIdsSkipsResourceWithoutId() {
    var bundle = new Bundle();
    var patient = new Patient(); // no ID set
    bundle.addEntry().setResource(patient);

    var tIds = FhirPseudonymizerStep.extractTransportIds(bundle);

    assertThat(tIds).isEmpty();
  }

  @Test
  void extractTransportIdsSkipsNullIdPart() {
    var bundle = new Bundle();
    var patient = new Patient();
    // setId with just a type prefix — getIdPart() returns null
    patient.getIdElement().setValue("Patient/");
    bundle.addEntry().setResource(patient);

    var tIds = FhirPseudonymizerStep.extractTransportIds(bundle);

    assertThat(tIds).isEmpty();
  }

  @Test
  void deidentifyConsolidatesWhenOnlyDateMappingsPresent() {
    // Step with dateShiftPaths so date mappings are produced
    var domains = new TcaDomains("pseudo-domain", "salt-domain", "dateshift-domain");
    var stepWithDatePaths =
        new FhirPseudonymizerStep(
            fpClient,
            tcaClient,
            domains,
            Duration.ofDays(14),
            DateShiftPreserve.NONE,
            List.of("Encounter.period.start"),
            new SimpleMeterRegistry());

    // FP returns a bundle with no tIDs (no 32-char IDs)
    var pseudonymizedBundle = new Bundle();
    var encounter = new Encounter();
    encounter.setId("regular-id");
    pseudonymizedBundle.addEntry().setResource(encounter);

    when(fpResponseSpec.bodyToMono(Bundle.class)).thenReturn(Mono.just(pseudonymizedBundle));
    when(tcaResponseSpec.bodyToMono(TransportMappingResponse.class))
        .thenReturn(Mono.just(new TransportMappingResponse("transfer-id-xyz")));

    // Input bundle has a date that will be nullified
    var inputEncounter = new Encounter();
    var period = new Period();
    period.setStartElement(new DateTimeType("2020-06-15"));
    inputEncounter.setPeriod(period);

    var inputBundle = new Bundle();
    inputBundle.addEntry().setResource(inputEncounter);

    var patient = new ConsentedPatient("patient-1", "http://system");
    var result = stepWithDatePaths.deidentify(new ConsentedPatientBundle(inputBundle, patient));

    StepVerifier.create(result)
        .assertNext(
            transport -> {
              assertThat(transport.transferId()).isEqualTo("transfer-id-xyz");
            })
        .verifyComplete();
  }
}
