package care.smith.fts.packager.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TildeExpandingFileConverter.
 */
class TildeExpandingFileConverterTest {

  private TildeExpandingFileConverter converter;
  private String userHome;

  @BeforeEach
  void setUp() {
    converter = new TildeExpandingFileConverter();
    userHome = System.getProperty("user.home");
    assertThat(userHome).isNotNull(); // Ensure we have a user home for testing
  }

  @Test
  void shouldExpandTildeAlone() {
    // Given: Just a tilde
    String input = "~";

    // When: Convert the path
    File result = converter.convert(input);

    // Then: Should expand to user home directory
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo(userHome);
  }

  @Test
  void shouldExpandTildeSlashPath() {
    // Given: Tilde with path
    String input = "~/config/application.yaml";

    // When: Convert the path
    File result = converter.convert(input);

    // Then: Should expand tilde to user home
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo(userHome + "/config/application.yaml");
  }

  @Test
  void shouldNotExpandPathsWithoutTilde() {
    // Given: Regular absolute path
    String input = "/etc/config.yaml";

    // When: Convert the path
    File result = converter.convert(input);

    // Then: Should return path unchanged
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo("/etc/config.yaml");
  }

  @Test
  void shouldNotExpandRelativePathsWithoutTilde() {
    // Given: Regular relative path
    String input = "config/application.yaml";

    // When: Convert the path
    File result = converter.convert(input);

    // Then: Should return path unchanged
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo("config/application.yaml");
  }

  @Test
  void shouldHandleNullInput() {
    // Given: Null input
    String input = null;

    // When: Convert the path
    File result = converter.convert(input);

    // Then: Should return null
    assertThat(result).isNull();
  }

  @Test
  void shouldHandleEmptyInput() {
    // Given: Empty string
    String input = "";

    // When: Convert the path
    File result = converter.convert(input);

    // Then: Should return null
    assertThat(result).isNull();
  }

  @Test
  void shouldNotExpandTildeWithUsernameFormat() {
    // Given: Tilde with username format (not supported)
    String input = "~otheruser/config.yaml";

    // When: Convert the path
    File result = converter.convert(input);

    // Then: Should return path unchanged (not expanded)
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo("~otheruser/config.yaml");
  }

  @Test
  void shouldExpandTildeInComplexPath() {
    // Given: Complex path with tilde
    String input = "~/.config/fhir-packager/settings.yaml";

    // When: Convert the path
    File result = converter.convert(input);

    // Then: Should properly expand the tilde part
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo(userHome + "/.config/fhir-packager/settings.yaml");
  }

  @Test
  void shouldHandleTildeInMiddleOfPath() {
    // Given: Path with tilde not at start (shouldn't expand)
    String input = "/some/path/~/config.yaml";

    // When: Convert the path
    File result = converter.convert(input);

    // Then: Should not expand tilde in middle of path
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo("/some/path/~/config.yaml");
  }

  @Test
  void shouldCreateCorrectFileObject() {
    // Given: Tilde path
    String input = "~/test.yaml";

    // When: Convert the path
    File result = converter.convert(input);

    // Then: Should create a proper File object
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(File.class);
    assertThat(result.getName()).isEqualTo("test.yaml");
    assertThat(result.getParent()).isEqualTo(userHome);
  }
}