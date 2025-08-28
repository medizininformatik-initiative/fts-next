package care.smith.fts.packager.service;

import care.smith.fts.packager.config.PseudonymizerConfig;
import care.smith.fts.packager.service.BundleProcessor.BundleProcessingException;
import care.smith.fts.packager.service.BundleValidator.BundleValidationException;
import care.smith.fts.packager.service.BundleValidator.ValidationMode;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import reactor.core.publisher.Mono;

/**
 * Unit tests for BundleProcessor service.
 */
@ExtendWith(MockitoExtension.class)
class BundleProcessorTest {

  @Mock
  private StdinReader stdinReader;

  @Mock
  private StdoutWriter stdoutWriter;

  @Mock
  private BundleValidator bundleValidator;

  @Mock
  private PseudonymizerConfig config;

  @Mock
  private PseudonymizerClient pseudonymizerClient;

  private BundleProcessor bundleProcessor;
  private InputStream originalSystemIn;
  private PrintStream originalSystemOut;
  private ByteArrayOutputStream capturedOutput;

  @BeforeEach
  void setUp() {
    bundleProcessor = new BundleProcessor(stdinReader, stdoutWriter, bundleValidator, config, pseudonymizerClient);
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
  void processBundle_WithValidInput_ShouldReturnSuccessExitCode() throws Exception {
    String validJson = loadTestResource("test-bundles/valid-collection-bundle.json");
    when(stdinReader.readFromStdin()).thenReturn(validJson);
    
    // Mock the PseudonymizerConfig and Client for this test
    when(config.url()).thenReturn("http://localhost:8080");
    when(config.healthCheckEnabled()).thenReturn(false);
    Bundle mockBundle = createValidBundle();
    when(pseudonymizerClient.pseudonymize(any(Bundle.class))).thenReturn(Mono.just(mockBundle));

    int result = bundleProcessor.processBundle();

    assertThat(result).isEqualTo(0); // Success
    verify(stdinReader).readFromStdin();
    verify(bundleValidator).validateBundle(any(Bundle.class), eq(ValidationMode.STRICT));
    verify(stdoutWriter).writeToStdout(anyString());
  }

  @Test
  void processBundle_WithEmptyInput_ShouldReturnErrorExitCode() throws Exception {
    when(stdinReader.readFromStdin()).thenReturn("");

    int result = bundleProcessor.processBundle();

    assertThat(result).isEqualTo(1); // Processing error
  }

  @Test
  void processBundle_WithIOException_ShouldReturnErrorExitCode() throws Exception {
    when(stdinReader.readFromStdin()).thenThrow(new IOException("Read failed"));

    int result = bundleProcessor.processBundle();

    assertThat(result).isEqualTo(1); // Processing error
  }

  @Test
  void processBundle_WithInvalidBundle_ShouldReturnInvalidBundleExitCode() throws Exception {
    String validJson = loadTestResource("test-bundles/valid-collection-bundle.json");
    when(stdinReader.readFromStdin()).thenReturn(validJson);
    doThrow(new BundleValidationException("Invalid bundle"))
        .when(bundleValidator).validateBundle(any(Bundle.class), any(ValidationMode.class));

    int result = bundleProcessor.processBundle();

    assertThat(result).isEqualTo(3); // Invalid bundle
  }

  @Test
  void readFromStdin_WithValidInput_ShouldReturnContent() throws Exception {
    String input = "test content";
    when(stdinReader.readFromStdin()).thenReturn(input);

    String result = bundleProcessor.readFromStdin();

    assertThat(result).isEqualTo("test content");
  }

  @Test
  void readFromStdin_WithEmptyInput_ShouldThrowException() throws Exception {
    when(stdinReader.readFromStdin()).thenReturn("   ");

    assertThatThrownBy(() -> bundleProcessor.readFromStdin())
        .isInstanceOf(IOException.class)
        .hasMessage("No input data received from stdin");
  }

  @Test
  void parseBundle_WithValidJson_ShouldReturnBundle() throws Exception {
    String validJson = loadTestResource("test-bundles/valid-collection-bundle.json");

    Bundle result = bundleProcessor.parseBundle(validJson);

    assertThat(result).isNotNull();
    assertThat(result.getResourceType()).isEqualTo(org.hl7.fhir.r4.model.ResourceType.Bundle);
  }

  @Test
  void parseBundle_WithNullInput_ShouldThrowException() {
    assertThatThrownBy(() -> bundleProcessor.parseBundle(null))
        .isInstanceOf(BundleProcessingException.class)
        .hasMessage("Bundle string is null or empty");
  }

  @Test
  void parseBundle_WithEmptyInput_ShouldThrowException() {
    assertThatThrownBy(() -> bundleProcessor.parseBundle(""))
        .isInstanceOf(BundleProcessingException.class)
        .hasMessage("Bundle string is null or empty");
  }

  @Test
  void parseBundle_WithInvalidJson_ShouldThrowException() {
    String invalidJson = "{ invalid json }";

    assertThatThrownBy(() -> bundleProcessor.parseBundle(invalidJson))
        .isInstanceOf(BundleProcessingException.class)
        .hasMessageContaining("Failed to parse input as FHIR Bundle");
  }

  @Test
  void validateBundle_WithValidBundle_ShouldPass() throws Exception {
    Bundle bundle = createValidBundle();

    bundleProcessor.validateBundle(bundle);

    verify(bundleValidator).validateBundle(bundle, ValidationMode.STRICT);
  }

  @Test
  void validateBundle_WithInvalidBundle_ShouldThrowException() throws Exception {
    Bundle bundle = createValidBundle();
    doThrow(new BundleValidationException("Invalid bundle"))
        .when(bundleValidator).validateBundle(bundle, ValidationMode.STRICT);

    assertThatThrownBy(() -> bundleProcessor.validateBundle(bundle))
        .isInstanceOf(BundleProcessingException.class)
        .hasMessageContaining("Bundle validation failed");
  }

  @Test
  void processBundle_WithBundle_ShouldReturnProcessedBundle() throws Exception {
    Bundle inputBundle = createValidBundle();
    Bundle mockProcessedBundle = createValidBundle();
    mockProcessedBundle.setId("processed-bundle");
    when(pseudonymizerClient.pseudonymize(inputBundle)).thenReturn(Mono.just(mockProcessedBundle));

    Bundle result = bundleProcessor.processBundle(inputBundle);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("processed-bundle");
    verify(pseudonymizerClient).pseudonymize(inputBundle);
  }

  @Test
  void writeToStdout_WithBundle_ShouldWriteJson() throws Exception {
    Bundle bundle = createValidBundle();

    bundleProcessor.writeToStdout(bundle);

    verify(stdoutWriter).writeToStdout(anyString());
  }

  @Test
  void writeToStdout_WithIOException_ShouldThrowException() throws Exception {
    Bundle bundle = createValidBundle();
    doThrow(new IOException("Write failed")).when(stdoutWriter).writeToStdout(anyString());

    assertThatThrownBy(() -> bundleProcessor.writeToStdout(bundle))
        .isInstanceOf(IOException.class)
        .hasMessage("Write failed");
  }

  @Test
  void processBundle_EndToEnd_WithRealIO_ShouldWork() throws Exception {
    // Given - use real services instead of mocks for end-to-end test
    StdinReader realStdinReader = new StdinReader();
    StdoutWriter realStdoutWriter = new StdoutWriter();
    BundleValidator realBundleValidator = new BundleValidator();
    
    // Still need to mock the pseudonymizer client and config for end-to-end test
    when(config.url()).thenReturn("http://localhost:8080");
    when(config.healthCheckEnabled()).thenReturn(false);
    Bundle mockProcessedBundle = createValidBundle();
    mockProcessedBundle.setId("processed-bundle");
    when(pseudonymizerClient.pseudonymize(any(Bundle.class))).thenReturn(Mono.just(mockProcessedBundle));
    
    BundleProcessor realProcessor = new BundleProcessor(
        realStdinReader, realStdoutWriter, realBundleValidator, config, pseudonymizerClient);

    String validJson = loadTestResource("test-bundles/valid-collection-bundle.json");
    System.setIn(new ByteArrayInputStream(validJson.getBytes(StandardCharsets.UTF_8)));

    int result = realProcessor.processBundle();

    assertThat(result).isEqualTo(0);
    String output = capturedOutput.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("\"resourceType\":\"Bundle\"");
    assertThat(output).contains("\"id\":\"processed-bundle\"");
  }

  private Bundle createValidBundle() {
    Patient patient = new Patient();
    patient.setId("patient-1");
    
    Bundle bundle = new Bundle();
    bundle.setId("test-bundle");
    bundle.setType(Bundle.BundleType.COLLECTION);
    bundle.addEntry(new Bundle.BundleEntryComponent().setResource(patient));
    return bundle;
  }

  private String loadTestResource(String resourcePath) throws IOException {
    ClassPathResource resource = new ClassPathResource(resourcePath);
    return resource.getContentAsString(StandardCharsets.UTF_8);
  }
}