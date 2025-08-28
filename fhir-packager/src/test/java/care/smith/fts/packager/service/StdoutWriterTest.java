package care.smith.fts.packager.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StdoutWriter service.
 * 
 * <p>This test class does not require Spring Boot context since StdoutWriter
 * is a standalone service with no Spring dependencies.
 */
class StdoutWriterTest {

  private StdoutWriter stdoutWriter;
  private PrintStream originalSystemOut;
  private ByteArrayOutputStream capturedOutput;

  @BeforeEach
  void setUp() {
    stdoutWriter = new StdoutWriter();
    originalSystemOut = System.out;
    capturedOutput = new ByteArrayOutputStream();
    System.setOut(new PrintStream(capturedOutput, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void tearDown() {
    System.setOut(originalSystemOut);
  }

  @Test
  void writeToStdout_WithSimpleText_ShouldWriteContent() throws IOException {
    // Given
    String content = "Hello, World!";

    // When
    stdoutWriter.writeToStdout(content);

    // Then
    String output = capturedOutput.toString(StandardCharsets.UTF_8);
    assertThat(output).isEqualTo("Hello, World!");
  }

  @Test
  void writeToStdout_WithJsonContent_ShouldWriteFormattedJson() throws IOException {
    // Given
    String jsonContent = "{\n  \"resourceType\": \"Bundle\",\n  \"id\": \"test\"\n}";

    // When
    stdoutWriter.writeToStdout(jsonContent);

    // Then
    String output = capturedOutput.toString(StandardCharsets.UTF_8);
    assertThat(output).isEqualTo(jsonContent);
    assertThat(output).contains("\"resourceType\": \"Bundle\"");
    assertThat(output).contains("\"id\": \"test\"");
  }

  @Test
  void writeToStdout_WithEmptyString_ShouldWriteNothing() throws IOException {
    // Given
    String content = "";

    // When
    stdoutWriter.writeToStdout(content);

    // Then
    String output = capturedOutput.toString(StandardCharsets.UTF_8);
    assertThat(output).isEmpty();
  }

  @Test
  void writeToStdout_WithUtf8Characters_ShouldHandleEncodingCorrectly() throws IOException {
    // Given
    String content = "Ünicöde tëxt with spëcial chäracteŕs: 你好世界";

    // When
    stdoutWriter.writeToStdout(content);

    // Then
    String output = capturedOutput.toString(StandardCharsets.UTF_8);
    assertThat(output).isEqualTo(content);
  }

  @Test
  void writeToStdout_WithMultilineContent_ShouldPreserveLineBreaks() throws IOException {
    // Given
    String content = "Line 1\nLine 2\nLine 3";

    // When
    stdoutWriter.writeToStdout(content);

    // Then
    String output = capturedOutput.toString(StandardCharsets.UTF_8);
    assertThat(output).isEqualTo("Line 1\nLine 2\nLine 3");
    assertThat(output.split("\n")).hasSize(3);
  }

  @Test
  void writeToStdout_WithLargeContent_ShouldHandleLargeOutput() throws IOException {
    // Given
    StringBuilder largeContent = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
      largeContent.append("This is line ").append(i).append("\n");
    }

    // When
    stdoutWriter.writeToStdout(largeContent.toString());

    // Then
    String output = capturedOutput.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("This is line 0");
    assertThat(output).contains("This is line 999");
    assertThat(output.split("\n")).hasSize(1000);
  }

  @Test
  void writeToStdout_WithWhitespaceContent_ShouldPreserveWhitespace() throws IOException {
    // Given
    String content = "   \t  \n  \r\n\t";

    // When
    stdoutWriter.writeToStdout(content);

    // Then
    String output = capturedOutput.toString(StandardCharsets.UTF_8);
    assertThat(output).isEqualTo(content);
  }
}