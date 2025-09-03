package care.smith.fts.packager.integration;

import care.smith.fts.packager.cli.PackagerCommand;
import care.smith.fts.packager.config.MockPseudonymizerTestConfiguration;
import care.smith.fts.packager.service.StdinReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the PackagerCommand with complete CLI workflow.
 * 
 * <p>These tests verify the end-to-end functionality of the CLI command
 * including argument parsing, configuration validation, and bundle processing.
 */
@SpringBootTest(classes = MockPseudonymizerTestConfiguration.class)
@ActiveProfiles("test")
class PackagerCommandIT {

  @Autowired
  private PackagerCommand packagerCommand;

  @MockBean
  private StdinReader stdinReader;

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
  void call_WithValidBundleInput_ShouldProcessSuccessfully() throws Exception {
    String inputJson = loadTestResource("test-bundles/valid-collection-bundle.json");
    when(stdinReader.readFromStdin()).thenReturn(inputJson);

    Integer exitCode = packagerCommand.call();

    assertThat(exitCode).isEqualTo(0);
    String output = capturedOutput.toString(StandardCharsets.UTF_8);
    
    // Verify the output is valid FHIR Bundle JSON
    assertThat(output).contains("\"resourceType\":\"Bundle\"");
    assertThat(output).contains("\"type\":\"collection\"");
  }

  @Test
  void call_WithInvalidBundleInput_ShouldReturnErrorCode() throws Exception {
    String invalidJson = "{ \"resourceType\": \"Bundle\", invalid }";
    when(stdinReader.readFromStdin()).thenReturn(invalidJson);

    Integer exitCode = packagerCommand.call();

    assertThat(exitCode).isEqualTo(3); // Invalid bundle
  }

  @Test
  void call_WithEmptyInput_ShouldReturnErrorCode() throws Exception {
    when(stdinReader.readFromStdin()).thenReturn("");

    Integer exitCode = packagerCommand.call();

    assertThat(exitCode).isEqualTo(1); // Processing error
  }

  @Test
  void call_WithNonBundleResource_ShouldReturnErrorCode() throws Exception {
    String inputJson = loadTestResource("test-bundles/not-a-bundle.json");
    when(stdinReader.readFromStdin()).thenReturn(inputJson);

    Integer exitCode = packagerCommand.call();

    assertThat(exitCode).isEqualTo(3); // Invalid bundle
  }

  @Test
  void call_WithEmptyBundle_ShouldReturnErrorCode() throws Exception {
    String inputJson = loadTestResource("test-bundles/empty-bundle.json");
    when(stdinReader.readFromStdin()).thenReturn(inputJson);

    Integer exitCode = packagerCommand.call();

    assertThat(exitCode).isEqualTo(3); // Invalid bundle
  }

  @Test
  void call_WithBundleNoType_ShouldReturnErrorCode() throws Exception {
    String inputJson = loadTestResource("test-bundles/bundle-no-type.json");
    when(stdinReader.readFromStdin()).thenReturn(inputJson);

    Integer exitCode = packagerCommand.call();

    assertThat(exitCode).isEqualTo(3); // Invalid bundle
  }

  @Test
  void call_WithValidTransactionBundle_ShouldProcessSuccessfully() throws Exception {
    String inputJson = loadTestResource("test-bundles/valid-transaction-bundle.json");
    when(stdinReader.readFromStdin()).thenReturn(inputJson);

    Integer exitCode = packagerCommand.call();

    assertThat(exitCode).isEqualTo(0);
    String output = capturedOutput.toString(StandardCharsets.UTF_8);
    
    // Verify the output is valid FHIR Bundle JSON
    assertThat(output).contains("\"resourceType\":\"Bundle\"");
    assertThat(output).contains("\"type\":\"transaction\"");
  }

  @Test
  void call_IdentityTransform_ShouldProduceEquivalentOutput() throws Exception {
    String inputJson = loadTestResource("test-bundles/valid-collection-bundle.json");
    when(stdinReader.readFromStdin()).thenReturn(inputJson);

    Integer exitCode = packagerCommand.call();

    assertThat(exitCode).isEqualTo(0);
    String output = capturedOutput.toString(StandardCharsets.UTF_8);
    
    // Verify that essential structure is preserved (identity transform)
    assertThat(output).contains("\"id\":\"test-collection-bundle\"");
    assertThat(output).contains("\"total\":2");
    assertThat(output).contains("\"id\":\"patient-1\"");
    assertThat(output).contains("\"id\":\"observation-1\"");
    assertThat(output).contains("\"family\":\"Doe\"");
    assertThat(output).contains("\"given\":[\"John\"]");
  }

  private String loadTestResource(String resourcePath) throws Exception {
    ClassPathResource resource = new ClassPathResource(resourcePath);
    return resource.getContentAsString(StandardCharsets.UTF_8);
  }
}