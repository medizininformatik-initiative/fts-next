package care.smith.fts.cda.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PatientCompartmentServiceTest {

  private PatientCompartmentService service;

  @BeforeEach
  void setUp() {
    service =
        new PatientCompartmentService(
            Map.of(
                "ServiceRequest", List.of("subject", "performer"),
                "Observation", List.of("subject", "performer"),
                "Organization", List.of(),
                "Medication", List.of()));
  }

  @Test
  void getParamsForResourceType_returnsParams() {
    assertThat(service.getParamsForResourceType("ServiceRequest"))
        .containsExactlyInAnyOrder("subject", "performer");
  }

  @Test
  void getParamsForResourceType_returnsEmptyForNonCompartmentType() {
    assertThat(service.getParamsForResourceType("Organization")).isEmpty();
  }

  @Test
  void getParamsForResourceType_returnsEmptyForUnknownType() {
    assertThat(service.getParamsForResourceType("Unknown")).isEmpty();
  }

  @Test
  void hasCompartmentParams_returnsTrueForCompartmentType() {
    assertThat(service.hasCompartmentParams("ServiceRequest")).isTrue();
  }

  @Test
  void hasCompartmentParams_returnsFalseForNonCompartmentType() {
    assertThat(service.hasCompartmentParams("Organization")).isFalse();
  }

  @Test
  void hasCompartmentParams_returnsFalseForUnknownType() {
    assertThat(service.hasCompartmentParams("Unknown")).isFalse();
  }
}
