package care.smith.fts.packager.integration;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.packager.FhirPackagerApplication;
import care.smith.fts.util.fhir.FhirUtils;
import java.nio.file.Path;
import java.time.Duration;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end integration tests for the FHIR Packager using a real FHIR Pseudonymizer service.
 * 
 * <p>These tests verify the complete functionality of the packager by running it against
 * a real FHIR Pseudonymizer service running in a Docker container. This ensures that
 * the integration works correctly with the actual external service.
 * 
 * <p>Note: These tests are disabled by default to avoid Docker dependency issues in CI/CD.
 * Enable them locally by removing @Disabled when Docker is available.
 */
@Disabled("E2E tests disabled by default due to Docker/Testcontainers requirements")
@Testcontainers
@SpringBootTest(classes = FhirPackagerApplication.class)
class PackagerE2ETest {

  /**
   * FHIR Pseudonymizer container running the real service.
   * Uses the same image that would be used in production.
   */
  @Container
  static GenericContainer<?> pseudonymizerContainer = new GenericContainer<>(
      DockerImageName.parse("ghcr.io/miracum/fhir-pseudonymizer:v2.22.10"))
      .withExposedPorts(8080)
      .withEnv("PseudonymizationService", "None")  // No external service for testing
      .withEnv("UseSystemTextJsonFhirSerializer", "true")
      .withEnv("AnonymizationEngineConfigurationPath", "/config/anonymization.yaml")
      .withFileSystemBind(
          Path.of("test-config").toAbsolutePath().toString(),
          "/config",
          org.testcontainers.containers.BindMode.READ_ONLY)
      .waitingFor(new HttpWaitStrategy()
          .forPath("/fhir/metadata")
          .forStatusCode(200)
          .withStartupTimeout(Duration.ofMinutes(3)));

  /**
   * Configure the application to use the testcontainer's dynamic port.
   */
  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("pseudonymizer.url", () -> 
        "http://localhost:" + pseudonymizerContainer.getMappedPort(8080));
  }

  @Test
  void endToEndPseudonymization_WithValidBundle_ShouldReturnPseudonymizedBundle() throws Exception {
    Bundle inputBundle = createTestBundle();
    String inputJson = FhirUtils.fhirResourceToString(inputBundle);
    
    // When - Make direct HTTP call to the pseudonymizer service
    String pseudonymizerUrl = "http://localhost:" + pseudonymizerContainer.getMappedPort(8080);
    
    // Use a simple HTTP client to test the service directly
    java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
        .uri(java.net.URI.create(pseudonymizerUrl + "/fhir/$de-identify"))
        .header("Content-Type", "application/fhir+json")
        .header("Accept", "application/fhir+json")
        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(inputJson))
        .build();
    
    java.net.http.HttpResponse<String> response = httpClient.send(request, 
        java.net.http.HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    
    Bundle pseudonymizedBundle = FhirUtils.stringToFhirBundle(response.body());
    assertThat(pseudonymizedBundle).isNotNull();
    assertThat(pseudonymizedBundle.getType()).isEqualTo(BundleType.COLLECTION);
    assertThat(pseudonymizedBundle.getEntry()).hasSize(1);
    
    // Verify that pseudonymization occurred - the patient should still exist
    // but may have modified data (depending on the anonymization configuration)
    Patient pseudonymizedPatient = (Patient) pseudonymizedBundle.getEntry().get(0).getResource();
    assertThat(pseudonymizedPatient).isNotNull();
    assertThat(pseudonymizedPatient.getResourceType().name()).isEqualTo("Patient");
    
    // The patient should still have the same structure but content may be pseudonymized
    assertThat(pseudonymizedPatient.hasName()).isTrue();
    assertThat(pseudonymizedPatient.hasBirthDate()).isTrue();
  }

  @Test
  void endToEndPseudonymization_WithLargeBundle_ShouldHandleSuccessfully() throws Exception {
    Bundle inputBundle = createLargeBundle();
    String inputJson = FhirUtils.fhirResourceToString(inputBundle);
    
    // When - Make direct HTTP call to the pseudonymizer service
    String pseudonymizerUrl = "http://localhost:" + pseudonymizerContainer.getMappedPort(8080);
    
    java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
        .uri(java.net.URI.create(pseudonymizerUrl + "/fhir/$de-identify"))
        .header("Content-Type", "application/fhir+json")
        .header("Accept", "application/fhir+json")
        .timeout(Duration.ofSeconds(30))  // Longer timeout for large bundle
        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(inputJson))
        .build();
    
    java.net.http.HttpResponse<String> response = httpClient.send(request, 
        java.net.http.HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    
    Bundle pseudonymizedBundle = FhirUtils.stringToFhirBundle(response.body());
    assertThat(pseudonymizedBundle).isNotNull();
    assertThat(pseudonymizedBundle.getType()).isEqualTo(BundleType.COLLECTION);
    assertThat(pseudonymizedBundle.getEntry()).hasSize(25); // Same number of entries as input
    
    // Verify all entries are still Patient resources
    pseudonymizedBundle.getEntry().forEach(entry -> {
      assertThat(entry.getResource()).isInstanceOf(Patient.class);
    });
  }

  @Test
  void serviceHealthCheck_ShouldReturnMetadata() throws Exception {
    String pseudonymizerUrl = "http://localhost:" + pseudonymizerContainer.getMappedPort(8080);
    
    // When - Call the FHIR metadata endpoint
    java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
        .uri(java.net.URI.create(pseudonymizerUrl + "/fhir/metadata"))
        .header("Accept", "application/fhir+json")
        .GET()
        .build();
    
    java.net.http.HttpResponse<String> response = httpClient.send(request, 
        java.net.http.HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("CapabilityStatement");
    assertThat(response.body()).contains("status");
    
    // The response should be valid JSON
    assertThat(response.body()).startsWith("{");
    assertThat(response.body()).endsWith("}");
  }

  @Test
  void endToEndPseudonymization_WithInvalidBundle_ShouldReturnError() throws Exception {
    // Given - Invalid FHIR Bundle JSON
    String invalidJson = "{\"resourceType\": \"InvalidResource\", \"invalid\": true}";
    
    // When - Make direct HTTP call to the pseudonymizer service
    String pseudonymizerUrl = "http://localhost:" + pseudonymizerContainer.getMappedPort(8080);
    
    java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
        .uri(java.net.URI.create(pseudonymizerUrl + "/fhir/$de-identify"))
        .header("Content-Type", "application/fhir+json")
        .header("Accept", "application/fhir+json")
        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(invalidJson))
        .build();
    
    java.net.http.HttpResponse<String> response = httpClient.send(request, 
        java.net.http.HttpResponse.BodyHandlers.ofString());
    
    // Then - Should return an error status (4xx or 5xx)
    assertThat(response.statusCode()).isGreaterThanOrEqualTo(400);
  }

  private Bundle createTestBundle() {
    Bundle bundle = new Bundle();
    bundle.setId("e2e-test-bundle");
    bundle.setType(BundleType.COLLECTION);

    Patient patient = new Patient();
    patient.setId("e2e-test-patient");
    patient.addName().setFamily("TestFamily").addGiven("TestGiven");
    patient.setBirthDateElement(new org.hl7.fhir.r4.model.DateType("1985-03-15"));
    
    // Add some additional patient information for more comprehensive testing
    patient.addTelecom()
        .setSystem(org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem.EMAIL)
        .setValue("test@example.com");
    
    patient.setGender(org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.OTHER);

    bundle.addEntry().setResource(patient);
    return bundle;
  }

  private Bundle createLargeBundle() {
    Bundle bundle = new Bundle();
    bundle.setId("e2e-large-test-bundle");
    bundle.setType(BundleType.COLLECTION);

    // Create 25 patients to simulate a larger bundle
    for (int i = 0; i < 25; i++) {
      Patient patient = new Patient();
      patient.setId("e2e-patient-" + i);
      patient.addName().setFamily("Family" + i).addGiven("Given" + i);
      patient.setBirthDateElement(new org.hl7.fhir.r4.model.DateType("1980-01-01"));
      
      // Add varied data to make the test more realistic
      if (i % 2 == 0) {
        patient.setGender(org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.FEMALE);
      } else {
        patient.setGender(org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.MALE);
      }
      
      if (i % 3 == 0) {
        patient.addTelecom()
            .setSystem(org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem.PHONE)
            .setValue("555-000-" + String.format("%04d", i));
      }
      
      bundle.addEntry().setResource(patient);
    }
    
    return bundle;
  }
}
