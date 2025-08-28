package care.smith.fts.packager.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for StdinReader service.
 * 
 * <p>This test class does not require Spring Boot context since StdinReader
 * is a standalone service with no Spring dependencies.
 */
class StdinReaderTest {

  private StdinReader stdinReader;
  private InputStream originalSystemIn;

  @BeforeEach
  void setUp() {
    stdinReader = new StdinReader();
    originalSystemIn = System.in;
  }

  @AfterEach
  void tearDown() {
    System.setIn(originalSystemIn);
  }

  @Test
  void readFromStdin_WithSingleLine_ShouldReturnContent() throws IOException {
    // Given
    String input = "Hello, World!";
    System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));

    // When
    String result = stdinReader.readFromStdin();

    // Then
    assertThat(result).isEqualTo("Hello, World!");
  }

  @Test
  void readFromStdin_WithMultipleLines_ShouldReturnContentWithLineBreaks() throws IOException {
    // Given
    String input = "Line 1\nLine 2\nLine 3";
    System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));

    // When
    String result = stdinReader.readFromStdin();

    // Then
    assertThat(result).isEqualTo("Line 1" + System.lineSeparator() + 
                                "Line 2" + System.lineSeparator() + 
                                "Line 3");
  }

  @Test
  void readFromStdin_WithEmptyInput_ShouldReturnEmptyString() throws IOException {
    // Given
    System.setIn(new ByteArrayInputStream(new byte[0]));

    // When
    String result = stdinReader.readFromStdin();

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void readFromStdin_WithWhitespaceOnly_ShouldReturnWhitespace() throws IOException {
    // Given
    String input = "   \t  \n  ";
    System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));

    // When
    String result = stdinReader.readFromStdin();

    // Then
    assertThat(result).isEqualTo("   \t  " + System.lineSeparator() + "  ");
  }

  @Test
  void readFromStdin_WithUtf8Characters_ShouldHandleEncodingCorrectly() throws IOException {
    // Given
    String input = "Ünicöde tëxt with spëcial chäracteŕs: 你好世界";
    System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));

    // When
    String result = stdinReader.readFromStdin();

    // Then
    assertThat(result).isEqualTo(input);
  }

  @Test
  void readFromStdin_WithJsonContent_ShouldReturnFormattedJson() throws IOException {
    // Given
    String jsonInput = "{\n  \"resourceType\": \"Bundle\",\n  \"id\": \"test\"\n}";
    System.setIn(new ByteArrayInputStream(jsonInput.getBytes(StandardCharsets.UTF_8)));

    // When
    String result = stdinReader.readFromStdin();

    // Then
    assertThat(result).contains("\"resourceType\": \"Bundle\"");
    assertThat(result).contains("\"id\": \"test\"");
    assertThat(result.split(System.lineSeparator())).hasSize(4);
  }

  @Test
  void readFromStdin_WithLargeContent_ShouldHandleLargeInput() throws IOException {
    // Given
    StringBuilder largeInput = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
      largeInput.append("This is line ").append(i).append("\n");
    }
    System.setIn(new ByteArrayInputStream(largeInput.toString().getBytes(StandardCharsets.UTF_8)));

    // When
    String result = stdinReader.readFromStdin();

    // Then
    assertThat(result).contains("This is line 0");
    assertThat(result).contains("This is line 999");
    assertThat(result.split(System.lineSeparator())).hasSize(1000);
  }
}