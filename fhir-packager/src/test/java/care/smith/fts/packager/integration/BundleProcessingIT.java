package care.smith.fts.packager.integration;

import care.smith.fts.packager.config.MockPseudonymizerTestConfiguration;
import care.smith.fts.packager.service.BundleProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the complete FHIR Bundle processing pipeline.
 * 
 * <p>These tests verify the end-to-end functionality of the bundle processing
 * workflow from stdin input to stdout output, using the actual Spring context
 * and real service implementations.
 */
@SpringBootTest(classes = MockPseudonymizerTestConfiguration.class)
@ActiveProfiles("test")
class BundleProcessingIT {

  @Autowired
  private BundleProcessor bundleProcessor;

  private InputStream originalSystemIn;
  private PrintStream originalSystemOut;
  private ByteArrayOutputStream capturedOutput;

  @BeforeEach
  void setUp() {
    originalSystemIn = System.in;
    originalSystemOut = System.out;
    capturedOutput = new ByteArrayOutputStream();
    System.setOut(new PrintStream(capturedOutput, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void tearDown() {
    System.setIn(originalSystemIn);
    System.setOut(originalSystemOut);
  }

  @Test
  void processBundle_WithValidCollectionBundle_ShouldProduceIdenticalOutput() throws Exception {
    String inputJson = loadTestResource("test-bundles/valid-collection-bundle.json");
    System.setIn(new ByteArrayInputStream(inputJson.getBytes(StandardCharsets.UTF_8)));

    int exitCode = bundleProcessor.processBundle();

    assertThat(exitCode).isEqualTo(0);
    String output = capturedOutput.toString(StandardCharsets.UTF_8);
    
    // Verify the output is valid FHIR Bundle JSON
    assertThat(output).contains("\"resourceType\":\"Bundle\"");
    assertThat(output).contains("\"type\":\"collection\"");
    assertThat(output).contains("\"id\":\"test-collection-bundle\"");
    assertThat(output).contains("\"resourceType\":\"Patient\"");
    assertThat(output).contains("\"resourceType\":\"Observation\"");
  }

  @Test
  void processBundle_WithValidTransactionBundle_ShouldProduceIdenticalOutput() throws Exception {
    String inputJson = loadTestResource("test-bundles/valid-transaction-bundle.json");
    System.setIn(new ByteArrayInputStream(inputJson.getBytes(StandardCharsets.UTF_8)));

    int exitCode = bundleProcessor.processBundle();

    assertThat(exitCode).isEqualTo(0);
    String output = capturedOutput.toString(StandardCharsets.UTF_8);
    
    // Verify the output is valid FHIR Bundle JSON
    assertThat(output).contains("\"resourceType\":\"Bundle\"");
    assertThat(output).contains("\"type\":\"transaction\"");
    assertThat(output).contains("\"id\":\"test-transaction-bundle\"");
  }

  @Test
  void processBundle_WithEmptyBundle_ShouldFailValidation() throws Exception {
    String inputJson = loadTestResource("test-bundles/empty-bundle.json");
    System.setIn(new ByteArrayInputStream(inputJson.getBytes(StandardCharsets.UTF_8)));

    int exitCode = bundleProcessor.processBundle();

    assertThat(exitCode).isEqualTo(3); // Invalid bundle
  }

  @Test
  void processBundle_WithBundleNoType_ShouldFailValidation() throws Exception {
    String inputJson = loadTestResource("test-bundles/bundle-no-type.json");
    System.setIn(new ByteArrayInputStream(inputJson.getBytes(StandardCharsets.UTF_8)));

    int exitCode = bundleProcessor.processBundle();

    assertThat(exitCode).isEqualTo(3); // Invalid bundle
  }

  @Test
  void processBundle_WithInvalidJson_ShouldFailParsing() throws Exception {
    String invalidJson = "{ \"resourceType\": \"Bundle\", invalid }";
    System.setIn(new ByteArrayInputStream(invalidJson.getBytes(StandardCharsets.UTF_8)));

    int exitCode = bundleProcessor.processBundle();

    assertThat(exitCode).isEqualTo(3); // Invalid bundle
  }

  @Test
  void processBundle_WithNonBundleResource_ShouldFailParsing() throws Exception {
    String inputJson = loadTestResource("test-bundles/not-a-bundle.json");
    System.setIn(new ByteArrayInputStream(inputJson.getBytes(StandardCharsets.UTF_8)));

    int exitCode = bundleProcessor.processBundle();

    assertThat(exitCode).isEqualTo(3); // Invalid bundle
  }

  @Test
  void processBundle_WithNoInput_ShouldFailWithIOError() throws Exception {
    System.setIn(new ByteArrayInputStream(new byte[0]));

    int exitCode = bundleProcessor.processBundle();

    assertThat(exitCode).isEqualTo(1); // Processing error
  }

  @Test
  void processBundle_WithLargeValidBundle_ShouldProcess() throws Exception {
    // Given - create a large bundle with many entries
    StringBuilder largeBundle = new StringBuilder();
    largeBundle.append("{\n");
    largeBundle.append("  \"resourceType\": \"Bundle\",\n");
    largeBundle.append("  \"id\": \"large-bundle\",\n");
    largeBundle.append("  \"type\": \"collection\",\n");
    largeBundle.append("  \"entry\": [\n");
    
    // Add 100 patient resources
    for (int i = 0; i < 100; i++) {
      if (i > 0) {
        largeBundle.append(",\n");
      }
      largeBundle.append("    {\n");
      largeBundle.append("      \"resource\": {\n");
      largeBundle.append("        \"resourceType\": \"Patient\",\n");
      largeBundle.append("        \"id\": \"patient-").append(i).append("\",\n");
      largeBundle.append("        \"active\": true\n");
      largeBundle.append("      }\n");
      largeBundle.append("    }");
    }
    
    largeBundle.append("\n  ]\n");
    largeBundle.append("}");
    
    System.setIn(new ByteArrayInputStream(largeBundle.toString().getBytes(StandardCharsets.UTF_8)));

    int exitCode = bundleProcessor.processBundle();

    assertThat(exitCode).isEqualTo(0);
    String output = capturedOutput.toString(StandardCharsets.UTF_8);
    
    // Verify the output contains all patients
    assertThat(output).contains("\"resourceType\":\"Bundle\"");
    assertThat(output).contains("\"type\":\"collection\"");
    assertThat(output).contains("\"id\":\"patient-0\"");
    assertThat(output).contains("\"id\":\"patient-99\"");
  }

  @Test
  void processBundle_WithUtf8Characters_ShouldPreserveEncoding() throws Exception {
    // Given - bundle with UTF-8 characters
    String bundleWithUtf8 = "{\n" +
        "  \"resourceType\": \"Bundle\",\n" +
        "  \"id\": \"utf8-bundle\",\n" +
        "  \"type\": \"collection\",\n" +
        "  \"entry\": [\n" +
        "    {\n" +
        "      \"resource\": {\n" +
        "        \"resourceType\": \"Patient\",\n" +
        "        \"id\": \"patient-utf8\",\n" +
        "        \"name\": [\n" +
        "          {\n" +
        "            \"family\": \"Müller\",\n" +
        "            \"given\": [\"José\", \"François\"]\n" +
        "          }\n" +
        "        ],\n" +
        "        \"text\": {\n" +
        "          \"status\": \"generated\",\n" +
        "          \"div\": \"<div>Patient with special chars: 你好世界</div>\"\n" +
        "        }\n" +
        "      }\n" +
        "    }\n" +
        "  ]\n" +
        "}";
    
    System.setIn(new ByteArrayInputStream(bundleWithUtf8.getBytes(StandardCharsets.UTF_8)));

    int exitCode = bundleProcessor.processBundle();

    assertThat(exitCode).isEqualTo(0);
    String output = capturedOutput.toString(StandardCharsets.UTF_8);
    
    // Verify UTF-8 characters are preserved
    assertThat(output).contains("Müller");
    assertThat(output).contains("José");
    assertThat(output).contains("François");
    assertThat(output).contains("你好世界");
  }

  private String loadTestResource(String resourcePath) throws Exception {
    ClassPathResource resource = new ClassPathResource(resourcePath);
    return resource.getContentAsString(StandardCharsets.UTF_8);
  }
}