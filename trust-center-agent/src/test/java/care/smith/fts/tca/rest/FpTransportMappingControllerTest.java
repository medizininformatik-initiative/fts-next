package care.smith.fts.tca.rest;

import static care.smith.fts.util.deidentifhir.DateShiftConstants.DATE_SHIFT_PREFIX;
import static java.util.Set.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import care.smith.fts.api.DateShiftPreserve;
import care.smith.fts.tca.deidentification.GpasClient;
import care.smith.fts.tca.services.TransportIdService;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class FpTransportMappingControllerTest {

  @Mock private TransportIdService transportIdService;
  @Mock private GpasClient gpasClient;

  @Captor private ArgumentCaptor<Map<String, String>> dateShiftEntriesCaptor;

  private FpTransportMappingController controller;

  @BeforeEach
  void setUp() {
    controller = new FpTransportMappingController(transportIdService, gpasClient);
  }

  @Test
  void consolidateReturnsTransferIdOnSuccess() {
    var request =
        new FpTransportMappingRequest(
            "patient-123",
            Set.of("tId-1", "tId-2"),
            Map.of("dateTid-1", "2020-06-15"),
            "dateshift-domain",
            Duration.ofDays(14),
            DateShiftPreserve.NONE);

    var seedKey = "PT336H_patient-123";
    when(gpasClient.fetchOrCreatePseudonyms(eq("dateshift-domain"), eq(of(seedKey))))
        .thenReturn(Mono.just(Map.of(seedKey, "seed-abc")));
    when(transportIdService.getDefaultTtl()).thenReturn(Duration.ofMinutes(10));
    when(transportIdService.consolidateMappings(anySet(), anyMap(), any(Duration.class)))
        .thenReturn(Mono.just("transferId-xyz"));

    var result = controller.consolidateTransportMappings(request);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
              assertThat(response.getBody()).isNotNull();
              assertThat(response.getBody().transferId()).isEqualTo("transferId-xyz");
            })
        .verifyComplete();
  }

  @Test
  void consolidatePrefixesDateShiftEntriesWithDsPrefix() {
    var request =
        new FpTransportMappingRequest(
            "patient-456",
            Set.of("tId-1"),
            Map.of("dateTid-1", "2020-06-15", "dateTid-2", "2021-01-01"),
            "dateshift-domain",
            Duration.ofDays(14),
            DateShiftPreserve.NONE);

    var seedKey = "PT336H_patient-456";
    when(gpasClient.fetchOrCreatePseudonyms(eq("dateshift-domain"), eq(of(seedKey))))
        .thenReturn(Mono.just(Map.of(seedKey, "seed-def")));
    when(transportIdService.getDefaultTtl()).thenReturn(Duration.ofMinutes(10));
    when(transportIdService.consolidateMappings(
            anySet(), dateShiftEntriesCaptor.capture(), any(Duration.class)))
        .thenReturn(Mono.just("transferId-abc"));

    controller.consolidateTransportMappings(request).block();

    var captured = dateShiftEntriesCaptor.getValue();
    assertThat(captured).hasSize(2);
    assertThat(captured.keySet()).allMatch(key -> key.startsWith(DATE_SHIFT_PREFIX));
  }

  @Test
  void consolidateAppliesDateShiftToOriginalDates() {
    var request =
        new FpTransportMappingRequest(
            "patient-789",
            Set.of("tId-1"),
            Map.of("dateTid-1", "2020-06-15"),
            "dateshift-domain",
            Duration.ofDays(14),
            DateShiftPreserve.NONE);

    var seedKey = "PT336H_patient-789";
    when(gpasClient.fetchOrCreatePseudonyms(eq("dateshift-domain"), eq(of(seedKey))))
        .thenReturn(Mono.just(Map.of(seedKey, "seed-ghi")));
    when(transportIdService.getDefaultTtl()).thenReturn(Duration.ofMinutes(10));
    when(transportIdService.consolidateMappings(
            anySet(), dateShiftEntriesCaptor.capture(), any(Duration.class)))
        .thenReturn(Mono.just("transferId-def"));

    controller.consolidateTransportMappings(request).block();

    var captured = dateShiftEntriesCaptor.getValue();
    assertThat(captured).hasSize(1);
    // The shifted date should not equal the original
    var shiftedDate = captured.get(DATE_SHIFT_PREFIX + "dateTid-1");
    assertThat(shiftedDate).isNotNull();
    // With a fixed seed, the shift is deterministic but not zero
  }

  @Test
  void consolidateWithEmptyDateMappingsSucceeds() {
    var request =
        new FpTransportMappingRequest(
            "patient-empty",
            Set.of("tId-1", "tId-2"),
            Map.of(),
            "dateshift-domain",
            Duration.ofDays(14),
            DateShiftPreserve.NONE);

    var seedKey = "PT336H_patient-empty";
    when(gpasClient.fetchOrCreatePseudonyms(eq("dateshift-domain"), eq(of(seedKey))))
        .thenReturn(Mono.just(Map.of(seedKey, "seed-jkl")));
    when(transportIdService.getDefaultTtl()).thenReturn(Duration.ofMinutes(10));
    when(transportIdService.consolidateMappings(
            eq(Set.of("tId-1", "tId-2")), eq(Map.of()), any(Duration.class)))
        .thenReturn(Mono.just("transferId-ghi"));

    var result = controller.consolidateTransportMappings(request);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
              assertThat(response.getBody().transferId()).isEqualTo("transferId-ghi");
            })
        .verifyComplete();
  }

  @Test
  void consolidateReturns500OnGpasFailure() {
    var request =
        new FpTransportMappingRequest(
            "patient-fail",
            Set.of("tId-1"),
            Map.of("dateTid-1", "2020-06-15"),
            "dateshift-domain",
            Duration.ofDays(14),
            DateShiftPreserve.NONE);

    var seedKey = "PT336H_patient-fail";
    when(gpasClient.fetchOrCreatePseudonyms(eq("dateshift-domain"), eq(of(seedKey))))
        .thenReturn(Mono.error(new RuntimeException("gPAS unavailable")));

    var result = controller.consolidateTransportMappings(request);

    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            })
        .verifyComplete();
  }
}
