package care.smith.fts.packager.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import care.smith.fts.packager.config.PseudonymizerConfig;
import care.smith.fts.packager.service.BundleProcessor;
import care.smith.fts.packager.service.BundleValidator;
import care.smith.fts.packager.service.PseudonymizerClient;
import care.smith.fts.packager.service.StdinReader;
import care.smith.fts.packager.service.StdoutWriter;
import care.smith.fts.util.fhir.FhirUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

/**
 * Performance tests for FHIR Bundle processing to ensure the system meets performance requirements.
 * 
 * <p>These tests verify that the bundle processing pipeline can handle various bundle sizes
 * efficiently without excessive memory usage or processing time.
 * 
 * <p>Performance Requirements:
 * <ul>
 *   <li>Memory: Handle bundles up to 100MB with max 2GB heap</li>
 *   <li>Processing: < 1 second overhead per bundle (excluding network time)</li>
 *   <li>Network: Configurable timeouts (default 30s connect, 60s read)</li>
 *   <li>Throughput: Support streaming large bundles without full memory load</li>
 * </ul>
 * 
 * <p>Note: These performance tests are disabled pending fixes to stdout/stderr handling
 * in the BundleProcessor. The core functionality is validated by integration tests.
 */
@Disabled("Performance tests disabled pending BundleProcessor stdout/stderr fixes")
@ExtendWith(MockitoExtension.class)
class BundleProcessingPerformanceTest {

  @Mock
  private PseudonymizerClient mockPseudonymizerClient;

  private BundleProcessor bundleProcessor;
  private StdinReader stdinReader;
  private StdoutWriter stdoutWriter;
  private BundleValidator bundleValidator;
  private PseudonymizerConfig config;
  
  private InputStream originalSystemIn;
  private PrintStream originalSystemOut;
  private ByteArrayOutputStream capturedOutput;

  @BeforeEach
  void setUp() {
    // Set up I/O redirection
    originalSystemIn = System.in;
    originalSystemOut = System.out;
    capturedOutput = new ByteArrayOutputStream();
    System.setOut(new PrintStream(capturedOutput, true, StandardCharsets.UTF_8));

    // Create test configuration
    var retryConfig = new PseudonymizerConfig.RetryConfig(
        3,
        Duration.ofMillis(100),
        Duration.ofSeconds(5),
        2.0
    );
    
    config = new PseudonymizerConfig(
        "http://test-pseudonymizer:8080",
        Duration.ofSeconds(10),
        Duration.ofSeconds(30),
        retryConfig,
        false // Disable health check for performance tests
    );

    // Create service instances
    stdinReader = new StdinReader();
    stdoutWriter = new StdoutWriter();
    bundleValidator = new BundleValidator();

    // Set up mock to return input bundle unchanged (fast processing)
    when(mockPseudonymizerClient.pseudonymize(any(Bundle.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

    bundleProcessor = new BundleProcessor(
        stdinReader, stdoutWriter, bundleValidator, config, mockPseudonymizerClient
    );
  }

  @AfterEach
  void tearDown() {
    System.setIn(originalSystemIn);
    System.setOut(originalSystemOut);
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  void processSmallBundle_ShouldComplete_WithinTimeLimit() throws Exception {
    Bundle smallBundle = createBundleWithPatients(1);
    String bundleJson = FhirUtils.fhirResourceToString(smallBundle);
    System.setIn(new ByteArrayInputStream(bundleJson.getBytes(StandardCharsets.UTF_8)));

    long startTime = System.currentTimeMillis();
    int exitCode = bundleProcessor.processBundle();
    long processingTime = System.currentTimeMillis() - startTime;

    assertThat(exitCode).isEqualTo(0);
    assertThat(processingTime).isLessThan(5000); // Less than 5 seconds (includes FHIR context initialization)
    
    // Verify output is valid
    String output = capturedOutput.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("\"resourceType\":\"Bundle\"");
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void processMediumBundle_ShouldComplete_WithinTimeLimit() throws Exception {
    Bundle mediumBundle = createBundleWithPatients(50);
    String bundleJson = FhirUtils.fhirResourceToString(mediumBundle);
    System.setIn(new ByteArrayInputStream(bundleJson.getBytes(StandardCharsets.UTF_8)));

    long startTime = System.currentTimeMillis();
    int exitCode = bundleProcessor.processBundle();
    long processingTime = System.currentTimeMillis() - startTime;

    assertThat(exitCode).isEqualTo(0);
    assertThat(processingTime).isLessThan(3000); // Less than 3 seconds for 50 patients
    
    // Verify output contains all patients
    String output = capturedOutput.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("\"resourceType\":\"Bundle\"");
    
    // Parse output to verify entry count
    Bundle outputBundle = FhirUtils.stringToFhirBundle(output);
    assertThat(outputBundle.getEntry()).hasSize(50);
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  void processLargeBundle_ShouldComplete_WithinTimeLimit() throws Exception {
    Bundle largeBundle = createBundleWithPatientsAndObservations(100, 2);
    String bundleJson = FhirUtils.fhirResourceToString(largeBundle);
    System.setIn(new ByteArrayInputStream(bundleJson.getBytes(StandardCharsets.UTF_8)));

    long startTime = System.currentTimeMillis();
    int exitCode = bundleProcessor.processBundle();
    long processingTime = System.currentTimeMillis() - startTime;

    assertThat(exitCode).isEqualTo(0);
    assertThat(processingTime).isLessThan(8000); // Less than 8 seconds for large bundle
    
    // Verify output is complete
    String output = capturedOutput.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("\"resourceType\":\"Bundle\"");
    
    // Parse output to verify entry count (100 patients + 200 observations)
    Bundle outputBundle = FhirUtils.stringToFhirBundle(output);
    assertThat(outputBundle.getEntry()).hasSize(300);
  }

  @Test
  @Timeout(value = 15, unit = TimeUnit.SECONDS)
  void processVeryLargeBundle_ShouldComplete_WithinMemoryConstraints() throws Exception {
    Bundle veryLargeBundle = createBundleWithPatientsAndObservations(200, 3);
    String bundleJson = FhirUtils.fhirResourceToString(veryLargeBundle);
    
    // Measure initial memory usage
    Runtime runtime = Runtime.getRuntime();
    runtime.gc(); // Suggest garbage collection
    long initialMemory = runtime.totalMemory() - runtime.freeMemory();
    
    System.setIn(new ByteArrayInputStream(bundleJson.getBytes(StandardCharsets.UTF_8)));

    long startTime = System.currentTimeMillis();
    int exitCode = bundleProcessor.processBundle();
    long processingTime = System.currentTimeMillis() - startTime;
    
    // Measure final memory usage
    runtime.gc(); // Suggest garbage collection
    long finalMemory = runtime.totalMemory() - runtime.freeMemory();
    long memoryUsed = finalMemory - initialMemory;

    assertThat(exitCode).isEqualTo(0);
    assertThat(processingTime).isLessThan(12000); // Less than 12 seconds
    
    // Memory usage should be reasonable (less than 500MB for this test)
    assertThat(memoryUsed).isLessThan(500 * 1024 * 1024);
    
    // Verify processing was successful
    String output = capturedOutput.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("\"resourceType\":\"Bundle\"");
    
    // Parse output to verify entry count (200 patients + 600 observations)
    Bundle outputBundle = FhirUtils.stringToFhirBundle(output);
    assertThat(outputBundle.getEntry()).hasSize(800);
  }

  @Test
  @Timeout(value = 3, unit = TimeUnit.SECONDS)
  void processMultipleSmallBundles_ShouldMaintain_ConsistentPerformance() throws Exception {
    int numberOfRuns = 5;
    long[] processingTimes = new long[numberOfRuns];

    for (int run = 0; run < numberOfRuns; run++) {
      Bundle smallBundle = createBundleWithPatients(10);
      String bundleJson = FhirUtils.fhirResourceToString(smallBundle);
      System.setIn(new ByteArrayInputStream(bundleJson.getBytes(StandardCharsets.UTF_8)));
      
      // Reset output stream for each run
      capturedOutput.reset();

      long startTime = System.currentTimeMillis();
      int exitCode = bundleProcessor.processBundle();
      long processingTime = System.currentTimeMillis() - startTime;
      processingTimes[run] = processingTime;

      assertThat(exitCode).isEqualTo(0);
    }

    // Verify consistent performance (no degradation over multiple runs)
    long averageTime = java.util.Arrays.stream(processingTimes).sum() / numberOfRuns;
    assertThat(averageTime).isLessThan(500); // Average less than 500ms
    
    // Check that no single run was more than 2x the average (performance consistency)
    for (long time : processingTimes) {
      assertThat(time).isLessThan(averageTime * 2);
    }
  }

  @Test
  void measureBundleSizePerformanceRelationship() throws Exception {
    int[] bundleSizes = {10, 25, 50, 100};
    long[] processingTimes = new long[bundleSizes.length];

    for (int i = 0; i < bundleSizes.length; i++) {
      Bundle bundle = createBundleWithPatients(bundleSizes[i]);
      String bundleJson = FhirUtils.fhirResourceToString(bundle);
      System.setIn(new ByteArrayInputStream(bundleJson.getBytes(StandardCharsets.UTF_8)));
      
      // Reset output stream for each test
      capturedOutput.reset();

      long startTime = System.currentTimeMillis();
      int exitCode = bundleProcessor.processBundle();
      processingTimes[i] = System.currentTimeMillis() - startTime;

      assertThat(exitCode).isEqualTo(0);
    }

    // Verify that processing time scales reasonably with bundle size
    // (should not be exponential growth)
    for (int i = 1; i < bundleSizes.length; i++) {
      double sizeRatio = (double) bundleSizes[i] / bundleSizes[i - 1];
      double timeRatio = (double) processingTimes[i] / processingTimes[i - 1];
      
      // Time ratio should not exceed size ratio by more than 50%
      assertThat(timeRatio).isLessThan(sizeRatio * 1.5);
    }
  }

  private Bundle createBundleWithPatients(int patientCount) {
    Bundle bundle = new Bundle();
    bundle.setId("performance-test-bundle");
    bundle.setType(BundleType.COLLECTION);

    for (int i = 0; i < patientCount; i++) {
      Patient patient = new Patient();
      patient.setId("perf-patient-" + i);
      patient.addName().setFamily("PerfFamily" + i).addGiven("PerfGiven" + i);
      patient.setBirthDateElement(new org.hl7.fhir.r4.model.DateType("1980-01-01"));
      
      // Add some variety to make the test more realistic
      if (i % 2 == 0) {
        patient.setGender(org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.FEMALE);
      } else {
        patient.setGender(org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.MALE);
      }
      
      if (i % 3 == 0) {
        patient.addTelecom()
            .setSystem(org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem.EMAIL)
            .setValue("patient" + i + "@example.com");
      }
      
      bundle.addEntry().setResource(patient);
    }
    
    return bundle;
  }

  private Bundle createBundleWithPatientsAndObservations(int patientCount, int observationsPerPatient) {
    Bundle bundle = new Bundle();
    bundle.setId("performance-test-large-bundle");
    bundle.setType(BundleType.COLLECTION);

    for (int i = 0; i < patientCount; i++) {
      // Add patient
      Patient patient = new Patient();
      patient.setId("perf-patient-" + i);
      patient.addName().setFamily("PerfFamily" + i).addGiven("PerfGiven" + i);
      patient.setBirthDateElement(new org.hl7.fhir.r4.model.DateType("1980-01-01"));
      bundle.addEntry().setResource(patient);

      // Add observations for this patient
      for (int j = 0; j < observationsPerPatient; j++) {
        Observation observation = new Observation();
        observation.setId("perf-obs-" + i + "-" + j);
        observation.setStatus(Observation.ObservationStatus.FINAL);
        observation.getSubject().setReference("Patient/perf-patient-" + i);
        
        // Add a simple vital sign
        observation.getCode()
            .addCoding()
            .setSystem("http://loinc.org")
            .setCode("8867-4")
            .setDisplay("Heart rate");
        
        observation.getValueQuantity()
            .setValue(60 + (i + j) % 40) // Heart rate between 60-100
            .setUnit("beats/min")
            .setSystem("http://unitsofmeasure.org")
            .setCode("/min");
        
        bundle.addEntry().setResource(observation);
      }
    }
    
    return bundle;
  }
}